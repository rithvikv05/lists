import { SearchResult } from '../../types';

export async function searchOpenLibrary(
  query: string,
  searchType: 'ALL' | 'TITLE' | 'AUTHOR' = 'ALL',
  lang?: string
): Promise<SearchResult[]> {
  const results: SearchResult[] = [];
  try {
    const term = encodeURIComponent(query.trim());
    const typeParam = searchType === 'TITLE' ? `title=${term}` : searchType === 'AUTHOR' ? `author=${term}` : `q=${term}`;
    const url = `https://openlibrary.org/search.json?${typeParam}&limit=25`;

    const res = await fetch(url, {
      headers: { 'User-Agent': 'ListsSearchEngine/1.0' },
      signal: AbortSignal.timeout(2200)
    });

    if (res.ok) {
      const data = await res.json() as any;
      const docs = data.docs || [];
      for (const doc of docs) {
        const title = doc.title;
        if (!title) continue;

        const authors = (doc.author_name || []).join(', ');
        const firstYear = doc.first_publish_year ? String(doc.first_publish_year) : '';
        const coverI = doc.cover_i;
        const coverUrl = coverI ? `https://covers.openlibrary.org/b/id/${coverI}-L.jpg` : '';
        const key = doc.key || '';
        const rating = doc.ratings_average ? Number(doc.ratings_average) * 2 : 0;
        const pages = doc.number_of_pages_median || doc.number_of_pages || 0;

        results.push({
          title,
          creator: authors,
          releaseYear: firstYear,
          releaseDate: firstYear,
          coverUrl,
          overview: doc.first_sentence?.[0] || '',
          rating,
          totalEpisodes: 0,
          totalPages: pages,
          category: 'BOOK',
          externalUrl: key ? `https://openlibrary.org${key}` : ''
        });
      }
    }
  } catch (_) {}
  return results;
}

