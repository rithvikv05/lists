import { Hono } from 'hono';
import { cors } from 'hono/cors';
import { logger } from 'hono/logger';
import { BookMetadata, SearchResponse, RecommendationRequest, ReadingSpeedCalculationRequest } from './types';
import { isQueryAnIsbn } from './normalizer/isbn';
import { searchGoogleBooks } from './providers/googleBooks';
import { searchOpenLibrary } from './providers/openLibrary';
import { lookupDirectIsbn } from './providers/isbnsDirect';
import { deduplicateAndRank } from './aggregator/merger';
import { rankRecommendations } from './recommendation/engine';
import { calculateReadingSpeedAndEta } from './tracker/readingSpeed';

type Bindings = {
  ENVIRONMENT?: string;
  GOOGLE_BOOKS_API_KEY?: string;
  ISBNDB_API_KEY?: string;
};

const app = new Hono<{ Bindings: Bindings }>();

// Enable CORS for mobile apps, web, and external clients
app.use('*', cors({
  origin: '*',
  allowMethods: ['GET', 'POST', 'OPTIONS'],
  allowHeaders: ['Content-Type', 'Authorization'],
  maxAge: 86400,
}));

app.use('*', logger());

// Health check endpoint
app.get('/health', (c) => {
  return c.json({
    status: 'ok',
    service: 'books-cloud-engine',
    version: '1.0.0',
    capabilities: [
      'multi-provider-search',
      'barcode-isbn-lookup',
      'recommendation-vector-matching',
      'dynamic-ewma-reading-tracker',
    ],
    timestamp: new Date().toISOString(),
  });
});

/**
 * GET /api/v1/books/search
 * Query parameters:
 *   - q: search term or barcode / ISBN
 *   - type: 'all' | 'title' | 'author' | 'isbn' (default: 'all')
 *   - lang: optional language code (e.g. 'en', 'es', 'ja')
 *   - limit: maximum results (default: 20, max: 40)
 */
app.get('/api/v1/books/search', async (c) => {
  const query = c.req.query('q')?.trim();
  if (!query) {
    return c.json({ error: 'Query parameter "q" is required' }, 400);
  }

  const searchType = (c.req.query('type') || 'all') as 'all' | 'title' | 'author' | 'isbn';
  const lang = c.req.query('lang');
  const limit = Math.min(parseInt(c.req.query('limit') || '20', 10), 40);

  // Fast-Path: If query is an ISBN or barcode, direct lookup immediately
  if (searchType === 'isbn' || isQueryAnIsbn(query)) {
    const directResult = await lookupDirectIsbn(query, {
      googleApiKey: c.env?.GOOGLE_BOOKS_API_KEY,
      isbndbApiKey: c.env?.ISBNDB_API_KEY,
    });
    if (directResult) {
      const response: SearchResponse = {
        query,
        searchType: 'isbn',
        source: 'live',
        count: 1,
        results: [directResult],
      };
      return c.json(response);
    }
  }

  // Parallel dual-provider search
  const [googlePromise, olPromise] = [
    searchGoogleBooks(query, searchType, lang, limit, c.env?.GOOGLE_BOOKS_API_KEY),
    searchOpenLibrary(query, searchType, lang, limit),
  ];

  const [googleRes, olRes] = await Promise.allSettled([googlePromise, olPromise]);

  const rawList: BookMetadata[] = [];
  if (googleRes.status === 'fulfilled') rawList.push(...googleRes.value);
  if (olRes.status === 'fulfilled') rawList.push(...olRes.value);

  // Deduplicate, merge across providers, and score relevance
  const ranked = deduplicateAndRank(rawList, query).slice(0, limit);

  const response: SearchResponse = {
    query,
    searchType,
    source: 'live',
    count: ranked.length,
    results: ranked,
  };

  return c.json(response);
});

/**
 * GET /api/v1/books/isbn/:isbn
 * Exact barcode / ISBN-10 / ISBN-13 lookup endpoint
 */
app.get('/api/v1/books/isbn/:isbn', async (c) => {
  const rawIsbn = c.req.param('isbn');
  const book = await lookupDirectIsbn(rawIsbn, {
    googleApiKey: c.env?.GOOGLE_BOOKS_API_KEY,
    isbndbApiKey: c.env?.ISBNDB_API_KEY,
  });

  if (!book) {
    return c.json({ error: `No book found for ISBN: ${rawIsbn}` }, 404);
  }

  return c.json({
    isbn: rawIsbn,
    success: true,
    book,
  });
});

/**
 * POST /api/v1/books/recommend
 * Content-attribute recommendation engine matching
 */
app.post('/api/v1/books/recommend', async (c) => {
  try {
    const body = await c.req.json() as RecommendationRequest;
    let candidatePool = body.candidateBooks || [];

    // If no candidate pool provided, fetch candidates based on favorite genres or keywords
    if (candidatePool.length === 0) {
      const genreKeywords: Record<string, string> = {
        SCI_FI: 'science fiction',
        FANTASY: 'fantasy',
        MYSTERY_THRILLER: 'mystery thriller',
        HORROR: 'horror',
        ROMANCE: 'romance',
        HISTORICAL: 'historical fiction',
        PHILOSOPHY: 'philosophy',
        PSYCHOLOGY: 'psychology',
        SELF_HELP: 'self-help',
        SCIENCE: 'science',
        BIOGRAPHY: 'biography',
      };
      const rawGenre = body.favoriteGenres?.[0] || 'FICTION';
      const searchKeyword = genreKeywords[rawGenre] || rawGenre.toLowerCase();

      const [googleRes, olRes] = await Promise.allSettled([
        searchGoogleBooks(searchKeyword, 'all', undefined, 20, c.env?.GOOGLE_BOOKS_API_KEY),
        searchOpenLibrary(searchKeyword, 'all', undefined, 20),
      ]);
      const pool: BookMetadata[] = [];
      if (googleRes.status === 'fulfilled') pool.push(...googleRes.value);
      if (olRes.status === 'fulfilled') pool.push(...olRes.value);
      candidatePool = deduplicateAndRank(pool, searchKeyword);
    }

    const response = rankRecommendations(body, candidatePool);
    return c.json(response);
  } catch (err: any) {
    return c.json({ error: 'Invalid recommendation request body', message: err.message }, 400);
  }
});

/**
 * POST /api/v1/books/reading-speed
 * Calculates dynamic EWMA reading speed and finish ETA
 */
app.post('/api/v1/books/reading-speed', async (c) => {
  try {
    const body = await c.req.json() as ReadingSpeedCalculationRequest;
    if (typeof body.totalPages !== 'number' || typeof body.currentPage !== 'number') {
      return c.json({ error: 'totalPages and currentPage numbers are required' }, 400);
    }
    const result = calculateReadingSpeedAndEta(body);
    return c.json(result);
  } catch (err: any) {
    return c.json({ error: 'Invalid reading speed request body', message: err.message }, 400);
  }
});

export default app;
