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

export interface Contributor { name: string; role: string }
export interface SourceRef { source: string; id: string; url: string }
export interface SourceRating { source: string; value: number; scale: number; count?: number }
export interface MetadataEvidence { source: string; sourceId: string; fields: Record<string, unknown> }
export interface EditionMetadata {
  source: string; sourceId: string; isbn?: string; language?: string; publisher?: string;
  totalPages?: number; releaseDate?: string; coverUrl?: string;
}

export interface SearchResult {
  bookMetadata?: import('./books/types').BookMetadata;
  id?: string;
  catalogRevision?: number;
  catalogUpdatedAt?: string;
  offlineSnapshot?: Record<string, unknown>;
  originalTitle?: string;
  backdropUrl?: string;
  isbn?: string;
  publisher?: string;
  language?: string;
  entityKind?: string;
  aliases?: string[];
  genres?: string[];
  themes?: string[];
  demographics?: string[];
  platforms?: string[];
  creators?: Contributor[];
  totalChapters?: number;
  totalVolumes?: number;
  totalIssues?: number;
  runtimeMinutes?: number;
  status?: string;
  sourceRefs?: SourceRef[];
  ratings?: SourceRating[];
  /** All supplied fields, including alternative values, retained with provenance. */
  metadata?: MetadataEvidence[];
  editions?: EditionMetadata[];
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
  status?: 'complete' | 'partial';
  incompleteSources?: string[];
  schemaVersion?: number;
  freshness?: 'live' | 'catalog' | 'stale';
  catalogState?: import('./catalog/store').CatalogState;
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

