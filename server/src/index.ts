import { Hono } from 'hono';
import { cors } from 'hono/cors';
import { bodyLimit } from 'hono/body-limit';
import { Category, EnrichResponse, SearchResponse } from './types';
import { normalizeQuery } from './normalizer/queryNormalizer';
import { selectBookEdition } from './catalog/bookEdition';
import { offlineSnapshot } from './catalog/offlineSnapshot';
import { CatalogStore } from './catalog/store';
import { searchMetadata } from './search/service';
import { bookRoutes } from './books/routes';
import { EngineEnv, fallbackPlan, providerPlan, SearchOptions } from './search/providers';
import { resolveUrl, supportedUrl } from './resolve/resolver';
import { providerFetch, runProvider } from './providers/http';
import { allowedImageUrl, fetchCover } from './images';

const app = new Hono<{ Bindings: EngineEnv }>();
app.use('*', cors({ origin: '*', allowMethods: ['GET', 'POST', 'OPTIONS'], allowHeaders: ['Content-Type', 'Authorization'], maxAge: 86400 }));
app.use('*', bodyLimit({ maxSize: 512_000, onError: c => c.json({ error: 'Request too large' }, 413) }));
const categories: Category[] = ['MOVIE', 'TV_SHOW', 'ANIME', 'BOOK', 'MANGA', 'COMIC', 'GAME', 'BOARD_GAME', 'PODCAST', 'MUSIC'];
app.get('/v2/cover', async c => {
  const url = c.req.query('url') || '';
  if (url.length > 2048 || !allowedImageUrl(url)) return c.text('Unsupported image URL', 400);
  const key = new Request(c.req.url);
  // Cache API is optional in local Node tests, available at the Worker edge.
  const cache = typeof caches !== 'undefined' ? caches.default : undefined;
  const cached = await cache?.match(key);
  if (cached) return cached;
  const response = await fetchCover(url);
  if (response.ok && cache) c.executionCtx.waitUntil(cache.put(key, response.clone()));
  return response;
});
const envKeys: (keyof Omit<EngineEnv, 'CATALOG_DB'>)[] = ['TMDB_API_KEY', 'TMDB_READ_ACCESS_TOKEN', 'OMDB_API_KEY', 'HARDCOVER_API_TOKEN',
  'COMIC_VINE_API_KEY', 'BGG_API_TOKEN', 'GOOGLE_BOOKS_API_KEY', 'ISBNDB_API_KEY', 'TWITCH_CLIENT_ID', 'TWITCH_CLIENT_SECRET', 'IGDB_CLIENT_ID', 'IGDB_CLIENT_SECRET', 'ENABLE_ANILIST', 'ENVIRONMENT'];
function environment(bindings: EngineEnv = {}): EngineEnv {
  const result = { ...bindings };
  const local = (globalThis as { process?: { env?: Record<string, string | undefined> } }).process?.env;
  for (const key of envKeys) if (!result[key] && local?.[key]) result[key] = local[key];
  return result;
}
app.get('/health', c => {
  const env = environment(c.env);
  const providers = Object.fromEntries(categories.map(category => [category,
    providerPlan({ query: '', category, mode: 'ALL' }, env).map(p => p.source)]));
  const fallbackProviders = Object.fromEntries(categories.map(category => [category,
    fallbackPlan({ query: '', category, mode: 'ALL' }).map(p => p.source)]));
  return c.json({ status: 'ok', engine: 'lists-search-engine', version: '3.1.0', catalogConfigured: !!env.CATALOG_DB, providers, fallbackProviders, time: new Date().toISOString() });
});

app.on('GET', ['/v1/search', '/v2/search'], async c => {
  const q = (c.req.query('q') || '').trim();
  let category = (c.req.query('category') || 'MOVIE').toUpperCase() as Category;
  const subFilter = (c.req.query('subFilter') || 'ALL').toUpperCase();
  const mode = (c.req.query('mode') || c.req.query('bookSearchType') || 'ALL').toUpperCase();
  const extras = c.req.query('includeExtras');
  const strategy = c.req.query('strategy') || 'standard';
  if (!['standard', 'backup'].includes(strategy)) return c.json({ error: 'Invalid search strategy' }, 400);
  if (extras && !['true', 'false'].includes(extras)) return c.json({ error: 'includeExtras must be true or false' }, 400);
  const year = c.req.query('year');
  const lang = c.req.query('lang');
  const country = (c.req.query('country') || 'US').toUpperCase();
  if (!q || q.length > 500) return c.json({ error: 'q must contain 1–500 characters' }, 400);
  if (!categories.includes(category)) return c.json({ error: 'Unsupported category' }, 400);
  if (!['ALL', 'TITLE', 'AUTHOR'].includes(mode)) return c.json({ error: 'Invalid search mode' }, 400);
  if (year && !/^(?:(?:18|19|20)\d{2}|(?:18|19|20)\d0s)$/.test(year)) return c.json({ error: 'Invalid year' }, 400);
  if (lang && !/^[a-z]{2,3}(?:-[A-Za-z]{2,4})?$/.test(lang)) return c.json({ error: 'Invalid language' }, 400);
  if (!/^[A-Z]{2}$/.test(country)) return c.json({ error: 'Invalid country' }, 400);
  const allowedFilters = category === 'BOOK' ? ['ALL', 'BOOKS', 'MANGA', 'COMICS'] : category === 'TV_SHOW' ? ['ALL', 'TV_SHOWS', 'ANIME'] : ['ALL'];
  if (!allowedFilters.includes(subFilter)) return c.json({ error: 'Invalid subFilter for category' }, 400);
  if (category === 'BOOK' && subFilter === 'MANGA') category = 'MANGA';
  if (category === 'BOOK' && subFilter === 'COMICS') category = 'COMIC';
  if (category === 'TV_SHOW' && subFilter === 'ANIME') category = 'ANIME';
  if (/^https?:\/\//i.test(q)) {
    if (!supportedUrl(q)) return c.json({ error: 'Supported link imports: Goodreads books and Steam apps' }, 400);
    const resolution = await resolveUrl(q, country);
    if (c.env?.CATALOG_DB && resolution.results.length) {
      const stored = await new CatalogStore(c.env.CATALOG_DB).reconcile(resolution.results, resolution.results[0].category);
      resolution.results = stored.results;
    }
    resolution.results = resolution.results.map(r => ({ ...r, offlineSnapshot: offlineSnapshot(r) }));
    return c.json({ query: q, normalizedQuery: q, category, source: 'live', schemaVersion: 2,
      count: resolution.results.length, results: resolution.results,
      status: resolution.status === 'resolved' && !resolution.incompleteSources.length ? 'complete' : 'partial',
      resolutionStatus: resolution.status, candidates: resolution.candidates, incompleteSources: resolution.incompleteSources });
  }
  const norm = normalizeQuery(q);
  const decade = year?.endsWith('s') ? Number(year.slice(0, 4)) : undefined;
  const options: SearchOptions = { query: norm.cleanQuery, category, year: decade !== undefined ? undefined : year || norm.extractedYear,
    ...(decade !== undefined ? { yearStart: decade, yearEnd: decade + 9 } : {}), lang, country, mode: mode as SearchOptions['mode'], ...(extras === 'true' ? { includeExtras: true } : {}), ...(strategy === 'backup' ? { backup: true } : {}) };
  const env = environment(c.env);
  const response = await searchMetadata(options, env);
  if (response.status === 'partial' && !response.results.length) {
    c.header('Retry-After', '5');
    return c.json({ ...response, query: q }, 503);
  }
  return c.json<SearchResponse>({ ...response, query: q });
});

app.on('GET', ['/v1/entities/:id', '/v2/entities/:id'], async c => {
  const db = c.env?.CATALOG_DB;
  if (!db) return c.json({ error: 'Catalog is not configured' }, 503);
  const id = c.req.param('id') || '';
  if (!/^catalog:[a-f0-9-]{36}$/.test(id)) return c.json({ error: 'Invalid catalog ID' }, 400);
  try {
    const stored = await new CatalogStore(db).get(id);
    const entity = stored ? selectBookEdition(stored, c.req.query('isbn') || '') : null;
    return entity ? c.json({ ...entity, offlineSnapshot: offlineSnapshot(entity) }) : c.json({ error: 'Entity not found' }, 404);
  } catch { return c.json({ error: 'Catalog temporarily unavailable' }, 503); }
});
app.on('GET', ['/v1/catalog/coverage', '/v2/catalog/coverage'], async c => {
  const db = c.env?.CATALOG_DB;
  if (!db) return c.json({ error: 'Catalog is not configured' }, 503);
  try { return c.json(await new CatalogStore(db).coverage()); }
  catch { return c.json({ error: 'Catalog temporarily unavailable' }, 503); }
});

app.on('POST', ['/v1/resolve', '/v2/resolve'], async c => {
  const length = Number(c.req.header('content-length') || 0);
  if (length > 4096) return c.json({ error: 'Request too large' }, 413);
  let input: any;
  try {
    const body = await c.req.text();
    if (body.length > 4096) return c.json({ error: 'Request too large' }, 413);
    input = JSON.parse(body);
  } catch { return c.json({ error: 'Invalid JSON' }, 400); }
  if (typeof input?.url !== 'string' || input.url.length > 500 || !supportedUrl(input.url)) return c.json({ error: 'Unsupported URL' }, 400);
  const country = input.country || 'US';
  if (typeof country !== 'string' || !/^[A-Z]{2}$/.test(country)) return c.json({ error: 'Invalid country' }, 400);
  const resolution = await resolveUrl(input.url, country);
  if (c.env?.CATALOG_DB && resolution.results.length) {
    const stored = await new CatalogStore(c.env.CATALOG_DB).reconcile(resolution.results, resolution.results[0].category);
    resolution.results = stored.results;
  }
  resolution.results = resolution.results.map(r => ({ ...r, offlineSnapshot: offlineSnapshot(r) }));
  return c.json(resolution);
});

// Enrichment requires an identifier, so similarly named shows never exchange episodes.
app.on('GET', ['/v1/enrich', '/v2/enrich'], async c => {
  const id = c.req.query('id') || '';
  const source = c.req.query('source');
  const category = (c.req.query('category') || 'MOVIE').toUpperCase() as Category;
  const country = (c.req.query('country') || 'US').toUpperCase();
  if (!/^\d+$/.test(id) || !['tmdb', 'tvmaze'].includes(source || '') || !['MOVIE', 'TV_SHOW'].includes(category) || !/^[A-Z]{2}$/.test(country)) {
    return c.json({ error: 'Valid id, source (tmdb or tvmaze), category and country are required' }, 400);
  }
  const result: EnrichResponse = { title: '', category, platforms: [], episodes: [] };
  const outcome = await runProvider(source!, async () => {
    if (source === 'tvmaze') {
      if (category !== 'TV_SHOW') throw new Error('TVmaze only supports TV');
      const response = await providerFetch(`https://api.tvmaze.com/shows/${id}/episodes`);
      if (!response.ok) throw new Error('Episodes unavailable');
      const episodes: any = await response.json();
      if (!Array.isArray(episodes)) throw new Error('Invalid episodes');
      result.episodes = episodes.filter((ep: any) => Number.isInteger(ep.season) && Number.isInteger(ep.number)).map((ep: any) => ({
        season: ep.season, number: ep.number, name: ep.name || '', airdate: ep.airdate || '',
      }));
    } else {
      const env = environment(c.env);
      const token = env.TMDB_API_KEY || env.TMDB_READ_ACCESS_TOKEN;
      if (!token) throw new Error('TMDB not configured');
      const url = new URL(`https://api.themoviedb.org/3/${category === 'MOVIE' ? 'movie' : 'tv'}/${id}/watch/providers`);
      const headers: Record<string, string> = {};
      if (token.length > 40) headers.Authorization = `Bearer ${token}`; else url.searchParams.set('api_key', token);
      const response = await providerFetch(url, { headers });
      if (!response.ok) throw new Error('Offers unavailable');
      const data: any = await response.json();
      const offers = data.results?.[country];
      const seen = new Set<string>();
      for (const p of [...(offers?.flatrate || []), ...(offers?.free || []), ...(offers?.ads || []), ...(offers?.rent || []), ...(offers?.buy || [])]) {
        if (p.provider_name && offers.link && !seen.has(p.provider_name)) {
          seen.add(p.provider_name); result.platforms.push({ name: p.provider_name, url: offers.link });
        }
      }
    }
    return [];
  }, AbortSignal.timeout(2500));
  if (!['ok', 'empty'].includes(outcome.status)) return c.json({ error: 'Enrichment temporarily unavailable' }, 503);
  return c.json({ ...result, country, attribution: source === 'tmdb' ? 'JustWatch via TMDB' : 'TVmaze' });
});
app.route('/api/v1/books', bookRoutes(environment));

app.onError((error, c) => {
  console.error('Search request failed:', error.name);
  return c.json({ error: 'Metadata service temporarily unavailable' }, 503);
});
export default app;
