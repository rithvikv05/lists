import { providerFetch } from '../http';
import { SearchResult } from '../../types';
import { normalizeIsbn } from '../../merger/entityMerger';

/** Use Hardcover's search index; wildcard SQL filters are prohibited by its API. */
export async function searchHardcover(query: string, apiToken?: string): Promise<SearchResult[]> {
  if (!apiToken) return [];
  const response = await providerFetch('https://api.hardcover.app/v1/graphql', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'User-Agent': 'ListsSearchEngine/1.0',
      Authorization: `Bearer ${apiToken.replace(/^Bearer\s+/i, '').trim()}` },
    body: JSON.stringify({ query: `query SearchBooks($query: String!) {
      search(query: $query, query_type: "book", per_page: 15) { results error }
    }`, variables: { query: query.trim() } }),
    signal: AbortSignal.timeout(2200),
  });
  if (!response.ok) return [];
  const body = await response.json() as any;
  const search = body.data?.search;
  if (!search || search.error) throw new Error('Hardcover search unavailable');
  const payload = typeof search.results === 'string' ? JSON.parse(search.results) : search.results;
  if (!Array.isArray(payload?.hits)) throw new Error('Invalid Hardcover search response');
  return payload.hits.flatMap((hit: any): SearchResult[] => {
    const b = hit.document;
    if (!b?.title || !/^\d+$/.test(String(b.id))) return [];
    const authors = (Array.isArray(b.author_names) ? b.author_names : []).filter((name: unknown) => typeof name === 'string');
    const url = `https://hardcover.app/books/${encodeURIComponent(b.slug || String(b.id))}`;
    const isbns: string[] = [...new Set<string>((Array.isArray(b.isbns) ? b.isbns : []).map((isbn: string) => normalizeIsbn(isbn)).filter(Boolean))];
    return [{
      title: b.title, sourceRefs: [{ source: 'hardcover', id: String(b.id), url }],
      entityKind: 'book', aliases: b.alternative_titles || [],
      creator: authors.join(', '), creators: authors.map((name: string) => ({ name, role: 'author' })),
      releaseYear: b.release_year ? String(b.release_year) : '', releaseDate: b.release_date || '',
      coverUrl: b.image?.url || '', overview: String(b.description || '').replace(/<[^>]*>/g, ''),
      rating: typeof b.rating === 'number' ? b.rating * 2 : 0,
      ratings: typeof b.rating === 'number' ? [{ source: 'hardcover', value: b.rating, scale: 5, count: b.ratings_count }] : [],
      genres: Array.isArray(b.genres) ? b.genres : [],
      totalPages: Number.isInteger(b.pages) && b.pages > 0 ? b.pages : 0, totalEpisodes: 0,
      // The index lists ISBNs across editions; do not assign the work's page
      // count, language or publication date to every one of those editions.
      editions: isbns.map(isbn => ({ source: 'hardcover', sourceId: String(b.id), isbn })),
      category: 'BOOK', externalUrl: url,
    }];
  });
}
