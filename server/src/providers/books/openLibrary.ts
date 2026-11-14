import { SearchResult } from '../../types';
import { searchOpenLibrary as searchBooks } from '../../books/providers/openLibrary';
import { toSearchResult } from '../../books/projection';
export async function searchOpenLibrary(query: string, mode: 'ALL' | 'TITLE' | 'AUTHOR' = 'ALL', lang?: string): Promise<SearchResult[]> {
  const results = await searchBooks(query, mode.toLowerCase() as 'all' | 'title' | 'author', lang, 20);
  return results.map(book => toSearchResult(book));
}
