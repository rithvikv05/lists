import { SearchResult } from '../../types';

export async function searchItunesTv(query: string, year?: string): Promise<SearchResult[]> {
  try {
    const url = `https://itunes.apple.com/search?media=tvShow&entity=tvSeason&limit=6&term=${encodeURIComponent(query)}`;
    const res = await fetch(url, {
      signal: AbortSignal.timeout(1800),
      headers: { 'User-Agent': 'ListsEngine/2.0 (media-aggregator)' },
    });
    if (!res.ok) return [];

    const data = await res.json() as { results?: Array<{
      collectionName: string;
      artistName?: string;
      releaseDate?: string;
      artworkUrl100?: string;
      longDescription?: string;
      shortDescription?: string;
      trackCount?: number;
      collectionViewUrl?: string;
    }> };

    if (!data.results) return [];

    return data.results.map(item => {
      // Clean "Season X" from collectionName to get core series title
      const cleanTitle = item.collectionName.replace(/\s*,?\s*Season\s*\d+.*$/i, '').trim();
      const releaseYear = item.releaseDate ? item.releaseDate.slice(0, 4) : (year || '');
      const cover = item.artworkUrl100
        ? item.artworkUrl100.replace(/100x100bb\.jpg/i, '1200x1200bb.jpg')
        : '';

      return {
        title: cleanTitle,
        creator: item.artistName || '',
        releaseYear,
        releaseDate: item.releaseDate || '',
        coverUrl: cover,
        overview: item.longDescription || item.shortDescription || '',
        rating: 0,
        totalEpisodes: item.trackCount || 0,
        totalPages: 0,
        category: 'TV_SHOW' as const,
        externalUrl: item.collectionViewUrl || '',
      };
    });
  } catch {
    return [];
  }
}

