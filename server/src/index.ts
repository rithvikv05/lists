import { Hono } from 'hono';
import { cors } from 'hono/cors';
import { logger } from 'hono/logger';
import {
  Category,
  BookSubFilter,
  TvSubFilter,
  SearchResult,
  SearchResponse,
  EnrichResponse,
  OttPlatform,
  EpisodeItem,
} from './types';
import { normalizeQuery } from './normalizer/queryNormalizer';
import { deduplicateAndMerge } from './merger/entityMerger';
import { calculateRelevance } from './ranker/relevanceScorer';
import { getCachedSearch, setCachedSearch } from './cache/cacheManager';

// Movies Providers (6)
import { searchITunesMovies } from './providers/movies/itunesMovie';
import { searchImdbMovies } from './providers/movies/imdbMovie';
import { searchOmdbMovies } from './providers/movies/omdbMovie';
import { searchTmdbMovie } from './providers/movies/tmdbMovie';
import { searchWikipediaMovie } from './providers/movies/wikipediaMovie';
import { searchJustWatchMovie } from './providers/movies/justwatchMovie';

// TV Shows Providers (6)
import { searchTvMaze } from './providers/tv/tvmaze';
import { searchItunesTv } from './providers/tv/itunesTv';
import { searchImdbTv } from './providers/tv/imdbTv';
import { searchTmdbTv } from './providers/tv/tmdbTv';
import { searchOmdbTv } from './providers/tv/omdbTv';
import { searchWikipediaTv } from './providers/tv/wikipediaTv';

// Anime Providers (6)
import { searchAnilistAnime } from './providers/anime/anilistAnime';
import { searchJikanAnime } from './providers/anime/jikanAnime';
import { searchKitsuAnime } from './providers/anime/kitsuAnime';
import { searchTvmazeAnime } from './providers/anime/tvmazeAnime';
import { searchImdbAnime } from './providers/anime/imdbAnime';
import { searchWikidataAnime } from './providers/anime/wikidataAnime';

// Books Providers (6)
import { searchAppleBooks } from './providers/books/appleBooks';
import { searchOpenLibrary } from './providers/books/openLibrary';
import { searchGoogleBooks } from './providers/books/googleBooks';
import { searchHardcover } from './providers/books/hardcover';
import { searchWikipediaBooks } from './providers/books/wikipediaBooks';
import { searchCrossrefBooks } from './providers/books/crossrefBooks';

// Manga Providers (6)
import { searchMangaDex } from './providers/manga/mangadex';
import { searchAnilistManga } from './providers/manga/anilistManga';
import { searchJikanManga } from './providers/manga/jikanManga';
import { searchKitsuManga } from './providers/manga/kitsuManga';
import { searchGoogleBooksManga } from './providers/manga/googleBooksManga';
import { searchOpenLibraryManga } from './providers/manga/openLibraryManga';

// Comics Providers (6)
import { searchCataloggdComicDb } from './providers/comics/cataloggdComicDb';
import { searchComicVine } from './providers/comics/comicVine';
import { searchAppleComics } from './providers/comics/appleComics';
import { searchGetComics } from './providers/comics/getComics';
import { searchGoogleBooksComics } from './providers/comics/googleBooksComics';
import { searchOpenLibraryComics } from './providers/comics/openLibraryComics';

// Video Games Providers (6)
import { searchIgdbGames } from './providers/games/igdbGames';
import { searchSteamStore } from './providers/games/steamStore';
import { searchImdbGames } from './providers/games/imdbGames';
import { searchCheapSharkGames } from './providers/games/cheapSharkGames';
import { searchWikidataGames } from './providers/games/wikidataGames';
import { searchWikipediaGames } from './providers/games/wikipediaGames';

// Board Games Providers (6)
import { searchBgg } from './providers/boardgames/bgg';
import { searchWikidataBoardGames } from './providers/boardgames/wikidataBoardGames';
import { searchWikipediaBoardGames } from './providers/boardgames/wikipediaBoardGames';
import { searchOpenLibraryBoardGames } from './providers/boardgames/openLibraryBoardGames';
import { searchGoogleBooksBoardGames } from './providers/boardgames/googleBooksBoardGames';
import { searchBoardGameAtlas } from './providers/boardgames/boardGameAtlas';

// Shared fallback
import { searchWikipedia } from './providers/boardgames/wikipediaBoardGames';

type Bindings = {
  ENVIRONMENT?: string;
  OMDB_API_KEY?: string;
  HARDCOVER_API_TOKEN?: string;
  TMDB_API_KEY?: string;
  TMDB_READ_ACCESS_TOKEN?: string;
  COMIC_VINE_API_KEY?: string;
  RAWG_API_KEY?: string;
  TWITCH_CLIENT_ID?: string;
  TWITCH_CLIENT_SECRET?: string;
  IGDB_CLIENT_ID?: string;
  IGDB_CLIENT_SECRET?: string;
};

const app = new Hono<{ Bindings: Bindings }>();

// Global request logger for Wrangler real-time tails
app.use('*', logger());

// Enable CORS for all origins (Android native & Web Wasm)
app.use('*', cors({
  origin: '*',
  allowMethods: ['GET', 'POST', 'OPTIONS'],
  allowHeaders: ['Content-Type', 'Authorization'],
  maxAge: 86400,
}));

// Health check endpoint
app.get('/health', (c) => c.json({
  status: 'ok',
  engine: 'lists-search-engine',
  version: '2.0.0',
  providersPerCategory: 6,
  time: new Date().toISOString(),
}));

function collectSettled(settled: PromiseSettledResult<SearchResult[]>[]): SearchResult[] {
  const combined: SearchResult[] = [];
  for (const res of settled) {
    if (res.status === 'fulfilled' && Array.isArray(res.value)) {
      combined.push(...res.value);
    }
  }
  return combined;
}

// -----------------------------------------------------------------------
// UNIVERSAL SEARCH ENDPOINT: /v1/search
// -----------------------------------------------------------------------
app.get('/v1/search', async (c) => {
  const startTime = Date.now();
  const q = (c.req.query('q') || '').trim();
  const category = (c.req.query('category') || 'MOVIE').toUpperCase() as Category;
  const subFilter = (c.req.query('subFilter') || 'ALL').toUpperCase() as BookSubFilter & TvSubFilter;
  const explicitYear = c.req.query('year');
  const lang = c.req.query('lang');

  const clientUA = c.req.header('User-Agent') || 'UnknownClient';
  console.log(`[EdgeSearch] [INCOMING] category=${category} q="${q}" subFilter=${subFilter} client="${clientUA}"`);

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
    console.log(`[EdgeSearch] [CACHE-HIT] q="${q}" -> ${cached.length} items (${Date.now() - startTime}ms)`);
    return c.json<SearchResponse>({
      query: q,
      normalizedQuery: searchCleanQuery,
      category,
      source: 'cache',
      count: cached.length,
      results: cached,
    });
  }

  // 2. Query 6 Providers via Two-Tier Waterfall Architecture
  const env = c.env;
  const getEnv = (key: keyof Bindings): string | undefined =>
    env?.[key] || ((globalThis as { process?: { env?: Record<string, string | undefined> } }).process?.env?.[key]);

  const omdbKey = getEnv('OMDB_API_KEY') || 'trilogy';
  const tmdbKey = getEnv('TMDB_API_KEY') || getEnv('TMDB_READ_ACCESS_TOKEN');
  const hardcoverToken = getEnv('HARDCOVER_API_TOKEN');
  const comicVineKey = getEnv('COMIC_VINE_API_KEY');
  const twitchCid = getEnv('TWITCH_CLIENT_ID') || getEnv('IGDB_CLIENT_ID');
  const twitchSecret = getEnv('TWITCH_CLIENT_SECRET') || getEnv('IGDB_CLIENT_SECRET');

  let rawResults: SearchResult[] = [];

  try {
    switch (category) {
      case 'MOVIE': {
        // Tier 1: Fast Core 3
        const tier1 = await Promise.allSettled([
          searchITunesMovies(searchCleanQuery, effectiveYear, lang),
          searchImdbMovies(searchCleanQuery, effectiveYear),
          searchJustWatchMovie(searchCleanQuery, effectiveYear),
        ]);
        rawResults.push(...collectSettled(tier1));

        // Tier 2: Deep 3 (OMDb, TMDb, Wikipedia)
        if (rawResults.length < 5) {
          const tier2 = await Promise.allSettled([
            searchOmdbMovies(searchCleanQuery, omdbKey, effectiveYear),
            searchTmdbMovie(searchCleanQuery, effectiveYear, tmdbKey),
            searchWikipediaMovie(searchCleanQuery, effectiveYear),
          ]);
          rawResults.push(...collectSettled(tier2));
        }
        break;
      }

      case 'TV_SHOW': {
        // Tier 1: Fast Core 3
        const tier1 = await Promise.allSettled([
          searchTvMaze(searchCleanQuery, effectiveYear),
          searchItunesTv(searchCleanQuery, effectiveYear),
          searchImdbTv(searchCleanQuery, effectiveYear),
        ]);
        rawResults.push(...collectSettled(tier1));

        // Tier 2: Deep 3
        if (rawResults.length < 5) {
          const tier2 = await Promise.allSettled([
            searchTmdbTv(searchCleanQuery, effectiveYear, tmdbKey),
            searchOmdbTv(searchCleanQuery, effectiveYear, omdbKey),
            searchWikipediaTv(searchCleanQuery, effectiveYear),
          ]);
          rawResults.push(...collectSettled(tier2));
        }
        break;
      }

      case 'ANIME': {
        // Tier 1: Fast Core 3
        const tier1 = await Promise.allSettled([
          searchAnilistAnime(searchCleanQuery, effectiveYear),
          searchKitsuAnime(searchCleanQuery, effectiveYear),
          searchJikanAnime(searchCleanQuery, effectiveYear),
        ]);
        rawResults.push(...collectSettled(tier1));

        // Tier 2: Deep 3
        if (rawResults.length < 5) {
          const tier2 = await Promise.allSettled([
            searchTvmazeAnime(searchCleanQuery, effectiveYear),
            searchImdbAnime(searchCleanQuery, effectiveYear),
            searchWikidataAnime(searchCleanQuery, effectiveYear),
          ]);
          rawResults.push(...collectSettled(tier2));
        }
        break;
      }

      case 'BOOK': {
        // Tier 1: Fast Core 3
        const tier1 = await Promise.allSettled([
          searchAppleBooks(searchCleanQuery, lang, 'BOOK'),
          searchOpenLibrary(searchCleanQuery, 'ALL', lang),
          searchGoogleBooks(searchCleanQuery, 'ALL', 'BOOK'),
        ]);
        rawResults.push(...collectSettled(tier1));

        // Tier 2: Deep 3
        if (rawResults.length < 5) {
          const tier2 = await Promise.allSettled([
            searchHardcover(searchCleanQuery, hardcoverToken),
            searchWikipediaBooks(searchCleanQuery, effectiveYear),
            searchCrossrefBooks(searchCleanQuery, effectiveYear),
          ]);
          rawResults.push(...collectSettled(tier2));
        }
        break;
      }

      case 'MANGA': {
        // Tier 1: Fast Core 3
        const tier1 = await Promise.allSettled([
          searchMangaDex(searchCleanQuery, effectiveYear),
          searchAnilistManga(searchCleanQuery, effectiveYear),
          searchJikanManga(searchCleanQuery, effectiveYear),
        ]);
        rawResults.push(...collectSettled(tier1));

        // Tier 2: Deep 3
        if (rawResults.length < 5) {
          const tier2 = await Promise.allSettled([
            searchKitsuManga(searchCleanQuery, effectiveYear),
            searchGoogleBooksManga(searchCleanQuery, effectiveYear),
            searchOpenLibraryManga(searchCleanQuery, effectiveYear),
          ]);
          rawResults.push(...collectSettled(tier2));
        }
        break;
      }

      case 'COMIC': {
        // Tier 1: Fast Core 3
        const tier1 = await Promise.allSettled([
          searchCataloggdComicDb(searchCleanQuery),
          searchAppleComics(searchCleanQuery, effectiveYear),
          searchGetComics(searchCleanQuery),
        ]);
        rawResults.push(...collectSettled(tier1));

        // Tier 2: Deep 3
        if (rawResults.length < 5) {
          const tier2 = await Promise.allSettled([
            searchComicVine(searchCleanQuery, effectiveYear, comicVineKey),
            searchGoogleBooksComics(searchCleanQuery, effectiveYear),
            searchOpenLibraryComics(searchCleanQuery, effectiveYear),
          ]);
          rawResults.push(...collectSettled(tier2));
        }
        break;
      }

      case 'GAME': {
        // Tier 1: Fast Core 3 (IGDB primary, Steam Store, IMDb Games)
        const tier1 = await Promise.allSettled([
          searchIgdbGames(searchCleanQuery, effectiveYear, twitchCid, twitchSecret),
          searchSteamStore(searchCleanQuery),
          searchImdbGames(searchCleanQuery),
        ]);
        rawResults.push(...collectSettled(tier1));

        // Tier 2: Deep 3 (CheapShark, Wikidata, Wikipedia)
        if (rawResults.length < 5) {
          const tier2 = await Promise.allSettled([
            searchCheapSharkGames(searchCleanQuery, effectiveYear),
            searchWikidataGames(searchCleanQuery, effectiveYear),
            searchWikipediaGames(searchCleanQuery, effectiveYear),
          ]);
          rawResults.push(...collectSettled(tier2));
        }
        break;
      }

      case 'BOARD_GAME': {
        // Tier 1: Fast Core 3
        const tier1 = await Promise.allSettled([
          searchBgg(searchCleanQuery),
          searchWikidataBoardGames(searchCleanQuery),
          searchWikipediaBoardGames(searchCleanQuery),
        ]);
        rawResults.push(...collectSettled(tier1));

        // Tier 2: Deep 3
        if (rawResults.length < 5) {
          const tier2 = await Promise.allSettled([
            searchBoardGameAtlas(searchCleanQuery, effectiveYear),
            searchOpenLibraryBoardGames(searchCleanQuery, effectiveYear),
            searchGoogleBooksBoardGames(searchCleanQuery, effectiveYear),
          ]);
          rawResults.push(...collectSettled(tier2));
        }
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

  console.log(`[EdgeSearch] [RESPONSE] q="${q}" -> ${ranked.length} items (${Date.now() - startTime}ms, source: live)`);

  return c.json<SearchResponse>({
    query: q,
    normalizedQuery: searchCleanQuery,
    category,
    source: 'live',
    count: ranked.length,
    results: ranked,
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
  let chaptersCount = 0;

  // 1. Fetch TV Episodes from TVMaze if TV show
  if (category === 'TV_SHOW') {
    try {
      const term = encodeURIComponent(title);
      const res = await fetch(`https://api.tvmaze.com/singlesearch/shows?q=${term}&embed=episodes`, {
        signal: AbortSignal.timeout(2500),
      });
      if (res.ok) {
        const data = await res.json() as { _embedded?: { episodes?: Array<{ season?: number; number?: number; name?: string; airdate?: string }> } };
        const eps = data._embedded?.episodes || [];
        for (const ep of eps) {
          episodes.push({
            season: ep.season || 1,
            number: ep.number || 1,
            name: ep.name || `Episode ${ep.number}`,
            airdate: ep.airdate || '',
          });
        }
      }
    } catch (_) {}
  }

  // 2. Fetch Streaming Offers from JustWatch for Movies & TV
  if (category === 'MOVIE' || category === 'TV_SHOW') {
    try {
      const jwResults = await searchJustWatchMovie(title);
      if (jwResults.length > 0) {
        platforms.push(
          { name: 'Apple TV', url: `https://tv.apple.com/search?term=${encodeURIComponent(title)}` },
          { name: 'Prime Video', url: `https://www.amazon.com/s?k=${encodeURIComponent(title)}&i=instant-video` },
          { name: 'Netflix', url: `https://www.netflix.com/search?q=${encodeURIComponent(title)}` }
        );
      }
    } catch (_) {}
  }

  // 3. Manga chapter count enrichment from MangaDex
  if (category === 'MANGA') {
    try {
      const mdResults = await searchMangaDex(title);
      if (mdResults.length > 0 && mdResults[0].totalPages > 0) {
        chaptersCount = mdResults[0].totalPages;
      }
    } catch (_) {}
  }

  return c.json<EnrichResponse>({
    title,
    category,
    platforms,
    episodes,
    chaptersCount: chaptersCount || undefined,
  });
});

export default app;
