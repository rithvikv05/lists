import { offlineSnapshot } from '../catalog/offlineSnapshot';
import { Hono } from 'hono';
import { EngineEnv } from '../search/providers';
import { searchMetadata } from '../search/service';
import { normalizeQuery } from '../normalizer/queryNormalizer';
import { isQueryAnIsbn, isValidIsbn } from './normalizer/isbn';
import { projectBookMetadata } from './projection';
import { calculateReadingSpeedAndEta } from './tracker/readingSpeed';

export function bookRoutes(environment: (env: EngineEnv) => EngineEnv): Hono<{ Bindings: EngineEnv }> {
  const routes = new Hono<{ Bindings: EngineEnv }>();
  routes.get('/search', async c => {
    const q = c.req.query('q')?.trim();
    const type = (c.req.query('type') || 'all').toLowerCase();
    const lang = c.req.query('lang');
    const limit = Number(c.req.query('limit') || 20);
    if (!q || q.length > 500 || !['all', 'title', 'author', 'isbn'].includes(type) || !Number.isInteger(limit) || limit < 1 || limit > 40 ||
        (lang && !/^[a-z]{2,3}(?:-[A-Za-z]{2,4})?$/.test(lang)) || (type === 'isbn' && !isValidIsbn(q))) {
      return c.json({ error: 'Invalid book query, type, language, ISBN or limit' }, 400);
    }
    const norm = normalizeQuery(q);
    const result = await searchMetadata({ query: norm.cleanQuery, category: 'BOOK', mode: type === 'isbn' ? 'ALL' : type.toUpperCase() as 'ALL' | 'TITLE' | 'AUTHOR',
      year: norm.extractedYear, lang, country: 'US' }, environment(c.env));
    const results = result.results.slice(0, limit).map(r => ({ ...projectBookMetadata(r), offlineSnapshot: offlineSnapshot(r) }));
    return c.json({ query: q, searchType: isQueryAnIsbn(q) ? 'isbn' : type, source: result.source,
      schemaVersion: 2, status: result.status, incompleteSources: result.incompleteSources, count: results.length, results },
    result.status === 'partial' && !results.length ? 503 : 200);
  });
  routes.get('/isbn/:isbn', async c => {
    const isbn = c.req.param('isbn');
    if (!isValidIsbn(isbn)) return c.json({ error: 'Invalid ISBN checksum or format' }, 400);
    const result = await searchMetadata({ query: isbn, category: 'BOOK', mode: 'ALL', country: 'US' }, environment(c.env));
    if (!result.results.length) return c.json({ error: result.status === 'partial' ? 'ISBN sources temporarily unavailable' : 'No book found for ISBN', status: result.status }, result.status === 'partial' ? 503 : 404);
    if (result.results.length > 1) return c.json({ error: 'Conflicting source identities for ISBN', candidates: result.results.map(projectBookMetadata) }, 409);
    return c.json({ isbn, success: true, status: result.status, incompleteSources: result.incompleteSources, book: { ...projectBookMetadata(result.results[0]), offlineSnapshot: offlineSnapshot(result.results[0]) } });
  });

  routes.post('/reading-speed', async c => {
    const raw = await c.req.text();
    if (raw.length > 100_000) return c.json({ error: 'Request too large' }, 413);
    let body: any;
    try { body = JSON.parse(raw); } catch { return c.json({ error: 'Invalid JSON' }, 400); }
    if (!body || !Number.isFinite(body.totalPages) || !Number.isFinite(body.currentPage) || body.totalPages < 0 || body.currentPage < 0 || body.currentPage > body.totalPages ||
        !Array.isArray(body.sessions) || body.sessions.length > 1000 || body.sessions.some((s: any) => !s || !Number.isFinite(s.durationMinutes) || !Number.isFinite(s.pagesRead) || s.durationMinutes < 0 || s.pagesRead < 0)) {
      return c.json({ error: 'Valid page totals and reading sessions are required' }, 400);
    }
    return c.json(calculateReadingSpeedAndEta(body));
  });
  return routes;
}
