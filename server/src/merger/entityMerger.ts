import { projectBookMetadata } from '../books/projection';
import { cleanIsbn, isValidIsbn, isbn10ToIsbn13 } from '../books/normalizer/isbn';
import { SearchResult, SourceRef } from '../types';
import { cleanCanonicalTitle, toCleanKey } from '../normalizer/canonicalTitle';
import { enhanceCoverUrl } from './imageUpscaler';
import { isValidSearchResult } from './validator';

const PRIORITY = ['tmdb', 'igdb', 'openlibrary', 'googlebooks', 'comicvine', 'bgg', 'tvmaze', 'hardcover', 'kitsu', 'jikan', 'mangadex', 'steam', 'apple', 'omdb'];
const union = <T>(items: T[], key: (item: T) => string = item => JSON.stringify(item)): T[] =>
  [...new Map(items.map(item => [key(item), item])).values()];
const populated = (value: unknown): boolean => value !== undefined && value !== null && value !== '' && value !== 0 && (!Array.isArray(value) || value.length > 0);
const book = (r: SearchResult) => ['BOOK', 'MANGA', 'COMIC'].includes(r.category);

export function normalizeIsbn(raw: string): string {
  const clean = cleanIsbn(raw);
  if (!isValidIsbn(clean)) return '';
  return clean.length === 10 ? isbn10ToIsbn13(clean)! : clean;
}

export function withSource(item: SearchResult, source: string): SearchResult {
  const explicit = item.sourceRefs?.find(ref => ref.source === source);
  const sourceId = explicit?.id || item.externalUrl || `${item.title}|${item.releaseYear}|${item.creator}`;
  const imdb = item.externalUrl.match(/^https?:\/\/(?:www\.)?imdb\.com\/title\/(tt\d+)/i);
  const refs: SourceRef[] = union([...(item.sourceRefs || []), { source, id: sourceId, url: item.externalUrl },
    ...(imdb ? [{ source: 'imdb', id: imdb[1], url: `https://www.imdb.com/title/${imdb[1]}/` }] : [])], r => `${r.source}:${r.id}`);
  const ratings = item.ratings || (item.rating > 0 ? [{ source, value: item.rating, scale: 10 }] : []);
  const fields = Object.fromEntries(Object.entries({ ...item, ratings }).filter(([key, value]) =>
    !['metadata', 'sourceRefs', 'editions', 'id'].includes(key) && populated(value)));
  return {
    ...item,
    sourceRefs: refs,
    ratings,
    metadata: [{ source, sourceId, fields: { ...fields, ...(item.metadata?.find(m => m.source === source && m.sourceId === sourceId)?.fields || {}) } }, ...(item.metadata || []).filter(m => m.source !== source || m.sourceId !== sourceId)],
    editions: item.editions || (book(item) ? [{ source, sourceId, isbn: item.isbn, language: item.language,
      publisher: item.publisher, totalPages: item.totalPages || undefined, releaseDate: item.releaseDate, coverUrl: item.coverUrl }] : []),
  };
}

function priority(item: SearchResult): number {
  return Math.min(...(item.sourceRefs || []).map(r => { const n = PRIORITY.indexOf(r.source); return n < 0 ? 100 : n; }), 100);
}
function stableKey(item: SearchResult): string {
  return (item.sourceRefs || []).map(r => `${r.source}:${r.id}`).sort().join('|') || JSON.stringify(item);
}
function titleKeys(item: SearchResult): string[] {
  return [item.title, item.originalTitle || '', ...(item.aliases || [])].filter(Boolean)
    .map(t => toCleanKey(t.replace(/\s*\((?:film|movie|tv series|novel|video game|manga|anime)\)\s*$/iu, '')));
}
function authorKey(name: string): string { return toCleanKey(name).replace(/\s/g, ''); }
function authors(item: SearchResult): string[] {
  const contributors = item.creators?.filter(c => ['author', 'developer'].includes(c.role)).map(c => c.name);
  return (contributors?.length ? contributors : [item.creator]).filter(Boolean).map(authorKey);
}
function authorOverlap(a: SearchResult, b: SearchResult): boolean {
  const x = authors(a), y = authors(b);
  return x.some(name => y.includes(name));
}
function sharedRef(a: SearchResult, b: SearchResult): boolean {
  return (a.sourceRefs || []).some(x => (b.sourceRefs || []).some(y => x.source === y.source && x.id === y.id));
}
function incompatible(a: SearchResult, b: SearchResult): boolean {
  if (a.category !== b.category) return true;
  if (a.id?.startsWith('catalog:') && b.id?.startsWith('catalog:') && a.id !== b.id) return true;
  if (a.entityKind && b.entityKind && a.entityKind !== b.entityKind) return true;
  if (a.category === 'BOOK' && a.creator && b.creator && !authorOverlap(a, b)) return true;
  // Distinct known IDs from the same work catalog must never be collapsed on title alone.
  const workCatalogs = ['tmdb', 'igdb', 'tvmaze', 'openlibrary', 'kitsu', 'jikan', 'anilist', 'mangadex', 'bgg', 'comicvine', 'wikipedia'];
  if ((a.sourceRefs || []).some(x => workCatalogs.includes(x.source) && (b.sourceRefs || []).some(y => x.source === y.source && x.id !== y.id))) return true;
  if (!book(a) && a.releaseYear && b.releaseYear && a.releaseYear !== b.releaseYear) return true;
  return false;
}
function sameEntity(a: SearchResult, b: SearchResult): boolean {
  if (a.category !== b.category) return false;
  if (a.id?.startsWith('catalog:') && b.id?.startsWith('catalog:') && a.id !== b.id) return false;
  // Exact references win over differing regional dates, but not different known kinds.
  if (sharedRef(a, b)) return !(a.entityKind && b.entityKind && a.entityKind !== b.entityKind);
  if (incompatible(a, b)) return false;
  const isbnA = normalizeIsbn(a.isbn || ''), isbnB = normalizeIsbn(b.isbn || '');
  if (isbnA && isbnA === isbnB) return true;
  if (!titleKeys(a).some(key => key && titleKeys(b).includes(key))) return false;
  if (a.category === 'BOOK') return !!a.creator && !!b.creator && authorOverlap(a, b);
  if (book(a)) {
    if (a.creator && b.creator) return authorOverlap(a, b);
    return !!a.releaseYear && a.releaseYear === b.releaseYear;
  }
  if (a.releaseYear && b.releaseYear) return a.releaseYear === b.releaseYear;
  // Sparse storefront records need a crosswalk or agreeing creator evidence.
  return !!a.creator && !!b.creator && authorOverlap(a, b);
}

function mergeCluster(records: SearchResult[]): SearchResult {
  const ordered = [...records].sort((a, b) => priority(a) - priority(b) || stableKey(a).localeCompare(stableKey(b)) || JSON.stringify(a).localeCompare(JSON.stringify(b)));
  const primary = ordered[0];
  const merged: SearchResult = { ...primary, title: cleanCanonicalTitle(primary.title) };
  const scalarFields = ['creator', 'releaseYear', 'releaseDate', 'coverUrl', 'overview', 'rating', 'totalEpisodes',
    'totalPages', 'externalUrl', 'originalTitle', 'backdropUrl', 'isbn', 'publisher', 'language', 'entityKind',
    'totalChapters', 'totalVolumes', 'totalIssues', 'runtimeMinutes', 'status'] as const;
  for (const field of scalarFields) {
    const record = ordered.find(r => populated(r[field]));
    if (record) (merged as unknown as Record<string, unknown>)[field] = record[field];
  }
  // Edition fields travel together. Do not combine one edition's pages with another's ISBN.
  if (book(primary)) {
    const edition = ordered.find(r => !!r.isbn && r.totalPages > 0) || ordered.find(r => !!r.isbn) || ordered.find(r => r.totalPages > 0) || primary;
    const isbn = normalizeIsbn(edition.isbn || '');
    const sameEdition = [edition, ...ordered.filter(r => r !== edition &&
      ((isbn && normalizeIsbn(r.isbn || '') === isbn) || sharedRef(r, edition)))];
    for (const field of ['isbn', 'publisher', 'language', 'totalPages', 'releaseDate'] as const) {
      const supplied = sameEdition.find(r => populated(r[field]));
      (merged as unknown as Record<string, unknown>)[field] = supplied?.[field] ?? (field === 'totalPages' ? 0 : '');
    }
    if (edition.coverUrl) merged.coverUrl = edition.coverUrl;
    // Original publication year is work-scoped; edition date remains available separately.
  }
  merged.coverUrl = enhanceCoverUrl(merged.coverUrl);
  merged.aliases = union(ordered.flatMap(r => [r.title, r.originalTitle || '', ...(r.aliases || [])]).filter(Boolean)).sort();
  merged.genres = union(ordered.flatMap(r => r.genres || []), toCleanKey).sort();
  merged.themes = union(ordered.flatMap(r => r.themes || []), toCleanKey).sort();
  merged.demographics = union(ordered.flatMap(r => r.demographics || []), toCleanKey).sort();
  merged.platforms = union(ordered.flatMap(r => r.platforms || []), toCleanKey).sort();
  merged.creators = union(ordered.flatMap(r => r.creators || []), c => `${c.role}:${toCleanKey(c.name)}`);
  merged.sourceRefs = union(ordered.flatMap(r => r.sourceRefs || []), r => `${r.source}:${r.id}`);
  merged.ratings = union(ordered.flatMap(r => r.ratings || []), r => `${r.source}:${r.value}:${r.scale}:${r.count || 0}`);
  merged.metadata = union(ordered.flatMap(r => r.metadata || []), r => `${r.source}:${r.sourceId}:${JSON.stringify(r.fields)}`);
  merged.editions = union(ordered.flatMap(r => r.editions || []), r => `${r.source}:${r.sourceId}:${normalizeIsbn(r.isbn || '')}`);
  const identity = merged.sourceRefs[0];
  merged.id = ordered.find(r => r.id?.startsWith('catalog:'))?.id || (identity ? `${identity.source}:${identity.id}` : undefined);
  merged.catalogRevision = ordered.find(r => r.id === merged.id)?.catalogRevision;
  merged.catalogUpdatedAt = ordered.find(r => r.id === merged.id)?.catalogUpdatedAt;
  if (merged.category === 'BOOK') {
    // Universal results already carry provenance; avoid copying those arrays a second time.
    const { metadata, sourceRefs, editions, ...bookFields } = projectBookMetadata(merged);
    merged.bookMetadata = bookFields;
  }
  return merged;
}

export function deduplicateAndMerge(results: SearchResult[]): SearchResult[] {
  const records = results.filter(isValidSearchResult).sort((a, b) => priority(a) - priority(b) || stableKey(a).localeCompare(stableKey(b)) || JSON.stringify(a).localeCompare(JSON.stringify(b)));
  const clusters: SearchResult[][] = [];
  for (const item of records) {
    const cluster = clusters.find(group => group.some(member => sameEntity(member, item)) &&
      group.every(member => !incompatible(member, item) || sharedRef(member, item)));
    if (cluster) cluster.push(item); else clusters.push([item]);
  }
  return clusters.map(mergeCluster);
}
