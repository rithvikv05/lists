import { SearchResult } from '../../types';

export async function searchGoogleBooks(
  query: string,
  searchType: 'ALL' | 'TITLE' | 'AUTHOR' = 'ALL',
  category: 'BOOK' | 'COMIC' | 'MANGA' = 'BOOK'
): Promise<SearchResult[]> {
  const results: SearchResult[] = [];
  try {
    const term = encodeURIComponent(query);
    const prefix = searchType === 'TITLE' ? 'intitle:' : searchType === 'AUTHOR' ? 'inauthor:' : '';
    const url = `https://www.googleapis.com/books/v1/volumes?q=${prefix}${term}&maxResults=15`;

    const res = await fetch(url, {
      headers: { 'User-Agent': 'ListsSearchEngine/1.0' },
      signal: AbortSignal.timeout(2200)
    });

    if (res.ok) {
      const data = await res.json() as any;
      const items = data.items || [];
      for (const item of items) {
        const info = item.volumeInfo;
        if (!info || !info.title) continue;

        const authors = (info.authors || []).join(', ');
        const publishedDate = info.publishedDate || '';
        const releaseYear = publishedDate.slice(0, 4);

        const rawThumb = info.imageLinks?.thumbnail || info.imageLinks?.smallThumbnail || '';
        const cover = rawThumb
          ? rawThumb.replace(/^http:\/\//, 'https://').replace(/&edge=curl/, '').replace(/zoom=[12]/, 'zoom=3')
          : '';

        results.push({
          title: info.title,
          creator: authors,
          releaseYear,
          releaseDate: publishedDate,
          coverUrl: cover,
          overview: (info.description || '').replace(/<[^>]*>/g, ''),
          rating: info.averageRating ? Number(info.averageRating) * 2 : 0,
          totalEpisodes: 0,
          totalPages: info.pageCount || 0,
          category,
          externalUrl: info.previewLink || info.infoLink || ''
        });
      }
    }
  } catch (_) {}
  return results;
}

