import { SearchResult } from '../../types';

export async function searchGoogleBooksComics(query: string, year?: string): Promise<SearchResult[]> {
  try {
    const q = `${query} comic graphic novel`;
    const url = `https://www.googleapis.com/books/v1/volumes?q=${encodeURIComponent(q)}&maxResults=6&printType=books`;
    const res = await fetch(url, {
      signal: AbortSignal.timeout(1800),
      headers: { 'User-Agent': 'ListsEngine/2.0 (media-aggregator)' },
    });
    if (!res.ok) return [];

    const data = await res.json() as {
      items?: Array<{
        id: string;
        volumeInfo: {
          title: string;
          authors?: string[];
          publishedDate?: string;
          description?: string;
          pageCount?: number;
          averageRating?: number;
          imageLinks?: { thumbnail?: string; smallThumbnail?: string };
          infoLink?: string;
        };
      }>;
    };

    if (!data.items) return [];

    return data.items.map(item => {
      const v = item.volumeInfo;
      const releaseYear = v.publishedDate ? v.publishedDate.slice(0, 4) : (year || '');
      let cover = v.imageLinks?.thumbnail || v.imageLinks?.smallThumbnail || '';
      if (cover.startsWith('http://')) cover = cover.replace('http://', 'https://');

      return {
        title: v.title,
        creator: v.authors ? v.authors.join(', ') : '',
        releaseYear,
        releaseDate: v.publishedDate || releaseYear,
        coverUrl: cover,
        overview: v.description || '',
        rating: v.averageRating ? Math.round(v.averageRating * 20) / 10 : 0,
        totalEpisodes: 0,
        totalPages: v.pageCount || 0,
        category: 'COMIC' as const,
        externalUrl: v.infoLink || `https://books.google.com/books?id=${item.id}`,
      };
    });
  } catch {
    return [];
  }
}

