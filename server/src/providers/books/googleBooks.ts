import { SearchResult } from '../../types';
import { searchGoogleBooks as searchBooks } from '../../books/providers/googleBooks';
import { toSearchResult } from '../../books/projection';
export async function searchGoogleBooks(query: string, mode: 'ALL' | 'TITLE' | 'AUTHOR' = 'ALL', category: 'BOOK' | 'COMIC' | 'MANGA' = 'BOOK', lang?: string, apiKey?: string): Promise<SearchResult[]> {
  const results = await searchBooks(query, mode.toLowerCase() as 'all' | 'title' | 'author', lang, 20, apiKey);
  return results.map(book => toSearchResult(book, category));
}
