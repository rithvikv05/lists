import { searchITunesMovies } from '../providers/movies/itunesMovie';
import { searchOmdbMovies } from '../providers/movies/omdbMovie';
import { searchTmdbMovie } from '../providers/movies/tmdbMovie';
import { searchTvMaze } from '../providers/tv/tvmaze';
import { searchTmdbTv } from '../providers/tv/tmdbTv';
import { searchAnilistAnime } from '../providers/anime/anilistAnime';
import { searchJikanAnime } from '../providers/anime/jikanAnime';
import { searchKitsuAnime } from '../providers/anime/kitsuAnime';
import { searchAppleBooks } from '../providers/books/appleBooks';
import { searchInventaire } from '../providers/books/inventaire';
import { searchOpenLibrary } from '../providers/books/openLibrary';
import { searchGoogleBooks } from '../providers/books/googleBooks';
import { searchHardcover } from '../providers/books/hardcover';
import { searchMangaDex } from '../providers/manga/mangadex';
import { searchAnilistManga } from '../providers/manga/anilistManga';
import { searchJikanManga } from '../providers/manga/jikanManga';
import { searchKitsuManga } from '../providers/manga/kitsuManga';
import { searchComicVine } from '../providers/comics/comicVine';
import { searchIgdbGames } from '../providers/games/igdbGames';
import { searchSteamStore } from '../providers/games/steamStore';
import { searchBgg } from '../providers/boardgames/bgg';
import { searchWikidataBoardGames } from '../providers/boardgames/wikidataBoardGames';
import { searchWikipedia } from '../providers/boardgames/wikipediaBoardGames';
import { Category, SearchResult } from '../types';
export interface EngineEnv {
  CATALOG_DB?: D1Database;
  TMDB_API_KEY?: string; TMDB_READ_ACCESS_TOKEN?: string; OMDB_API_KEY?: string;
  HARDCOVER_API_TOKEN?: string; COMIC_VINE_API_KEY?: string; BGG_API_TOKEN?: string;
  TWITCH_CLIENT_ID?: string; TWITCH_CLIENT_SECRET?: string; IGDB_CLIENT_ID?: string; IGDB_CLIENT_SECRET?: string;
  GOOGLE_BOOKS_API_KEY?: string; ISBNDB_API_KEY?: string;
  ENABLE_ANILIST?: string; ENVIRONMENT?: string;
}
export interface SearchOptions { query: string; category: Category; author?: string; year?: string; yearStart?: number; yearEnd?: number; lang?: string; country?: string; mode: 'ALL' | 'TITLE' | 'AUTHOR'; includeExtras?: boolean; backup?: boolean }
export interface ProviderTask { source: string; run: () => Promise<SearchResult[]> }
export function providerPlan(options: SearchOptions, env: EngineEnv): ProviderTask[] {
  const { query: q, category, year, lang, mode } = options;
  const tmdb = env.TMDB_API_KEY || env.TMDB_READ_ACCESS_TOKEN;
  const tasks: ProviderTask[] = [];
  const add = (source: string, run: ProviderTask['run']) => tasks.push({ source, run });
  switch (category) {
    case 'MOVIE':
      if (tmdb) add('tmdb', () => searchTmdbMovie(q, year, tmdb, lang));
      add('apple', () => searchITunesMovies(q, year, lang));
      if (env.OMDB_API_KEY) add('omdb', () => searchOmdbMovies(q, env.OMDB_API_KEY, year));
      break;
    case 'TV_SHOW':
      if (tmdb) add('tmdb', () => searchTmdbTv(q, year, tmdb, lang));
      add('tvmaze', () => searchTvMaze(q, year));
      break;
    case 'BOOK':
      add('openlibrary', () => searchOpenLibrary(options.author ? `title:"${q.replace(/"/g, '')}" AND author:"${options.author}"` : q, options.author ? 'ALL' : mode, lang));
      add('googlebooks', () => searchGoogleBooks(options.author ? `intitle:"${q.replace(/"/g, '')}" inauthor:"${options.author}"` : q, options.author ? 'ALL' : mode, 'BOOK', lang, env.GOOGLE_BOOKS_API_KEY));
      if (env.HARDCOVER_API_TOKEN && mode !== 'AUTHOR') add('hardcover', () => searchHardcover(options.author ? `${q} ${options.author}` : q, env.HARDCOVER_API_TOKEN));
      break;
    case 'GAME': {
      const id = env.TWITCH_CLIENT_ID || env.IGDB_CLIENT_ID;
      const secret = env.TWITCH_CLIENT_SECRET || env.IGDB_CLIENT_SECRET;
      if (id && secret) add('igdb', () => searchIgdbGames(q, year, id, secret));
      add('steam', () => searchSteamStore(q));
      break;
    }
    case 'ANIME':
      add('kitsu', () => searchKitsuAnime(q, year));
      add('jikan', () => searchJikanAnime(q, year));
      if (env.ENABLE_ANILIST === 'true') add('anilist', () => searchAnilistAnime(q, year));
      break;
    case 'MANGA':
      add('kitsu', () => searchKitsuManga(q, year));
      add('jikan', () => searchJikanManga(q, year));
      add('mangadex', () => searchMangaDex(q, year));
      if (env.ENABLE_ANILIST === 'true') add('anilist', () => searchAnilistManga(q, year));
      break;
    case 'COMIC':
      if (env.COMIC_VINE_API_KEY) add('comicvine', () => searchComicVine(q, year, env.COMIC_VINE_API_KEY));
      add('googlebooks', () => searchGoogleBooks(q + ' comic', mode, 'COMIC', lang, env.GOOGLE_BOOKS_API_KEY));
      break;
    case 'BOARD_GAME':
      if (env.BGG_API_TOKEN) add('bgg', () => searchBgg(q, env.BGG_API_TOKEN));
      add('wikidata', () => searchWikidataBoardGames(q));
      break;
    case 'PODCAST': case 'MUSIC':
      add('wikipedia', () => searchWikipedia(q, category));
  }
  return tasks;
}
export function fallbackPlan(options: SearchOptions): ProviderTask[] {
  if (options.category === 'BOOK') return [
    { source: 'apple', run: () => searchAppleBooks(options.author ? `${options.query} ${options.author}` : options.query, options.lang) },
    ...(options.mode !== 'AUTHOR' ? [{ source: 'inventaire', run: () => searchInventaire(options.query, options.lang) }] : []),
    { source: 'wikipedia', run: () => searchWikipedia(options.query, options.category) },
  ];
  return [{ source: 'wikipedia', run: () => searchWikipedia(options.query, options.category) }];
}
