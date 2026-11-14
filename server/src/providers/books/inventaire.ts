import { providerFetch } from '../http';
import { SearchResult } from '../../types';

const base = 'https://inventaire.io';
const validUri = (value: unknown): value is string => typeof value === 'string' && /^(?:wd:Q\d+|inv:[a-f0-9]{32})$/.test(value);
const words = (value: string) => value.normalize('NFKC').toLowerCase().match(/[\p{L}\p{N}]+/gu) || [];

/** Work-only, keyless recovery source. Hydrate a bounded batch, not N+1 items. */
export async function searchInventaire(query: string, lang = 'en'): Promise<SearchResult[]> {
  const url = new URL(`${base}/api/search`);
  Object.entries({ search: query, types: 'works', lang, limit: '6', exact: 'true' }).forEach(([k, v]) => url.searchParams.set(k, v));
  const response = await providerFetch(url, { signal: AbortSignal.timeout(2200) });
  if (!response.ok) return [];
  const data: any = await response.json();
  if (!Array.isArray(data.results)) throw new Error('Invalid Inventaire search');
  const terms = [...new Set(words(query).filter(w => !['the', 'a', 'an', 'of', 'and'].includes(w)))];
  const candidates = data.results.filter((r: any) => {
    if (r.type !== 'works' || !validUri(r.uri) || typeof r.label !== 'string') return false;
    const title = new Set(words(r.label));
    // The API can return loosely related titles. Keep strong lexical matches
    // and let the shared ranker/author/year constraints make the final choice.
    return terms.length > 0 && terms.filter(t => title.has(t)).length >= Math.ceil(terms.length * 0.7);
  }).slice(0, 4);
  if (!candidates.length) return [];
  const detailsUrl = new URL(`${base}/api/entities/by-uris`);
  Object.entries({ uris: candidates.map((r: any) => r.uri).join('|'), lang,
    attributes: 'info|labels|descriptions|claims|image', relatives: 'wdt:P50' }).forEach(([k, v]) => detailsUrl.searchParams.set(k, v));
  const details = await providerFetch(detailsUrl, { signal: AbortSignal.timeout(2200) });
  if (!details.ok) return [];
  const body: any = await details.json();
  if (!body.entities || typeof body.entities !== 'object') throw new Error('Invalid Inventaire entities');
  const entities = body.entities;
  const label = (entity: any): string => entity?.labels?.[lang] || entity?.labels?.en || Object.values(entity?.labels || {})[0] || '';
  return candidates.flatMap((candidate: any): SearchResult[] => {
    const entity = entities[candidate.uri];
    if (entity?.type !== 'work' || !validUri(entity.uri)) return [];
    const claims = entity.claims || {};
    const authorUris = Array.isArray(claims['wdt:P50']) ? claims['wdt:P50'].filter(validUri) : [];
    const authors = authorUris.map((uri: string) => label(entities[uri])).filter(Boolean);
    const date = Array.isArray(claims['wdt:P577']) ? claims['wdt:P577'].find((v: unknown) => typeof v === 'string' && /^\d{4}(?:-\d{2}(?:-\d{2})?)?$/.test(v)) || '' : '';
    const rawCover = entity.image?.url || candidate.image || '';
    const coverUrl = typeof rawCover === 'string' && /^\/img\/entities\/[a-f0-9]{40}$/.test(rawCover) ? `${base}${rawCover}` : '';
    const entityUrl = `${base}/entity/${entity.uri}`;
    return [{ title: label(entity) || candidate.label, creator: authors.join(', '),
      creators: authors.map((name: string) => ({ name, role: 'author' })),
      aliases: (Array.isArray(claims['wdt:P1476']) ? claims['wdt:P1476'] : []).filter((v: unknown) => typeof v === 'string'),
      sourceRefs: [{ source: 'inventaire', id: entity.uri, url: entityUrl },
        ...(entity.uri.startsWith('wd:') ? [{ source: 'wikidata', id: entity.uri.slice(3), url: `https://www.wikidata.org/wiki/${entity.uri.slice(3)}` }] : [])],
      // Do not blindly copy Wikidata's multiple Open Library IDs into identity
      // refs: they can point at different works/editions. Title+author matching
      // remains conservative until a crosswalk is independently validated.
      releaseYear: date.slice(0, 4), releaseDate: date,
      coverUrl, overview: entity.descriptions?.[lang] || entity.descriptions?.en || candidate.description || '',
      rating: 0, totalEpisodes: 0, totalPages: 0, category: 'BOOK', externalUrl: entityUrl,
    }];
  });
}
