import { SearchResult } from '../../types';

export async function searchAppleComics(query: string, year?: string): Promise<SearchResult[]> {
  try {
    const url = `https://itunes.apple.com/search?media=ebook&entity=ebook&limit=6&term=${encodeURIComponent(query + ' comic')}`;
    const res = await fetch(url, {
      signal: AbortSignal.timeout(1800),
      headers: { 'User-Agent': 'ListsEngine/2.0 (media-aggregator)' },
    });
    if (!res.ok) return [];

    const data = await res.json() as {
      results?: Array<{
        trackName: string;
        artistName?: string;
        releaseDate?: string;
        artworkUrl100?: string;
        description?: string;
        averageUserRating?: number;
        trackViewUrl?: string;
        genres?: string[];
      }>;
    };

    if (!data.results) return [];

    return data.results.map(item => {
      const releaseYear = item.releaseDate ? item.releaseDate.slice(0, 4) : (year || '');
      const cover = item.artworkUrl100
        ? item.artworkUrl100.replace(/100x100bb\.jpg/i, '1200x1200bb.jpg')
        : '';

      return {
        title: item.trackName,
        creator: item.artistName || '',
        releaseYear,
        releaseDate: item.releaseDate || '',
        coverUrl: cover,
        overview: item.description || '',
        rating: item.averageUserRating ? Math.round(item.averageUserRating * 20) / 10 : 0,
        totalEpisodes: 0,
        totalPages: 0,
        category: 'COMIC' as const,
        externalUrl: item.trackViewUrl || '',
      };
    });
  } catch {
    return [];
  }
}

