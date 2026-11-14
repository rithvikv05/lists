import { selectBookEdition } from '../catalog/bookEdition';
import { offlineSnapshot } from '../catalog/offlineSnapshot';
import { CatalogStore } from '../catalog/store';
import { rankResults } from './engine';
import { SearchResponse } from '../types';
import { coalesceSearch, getCachedSearch, getStaleSearch, setCachedSearch } from '../cache/cacheManager';
import { aggregateSearch } from './engine';
import { EngineEnv, SearchOptions } from './providers';
import { parseBookQuery } from '../normalizer/bookQuery';

/** Both book API aliases and universal search share the same cache and aggregation path. */
export async function searchMetadata(options: SearchOptions, env: EngineEnv): Promise<SearchResponse> {
  if (options.category === 'BOOK' && options.mode === 'ALL') {
    const parsed = parseBookQuery(options.query);
    if (parsed.author) options = { ...options, query: parsed.title, author: parsed.author };
  }
  const { CATALOG_DB, ...configuration } = env;
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(JSON.stringify(Object.fromEntries(Object.entries(configuration).sort(([a], [b]) => a.localeCompare(b))))));
  const fingerprint = [...new Uint8Array(digest)].map(n => n.toString(16).padStart(2, '0')).join('');
  const key = `v3.2|${CATALOG_DB ? "catalog" : "transient"}|${fingerprint}|${JSON.stringify([options.category, options.query.normalize('NFKC'), options.mode, options.year || '', options.lang || '', options.country || 'US', options.includeExtras || false, options.yearStart || '', options.yearEnd || '', options.backup || false, options.author || ''])}`;
  const cached = getCachedSearch(key);
  if (cached) return { ...cached, source: 'cache' };
  return coalesceSearch(key, async () => {
    const result = await aggregateSearch(options, env);
    result.catalogState = 'disabled';
    result.freshness = 'live';
    const hadLiveResults = result.results.length > 0;
    if (CATALOG_DB) {
      const persisted = await new CatalogStore(CATALOG_DB).reconcile(result.results, options.category, options.query);
      result.results = rankResults(persisted.results.map(r => selectBookEdition(r, options.query)), options);
      result.count = result.results.length;
      result.catalogState = persisted.state;
      if (!hadLiveResults && result.results.length) { result.source = 'cache'; result.freshness = 'catalog'; }
    }
    result.results = result.results.map(r => ({ ...r, offlineSnapshot: offlineSnapshot(r) }));
    if (!result.results.length && result.status === 'partial') {
      const stale = getStaleSearch(key);
      if (stale) return { ...stale, source: 'cache', freshness: 'stale', status: 'partial',
        incompleteSources: result.incompleteSources, catalogState: result.catalogState };
    }
    if (!['unavailable', 'busy'].includes(result.catalogState || '')) setCachedSearch(key, result);
    return result;
  });
}
