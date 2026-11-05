export type Category =
  | 'MOVIE'
  | 'TV_SHOW'
  | 'ANIME'
  | 'BOOK'
  | 'MANGA'
  | 'COMIC'
  | 'GAME'
  | 'BOARD_GAME'
  | 'PODCAST'
  | 'MUSIC';

export type BookSubFilter = 'ALL' | 'BOOKS' | 'MANGA' | 'COMICS';
export type TvSubFilter = 'ALL' | 'TV_SHOWS' | 'ANIME';

export interface SearchResult {
  title: string;
  creator: string;
  releaseYear: string;
  releaseDate: string;
  coverUrl: string;
  overview: string;
  rating: number; // 0.0 to 10.0 scale
  totalEpisodes: number;
  totalPages: number;
  category: Category;
  externalUrl: string;
}

export interface SearchResponse {
  query: string;
  normalizedQuery: string;
  category: Category;
  source: 'cache' | 'live';
  count: number;
  results: SearchResult[];
}

export interface OttPlatform {
  name: string;
  url: string;
}

export interface EpisodeItem {
  season: number;
  number: number;
  name: string;
  airdate: string;
}

export interface EnrichResponse {
  title: string;
  category: Category;
  platforms: OttPlatform[];
  episodes: EpisodeItem[];
  chaptersCount?: number;
}

