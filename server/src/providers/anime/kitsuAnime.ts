import { SearchResult } from '../../types';

export async function searchKitsuAnime(query: string, year?: string): Promise<SearchResult[]> {
  try {
    const url = `https://kitsu.io/api/edge/anime?filter[text]=${encodeURIComponent(query)}&page[limit]=6`;
    const res = await fetch(url, {
      signal: AbortSignal.timeout(1800),
      headers: {
        'Accept': 'application/vnd.api+json',
        'User-Agent': 'ListsEngine/2.0 (media-aggregator)',
      },
    });
    if (!res.ok) return [];

    const data = await res.json() as { data?: Array<{
      id: string;
      attributes?: {
        canonicalTitle?: string;
        titles?: { en?: string; en_jp?: string; ja_jp?: string };
        synopsis?: string;
        startDate?: string;
        posterImage?: { large?: string; original?: string; medium?: string };
        averageRating?: string;
        episodeCount?: number;
      };
    }> };

    if (!data.data || !Array.isArray(data.data)) return [];

    return data.data.map(item => {
      const attr = item.attributes || {};
      const title = attr.titles?.en || attr.canonicalTitle || attr.titles?.en_jp || '';
      const releaseYear = attr.startDate ? attr.startDate.slice(0, 4) : (year || '');
      const coverUrl = attr.posterImage?.large || attr.posterImage?.original || attr.posterImage?.medium || '';
      const rawRating = attr.averageRating ? parseFloat(attr.averageRating) / 10 : 0;

      return {
        title,
        creator: '',
        releaseYear,
        releaseDate: attr.startDate || releaseYear,
        coverUrl,
        overview: attr.synopsis || '',
        rating: Math.round(rawRating * 10) / 10,
        totalEpisodes: attr.episodeCount || 0,
        totalPages: 0,
        category: 'ANIME' as const,
        externalUrl: `https://kitsu.io/anime/${item.id}`,
      };
    });
  } catch {
    return [];
  }
}
