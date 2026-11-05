import { SearchResult } from '../../types';

export async function searchOpenLibraryBoardGames(query: string, year?: string): Promise<SearchResult[]> {
  try {
    const url = `https://openlibrary.org/search.json?q=${encodeURIComponent(query)}&subject=board_games&limit=6`;
    const res = await fetch(url, {
      signal: AbortSignal.timeout(1800),
      headers: { 'User-Agent': 'ListsEngine/2.0 (media-aggregator)' },
    });
    if (!res.ok) return [];

    const data = await res.json() as {
      docs?: Array<{
        title: string;
        author_name?: string[];
        first_publish_year?: number;
        cover_i?: number;
        ratings_average?: number;
        key: string;
      }>;
    };

    if (!data.docs) return [];

    return data.docs.map(doc => {
      const releaseYear = doc.first_publish_year ? String(doc.first_publish_year) : (year || '');
      const coverUrl = doc.cover_i ? `https://covers.openlibrary.org/b/id/${doc.cover_i}-L.jpg` : '';
      const rating = doc.ratings_average ? Math.round(doc.ratings_average * 20) / 10 : 0;

      return {
        title: doc.title,
        creator: doc.author_name ? doc.author_name.join(', ') : '',
        releaseYear,
        releaseDate: releaseYear,
        coverUrl,
        overview: '',
        rating,
        totalEpisodes: 0,
        totalPages: 0,
        category: 'BOARD_GAME' as const,
        externalUrl: `https://openlibrary.org${doc.key}`,
      };
    });
  } catch {
    return [];
  }
}
