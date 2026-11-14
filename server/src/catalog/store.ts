import { Category, MetadataEvidence, SearchResult } from '../types';
import { deduplicateAndMerge } from '../merger/entityMerger';
import { toCleanKey } from '../normalizer/canonicalTitle';

export type CatalogState = 'stored' | 'read_only' | 'busy' | 'capacity' | 'unavailable' | 'disabled';
interface Row { id: string; payload: string; revision: number; updated_at: number }
interface RecordRow extends Row { result: SearchResult }
const MAX_RECORD_BYTES = 32768;
const MAX_REFS = 24;
const WRITE_CANDIDATES = 3;
const refKey = (category: Category, source: string, id: string) => JSON.stringify([category, source, id]);
function refs(result: SearchResult): string[] {
  return [...new Set((result.sourceRefs || []).filter(r => r.id && r.source !== 'catalog').map(r => refKey(result.category, r.source, r.id)))];
}
function titles(result: SearchResult): string[] {
  return [...new Set([result.title, result.originalTitle || '', ...(result.aliases || [])].map(toCleanKey).filter(Boolean))].slice(0, 8);
}
const populated = (value: unknown) => value !== undefined && value !== null && value !== '' && value !== 0 && (!Array.isArray(value) || value.length > 0);
function stableJson(value: unknown): string {
  return JSON.stringify(value, (_, v) => v && typeof v === 'object' && !Array.isArray(v)
    ? Object.fromEntries(Object.entries(v).sort(([a], [b]) => a.localeCompare(b))) : v);
}
export function featureMask(result: SearchResult): number {
  return (result.genres?.length ? 1 : 0) | (result.ratings?.some(r => r.value > 0) || result.rating > 0 ? 2 : 0) |
    (result.ratings?.some(r => (r.count || 0) > 0) ? 4 : 0) | (result.creators?.length || result.creator ? 8 : 0) |
    (result.language ? 16 : 0) | (result.overview ? 32 : 0);
}

/** Latest normalized evidence only, not raw HTTP responses or an ever-growing history. */
function latestEvidence(previous: SearchResult | undefined, incoming: SearchResult): MetadataEvidence[] {
  const records = new Map<string, MetadataEvidence>();
  for (const evidence of [...(previous?.metadata || []), ...(incoming.metadata || [])]) {
    if (evidence.source === 'anilist') continue;
    const key = JSON.stringify([evidence.source, evidence.sourceId]);
    const old = records.get(key);
    records.set(key, { ...evidence, fields: { ...(old?.fields || {}),
      ...Object.fromEntries(Object.entries(evidence.fields).filter(([, v]) => populated(v))) } });
  }
  return [...records.values()];
}

export function mergeCatalogRecord(previous: SearchResult | undefined, incoming: SearchResult, id: string): SearchResult {
  const evidence = latestEvidence(previous, incoming);
  const allRefs = [...new Map([...(previous?.sourceRefs || []), ...(incoming.sourceRefs || [])].map(r => [`${r.source}:${r.id}`, r])).values()];
  const anchor = { source: 'catalog', id, url: '' };
  // Identity is checked before calling this function. The private anchor connects already-resolved records.
  const records = evidence.map(e => ({ ...e.fields, category: incoming.category,
    title: e.fields.title || incoming.title, creator: e.fields.creator || '', releaseYear: e.fields.releaseYear || '',
    releaseDate: e.fields.releaseDate || '', coverUrl: e.fields.coverUrl || '', overview: e.fields.overview || '',
    rating: e.fields.rating || 0, totalPages: e.fields.totalPages || 0, totalEpisodes: e.fields.totalEpisodes || 0,
    externalUrl: e.fields.externalUrl || '', sourceRefs: [anchor, { source: e.source, id: e.sourceId, url: String(e.fields.externalUrl || '') }],
    metadata: [e],
  } as SearchResult));
  const result = records.length ? deduplicateAndMerge(records)[0] : { ...previous, ...incoming };
  if (!result) throw new Error('Invalid catalog record');
  result.id = id;
  result.sourceRefs = allRefs;
  result.editions = [...new Map([...(previous?.editions || []), ...(incoming.editions || [])].map(e => [`${e.source}:${e.sourceId}`, e])).values()];
  delete result.catalogRevision;
  delete result.catalogUpdatedAt;
  delete result.offlineSnapshot;
  if (result.bookMetadata) result.bookMetadata.canonicalId = id;
  return result;
}
function compatible(previous: SearchResult, incoming: SearchResult): boolean {
  return deduplicateAndMerge([previous, incoming]).length === 1;
}
function candidateFor(records: RecordRow[], incoming: SearchResult): RecordRow | null {
  const keys = new Set(refs(incoming));
  const exact = records.filter(r => refs(r.result).some(key => keys.has(key)));
  // Never silently join two existing canonical IDs or contradictory kinds.
  if (exact.length) return exact.length === 1 && compatible(exact[0].result, incoming) ? exact[0] : null;
  const matches = records.filter(r => compatible(r.result, incoming));
  return matches.length === 1 ? matches[0] : null;
}

export class CatalogStore {
  constructor(private db: D1Database) {}
  async get(id: string): Promise<SearchResult | null> {
    const row = await this.db.prepare('SELECT id,payload,revision,updated_at FROM catalog_entities WHERE id=?').bind(id).first<Row>();
    return row ? { ...JSON.parse(row.payload), id: row.id, catalogRevision: row.revision, catalogUpdatedAt: new Date(row.updated_at * 1000).toISOString() } : null;
  }
  async coverage(): Promise<unknown> {
    const [budget, categories] = await Promise.all([
      this.db.prepare('SELECT entity_count,payload_bytes,max_entities,max_payload_bytes,write_day,entity_writes_today,max_entity_writes_per_day FROM catalog_budget WHERE id=1').first(),
      this.db.prepare('SELECT * FROM catalog_categories ORDER BY category').all(),
    ]);
    return { budget, categories: categories.results, scope: 'persisted catalog only; not global search recall' };
  }
  private async candidates(incoming: SearchResult[], category: Category, query?: string): Promise<RecordRow[]> {
    const keys = [...new Set(incoming.flatMap(refs))].slice(0, 160);
    const names = [...new Set([...(query ? [toCleanKey(query)] : []), ...incoming.slice(0, WRITE_CANDIDATES).flatMap(titles)])].slice(0, 16);
    const rows = await this.db.prepare(`SELECT id,payload,revision,updated_at FROM catalog_entities WHERE id IN (
      SELECT entity_id FROM catalog_refs WHERE ref_key IN (SELECT value FROM json_each(?))
      UNION SELECT entity_id FROM catalog_titles WHERE category=? AND title_key IN (SELECT value FROM json_each(?))
    ) LIMIT 16`).bind(JSON.stringify(keys), category, JSON.stringify(names)).all<Row>();
    return rows.results.map(row => ({ ...row, result: JSON.parse(row.payload) }));
  }
  async reconcile(incoming: SearchResult[], category: Category, query?: string, write = true): Promise<{ results: SearchResult[]; state: CatalogState }> {
    let known: RecordRow[] = [];
    const bindKnown = (items: SearchResult[]) => items.map(item => {
      const match = candidateFor(known, item);
      return match ? { ...mergeCatalogRecord(match.result, item, match.id), catalogRevision: match.revision, catalogUpdatedAt: new Date(match.updated_at * 1000).toISOString() } : item;
    });
    try {
      known = await this.candidates(incoming, category, query);
      if (!incoming.length) return { results: known.map(r => ({ ...r.result, catalogRevision: r.revision, catalogUpdatedAt: new Date(r.updated_at * 1000).toISOString() })), state: 'read_only' };
      if (!write) return { results: bindKnown(incoming), state: 'read_only' };
      const needsWrite = incoming.slice(0, WRITE_CANDIDATES).some(item => {
        if (!refs(item).length || refs(item).length > MAX_REFS || item.sourceRefs?.some(r => r.source === 'anilist')) return false;
        const match = candidateFor(known, item);
        const result = mergeCatalogRecord(match?.result, item, match?.id || 'catalog:pending');
        if (new TextEncoder().encode(stableJson(result)).byteLength > MAX_RECORD_BYTES) return false;
        return !match || stableJson(result) !== stableJson(match.result);
      });
      // Repeated reads must not consume a lock write on every search.
      if (!needsWrite) return { results: bindKnown(incoming), state: 'read_only' };
      const budget = await this.db.prepare(`SELECT write_day,entity_writes_today,max_entity_writes_per_day,date('now') AS today FROM catalog_budget WHERE id=1`)
        .first<{ write_day: string; today: string; entity_writes_today: number; max_entity_writes_per_day: number }>();
      if (budget?.write_day === budget?.today && budget && budget.entity_writes_today >= budget.max_entity_writes_per_day) {
        return { results: bindKnown(incoming), state: 'capacity' };
      }
      const token = crypto.randomUUID();
      const lock = await this.db.prepare(`INSERT INTO catalog_lock(id,token,expires_at) VALUES(1,?,unixepoch()+10)
        ON CONFLICT(id) DO UPDATE SET token=excluded.token,expires_at=excluded.expires_at WHERE catalog_lock.expires_at<=unixepoch()`)
        .bind(token).run();
      if (!lock.meta.changes) return { results: bindKnown(incoming), state: 'busy' };
      try {
        // Re-read under the cross-isolate lease; all catalog writers use this path.
        known = await this.candidates(incoming, category, query);
        const staged: RecordRow[] = [];
        const changes: D1PreparedStatement[] = [];
        const stage = (row: RecordRow) => {
          const index = staged.findIndex(r => r.id === row.id);
          if (index < 0) staged.push(row); else staged[index] = row;
        };
        for (const item of incoming.slice(0, WRITE_CANDIDATES)) {
          if (!refs(item).length || refs(item).length > MAX_REFS || item.sourceRefs?.some(r => r.source === 'anilist')) continue;
          const available = [...known.filter(r => !staged.some(s => s.id === r.id)), ...staged];
          const match = candidateFor(available, item);
          // An exact reference already assigned to an incompatible/ambiguous entity is a conflict, not a new ID.
          const keys = new Set(refs(item));
          if (!match && available.some(r => refs(r.result).some(key => keys.has(key)))) continue;
          const id = match?.id || `catalog:${crypto.randomUUID()}`;
          const result = mergeCatalogRecord(match?.result, item, id);
          if (refs(result).length > MAX_REFS) continue;
          const payload = stableJson(result);
          const bytes = new TextEncoder().encode(payload).byteLength;
          if (bytes > MAX_RECORD_BYTES) continue;
          const revision = (match?.revision || 0) + (match && payload === stableJson(match.result) ? 0 : 1);
          if (match && revision === match.revision) { stage({ ...match, result }); continue; }
          // Conditional lease check makes a lost/expired lease abort the entire atomic batch via constraints.
          changes.push(match
            ? this.db.prepare(`UPDATE catalog_entities SET payload=?,payload_bytes=?,revision=?,updated_at=unixepoch(),feature_mask=? WHERE id=? AND revision=?`)
              .bind(payload, bytes, revision, featureMask(result), id, match.revision)
            : this.db.prepare(`INSERT INTO catalog_entities(id,category,payload,payload_bytes,revision,updated_at,feature_mask)
              VALUES(?,?,?,?,?,unixepoch(),?)`).bind(id, category, payload, bytes, revision, featureMask(result)));
          changes.push(this.db.prepare(`INSERT INTO catalog_refs(ref_key,entity_id) SELECT value,? FROM json_each(?) WHERE true
            ON CONFLICT(ref_key) DO UPDATE SET entity_id=excluded.entity_id WHERE catalog_refs.entity_id=excluded.entity_id`)
            .bind(id, JSON.stringify(refs(result))));
          changes.push(this.db.prepare('DELETE FROM catalog_titles WHERE entity_id=?').bind(id));
          changes.push(this.db.prepare(`INSERT OR IGNORE INTO catalog_titles(category,title_key,entity_id) SELECT ?,value,? FROM json_each(?)`)
            .bind(category, id, JSON.stringify(titles(result))));
          stage({ id, payload, revision, updated_at: Date.now() / 1000, result });
        }
        if (changes.length) {
          // A CHECK constraint in the same batch prevents committing after lease expiry.
          const guard = this.db.prepare(`INSERT INTO catalog_lease_guard(valid) SELECT CASE WHEN EXISTS(
            SELECT 1 FROM catalog_lock WHERE id=1 AND token=? AND expires_at>unixepoch()) THEN 1 ELSE 0 END`).bind(token);
          await this.db.batch([guard, ...changes, this.db.prepare('DELETE FROM catalog_lease_guard')]);
        }
        known = [...known.filter(r => !staged.some(s => s.id === r.id)), ...staged];
        return { results: bindKnown(incoming), state: 'stored' };
      } finally {
        await this.db.prepare('DELETE FROM catalog_lock WHERE id=1 AND token=?').bind(token).run();
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : '';
      return { results: bindKnown(incoming), state: message.includes('catalog budget exceeded') ? 'capacity' : 'unavailable' };
    }
  }
}
