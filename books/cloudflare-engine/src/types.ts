export type BookSearchSource =
  | 'GOOGLE_BOOKS'
  | 'OPEN_LIBRARY'
  | 'ISBN_DIRECT'
  | 'ISBNDB'
  | 'MANUAL'
  | 'COMBINED';

export type BookPacing = 'SLOW' | 'MEDIUM' | 'FAST';

export type BookMood =
  | 'DARK'
  | 'LIGHTHEARTED'
  | 'ATMOSPHERIC'
  | 'REFLECTIVE'
  | 'TENSE'
  | 'HOPEFUL'
  | 'SAD'
  | 'EMOTIONAL'
  | 'CHALLENGING'
  | 'FUNNY'
  | 'MYSTERIOUS'
  | 'INSPIRING'
  | 'RELAXING'
  | 'INFORMATIVE';

export type BookGenre =
  | 'FICTION'
  | 'NON_FICTION'
  | 'SCI_FI'
  | 'FANTASY'
  | 'MYSTERY_THRILLER'
  | 'ROMANCE'
  | 'HISTORICAL'
  | 'HORROR'
  | 'BIOGRAPHY'
  | 'PHILOSOPHY'
  | 'PSYCHOLOGY'
  | 'SELF_HELP'
  | 'SCIENCE'
  | 'HISTORY'
  | 'POETRY'
  | 'GRAPHIC_NOVEL'
  | 'MANGA';

export type BookLengthBracket = 'SHORT' | 'MEDIUM' | 'LONG' | 'EPIC';

export interface RecommendationVector {
  genres: Record<string, number>; // BookGenre -> weight (0.0 to 1.0)
  moods: Record<string, number>;  // BookMood -> weight (0.0 to 1.0)
  pacing?: BookPacing;
  lengthBracket: BookLengthBracket;
  normalizedTags: string[];
}

export interface BookMetadata {
  id: string; // Canonical identifier (prefers ISBN-13, else ISBN-10, else provider ID)
  title: string;
  subtitle?: string;
  authors: string[];
  primaryAuthor: string;
  isbn10?: string;
  isbn13?: string;
  primaryIsbn?: string;
  hasValidIsbn: boolean;
  publisher?: string;
  publishedYear?: string;
  publishedDate?: string;
  totalPages?: number;
  language?: string;
  overview?: string;
  coverUrl?: string;
  thumbnailCoverUrl?: string;
  rating?: number; // 0.0 to 10.0 scale
  ratingsCount?: number;
  externalUrl?: string;
  source: BookSearchSource;

  // Metadata specifically engineered for recommendation matching:
  rawCategories: string[];
  rawSubjects: string[];
  normalizedGenres: BookGenre[];
  suggestedMoods: BookMood[];
  suggestedPacing?: BookPacing;
  lengthBracket: BookLengthBracket;
  recommendationVector: RecommendationVector;
}

export interface SearchResponse {
  query: string;
  searchType: 'all' | 'title' | 'author' | 'isbn';
  source: 'cache' | 'live';
  count: number;
  results: BookMetadata[];
}

export interface RecommendationRequest {
  favoriteBookIds?: string[]; // IDs or ISBNs of books user loved
  favoriteGenres?: BookGenre[];
  preferredMoods?: BookMood[];
  preferredPacing?: BookPacing;
  candidateBooks?: BookMetadata[]; // Candidate pool to rank (if omitted, searches related genres)
  limit?: number;
}

export interface RecommendedBook {
  book: BookMetadata;
  similarityScore: number; // 0.0 to 100.0%
  matchedMoods: BookMood[];
  matchedGenres: BookGenre[];
  matchReason: string;
}

export interface RecommendationResponse {
  totalCandidates: number;
  recommendations: RecommendedBook[];
}

export interface ReadingSessionLog {
  durationMinutes: number;
  pagesRead: number;
  timestamp?: number;
}

export interface ReadingSpeedCalculationRequest {
  totalPages: number;
  currentPage: number;
  sessions: ReadingSessionLog[];
}

export interface ReadingSpeedCalculationResponse {
  totalPages: number;
  currentPage: number;
  remainingPages: number;
  averagePagesPerHour: number;
  averageMinutesPerPage: number;
  estimatedMinutesToFinish: number;
  estimatedFinishFormatted: string; // e.g. "3h 24m"
  confidenceLevel: 'LOW' | 'MEDIUM' | 'HIGH';
}

