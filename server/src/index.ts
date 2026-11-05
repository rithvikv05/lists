import { Hono } from 'hono';
import { cors } from 'hono/cors';
import { Category, BookSubFilter, TvSubFilter, SearchResult, SearchResponse, EnrichResponse, OttPlatform, EpisodeItem } from './types';
import { normalizeQuery } from './normalizer/queryNormalizer';
import { deduplicateAndMerge } from './merger/entityMerger';
import { calculateRelevance } from './ranker/relevanceScorer';
import { getCachedSearch, setCachedSearch } from './cache/cacheManager';

// Provider imports
import { searchITunesMovies } from './providers/movies/itunesMovie';
import { searchImdbMovies } from './providers/movies/imdbMovie';
import { searchOmdbMovies } from './providers/movies/omdbMovie';
import { searchTvMaze } from './providers/tv/tvmaze';
import { searchAniList } from './providers/tv/anilist';
import { searchAppleBooks } from './providers/books/appleBooks';
import { searchOpenLibrary } from './providers/books/openLibrary';
import { searchGoogleBooks } from './providers/books/googleBooks';
import { searchHardcover } from './providers/books/hardcover';
import { searchCataloggdComicDb } from './providers/comics/cataloggdComicDb';
import { searchGetComics } from './providers/comics/getComics';
import { searchImdbGames } from './providers/games/imdbGames';
import { searchSteamStore } from './providers/games/steamStore';
import { searchWikipedia } from './providers/boardgames/wikipediaBoardGames';
import { searchWikidataBoardGames } from './providers/boardgames/wikidataBoardGames';
import { searchBgg } from './providers/boardgames/bgg';

type Bindings = {
  ENVIRONMENT?: string;
  OMDB_API_KEY?: string;
  HARDCOVER_API_TOKEN?: string;
};

const app = new Hono<{ Bindings: Bindings }>();

// Enable CORS for all origins (Android app & Web Wasm)
app.use('*', cors({
  origin: '*',
  allowMethods: ['GET', 'POST', 'OPTIONS'],
  allowHeaders: ['Content-Type', 'Authorization'],
  maxAge: 86400
}));

// Health check endpoint
app.get('/health', (c) => c.json({ status: 'ok', engine: 'lists-search-engine', time: new Date().toISOString() }));

// -----------------------------------------------------------------------
// UNIVERSAL SEARCH ENDPOINT: /v1/search
// -----------------------------------------------------------------------
app.get('/v1/search', async (c) => {
  const q = (c.req.query('q') || '').trim();
  const category = (c.req.query('category') || 'MOVIE').toUpperCase() as Category;
  const subFilter = (c.req.query('subFilter') || 'ALL').toUpperCase() as BookSubFilter & TvSubFilter;
  const explicitYear = c.req.query('year');
  const lang = c.req.query('lang');

  if (!q) {
    return c.json({ error: 'Query parameter q is required' }, 400);
  }

  const norm = normalizeQuery(q);
  const effectiveYear = explicitYear || norm.extractedYear;
  const searchCleanQuery = norm.cleanQuery;

  const cacheKey = `${category}|${subFilter}|${effectiveYear || ''}|${lang || ''}|${searchCleanQuery.toLowerCase()}`;

  // 1. Check multi-tier edge cache
  const cached = getCachedSearch(cacheKey);
  if (cached) {
    return c.json<SearchResponse>({
      query: q,
      normalizedQuery: searchCleanQuery,
      category,
      source: 'cache',
      count: cached.length,
      results: cached
    });
  }

  // 2. Query parallel providers according to category
  const env = c.env;
  const omdbKey = env?.OMDB_API_KEY || 'trilogy';
  const hardcoverToken = env?.HARDCOVER_API_TOKEN;

  let rawResults: SearchResult[] = [];

  try {
    switch (category) {
      case 'MOVIE': {
        const [itunes, imdb, omdb] = await Promise.all([
          searchITunesMovies(searchCleanQuery, effectiveYear, lang),
          searchImdbMovies(searchCleanQuery, effectiveYear),
          searchOmdbMovies(searchCleanQuery, omdbKey, effectiveYear)
        ]);
        rawResults = [...itunes, ...imdb, ...omdb];
        if (rawResults.length < 5) {
          const wiki = await searchWikipedia(searchCleanQuery, 'MOVIE');
          rawResults.push(...wiki);
        }
        break;
      }

      case 'TV_SHOW': {
        const [tvmaze, anilist] = await Promise.all([
          searchTvMaze(searchCleanQuery, effectiveYear),
          searchAniList(searchCleanQuery, 'ANIME', effectiveYear)
        ]);
        rawResults = [...tvmaze, ...anilist];
        if (rawResults.length < 5) {
          const wiki = await searchWikipedia(searchCleanQuery, 'TV_SHOW');
          rawResults.push(...wiki);
        }
        break;
      }

      case 'ANIME': {
        const [anilist, tvmaze] = await Promise.all([
          searchAniList(searchCleanQuery, 'ANIME', effectiveYear),
          searchTvMaze(searchCleanQuery, effectiveYear)
        ]);
        rawResults = [...anilist, ...tvmaze];
        break;
      }

      case 'BOOK': {
        const [apple, openLib, google, hardcover] = await Promise.all([
          searchAppleBooks(searchCleanQuery, lang, 'BOOK'),
          searchOpenLibrary(searchCleanQuery, 'ALL', lang),
          searchGoogleBooks(searchCleanQuery, 'ALL', 'BOOK'),
          searchHardcover(searchCleanQuery, hardcoverToken)
        ]);
        rawResults = [...apple, ...openLib, ...google, ...hardcover];
        break;
      }

      case 'MANGA': {
        const [anilist, google] = await Promise.all([
          searchAniList(searchCleanQuery, 'MANGA', effectiveYear),
          searchGoogleBooks(`${searchCleanQuery} manga`, 'ALL', 'MANGA')
        ]);
        rawResults = [...anilist, ...google];
        break;
      }

      case 'COMIC': {
        const [cataloggd, getcomics, apple, google] = await Promise.all([
          searchCataloggdComicDb(searchCleanQuery),
          searchGetComics(searchCleanQuery),
          searchAppleBooks(`${searchCleanQuery} comic`, lang, 'COMIC'),
          searchGoogleBooks(`${searchCleanQuery} comic`, 'ALL', 'COMIC')
        ]);
        rawResults = [...cataloggd, ...getcomics, ...apple, ...google];
        if (rawResults.length < 5) {
          const wiki = await searchWikipedia(searchCleanQuery, 'COMIC');
          rawResults.push(...wiki);
        }
        break;
      }

      case 'GAME': {
        const [imdb, steam] = await Promise.all([
          searchImdbGames(searchCleanQuery),
          searchSteamStore(searchCleanQuery)
        ]);
        rawResults = [...imdb, ...steam];
        if (rawResults.length < 5) {
          const wiki = await searchWikipedia(searchCleanQuery, 'GAME');
          rawResults.push(...wiki);
        }
        break;
      }

      case 'BOARD_GAME': {
        const [wiki, wikidata, bgg] = await Promise.all([
          searchWikipedia(searchCleanQuery, 'BOARD_GAME'),
          searchWikidataBoardGames(searchCleanQuery),
          searchBgg(searchCleanQuery)
        ]);
        rawResults = [...wiki, ...wikidata, ...bgg];
        break;
      }

      case 'PODCAST':
      case 'MUSIC': {
        rawResults = await searchWikipedia(searchCleanQuery, category);
        break;
      }
    }
  } catch (err) {
    console.error('Aggregation error:', err);
  }

  // 3. Deduplicate and merge entity metadata with high-res cover upscaling
  const merged = deduplicateAndMerge(rawResults);

  // 4. Rank with composite fuzzy relevance scoring
  const ranked = merged
    .sort((a, b) => {
      const scoreA = calculateRelevance(q, searchCleanQuery, effectiveYear, a);
      const scoreB = calculateRelevance(q, searchCleanQuery, effectiveYear, b);
      return scoreB - scoreA;
    })
    .slice(0, 40);

  // 5. Update multi-tier cache
  setCachedSearch(cacheKey, ranked);

  return c.json<SearchResponse>({
    query: q,
    normalizedQuery: searchCleanQuery,
    category,
    source: 'live',
    count: ranked.length,
    results: ranked
  });
});

// -----------------------------------------------------------------------
// ENRICHMENT ENDPOINT: /v1/enrich (Streaming OTT offers, episodes, chapters)
// -----------------------------------------------------------------------
app.get('/v1/enrich', async (c) => {
  const title = (c.req.query('title') || '').trim();
  const category = (c.req.query('category') || 'MOVIE').toUpperCase() as Category;

  if (!title) {
    return c.json({ error: 'Title parameter is required' }, 400);
  }

  const platforms: OttPlatform[] = [];
  const episodes: EpisodeItem[] = [];

  // 1. Fetch TV Episodes from TVMaze if TV show
  if (category === 'TV_SHOW') {
    try {
      const term = encodeURIComponent(title);
      const res = await fetch(`https://api.tvmaze.com/singlesearch/shows?q=${term}&embed=episodes`, {
        signal: AbortSignal.timeout(2500)
      });
      if (res.ok) {
        const data = await res.json() as any;
        const eps = data._embedded?.episodes || [];
        for (const ep of eps) {
          episodes.push({
            season: ep.season || 1,
            number: ep.number || 1,
            name: ep.name || `Episode ${ep.number}`,
            airdate: ep.airdate || ''
          });
        }
      }
    } catch (_) {}
  }

  // 2. Fetch JustWatch OTT Streaming Platforms
  try {
    const gql = `
      query GetSuggestedTitles(${'$'}country: Country!, ${'$'}language: Language!, ${'$'}first: Int!, ${'$'}filter: TitleFilter) {
        popularTitles(country: ${'$'}country, first: ${'$'}first, filter: ${'$'}filter) {
          edges {
            node {
              offers(country: ${'$'}country, platform: WEB) {
                monetizationType
                standardWebURL
                package {
                  clearName
                  technicalName
                }
              }
            }
          }
        }
      }
    `;

    const res = await fetch('https://apis.justwatch.com/graphql', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'User-Agent': 'Mozilla/5.0' },
      body: JSON.stringify({
        query: gql,
        variables: {
          country: 'US',
          language: 'en',
          first: 1,
          filter: { searchQuery: title }
        }
      }),
      signal: AbortSignal.timeout(2500)
    });

    if (res.ok) {
      const data = await res.json() as any;
      const offers = data.data?.popularTitles?.edges?.[0]?.node?.offers || [];
      const seen = new Set<string>();

      for (const off of offers) {
        const clearName = off.package?.clearName || '';
        const webUrl = off.standardWebURL || '';
        const tech = (off.package?.technicalName || '').toLowerCase();

        if (tech.includes('dvd') || tech.includes('bluray') || tech.includes('cinema')) continue;

        if (clearName && webUrl && !seen.has(clearName.toLowerCase())) {
          seen.add(clearName.toLowerCase());
          platforms.push({ name: clearName, url: webUrl });
        }
      }
    }
  } catch (_) {}

  return c.json<EnrichResponse>({
    title,
    category,
    platforms,
    episodes
  });
});

export default app;

