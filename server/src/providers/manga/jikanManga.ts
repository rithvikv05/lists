import { SearchResult } from '../../types';

export async function searchJikanManga(query: string, year?: string): Promise<SearchResult[]> {
  try {
    const url = `https://api.jikan.moe/v4/manga?q=${encodeURIComponent(query)}&limit=6&sfw=true`;
    const res = await fetch(url, {
      signal: AbortSignal.timeout(1800),
      headers: { 'User-Agent': 'ListsEngine/2.0 (media-aggregator)' },
    });
    if (!res.ok) return [];

    const data = await res.json() as { data?: Array<{
      title: string;
      title_english?: string;
      title_japanese?: string;
      published?: { from?: string; prop?: { from?: { year?: number } } };
      images?: { webp?: { large_image_url?: string }; jpg?: { large_image_url?: string } };
      synopsis?: string;
      score?: number;
      chapters?: number;
      authors?: Array<{ name: string }>;
      url?: string;
    }> };

    if (!data.data || !Array.isArray(data.data)) return [];

    return data.data.map(item => {
      const releaseYear = item.published?.prop?.from?.year
        ? String(item.published.prop.from.year)
        : (year || '');

      const coverUrl = item.images?.webp?.large_image_url || item.images?.jpg?.large_image_url || '';
      const author = item.authors?.[0]?.name || '';

      return {
        title: item.title_english || item.title,
        creator: author,
        releaseYear,
        releaseDate: item.published?.from || releaseYear,
        coverUrl,
        overview: item.synopsis || '',
        rating: item.score ? Math.round(item.score * 10) / 10 : 0,
        totalEpisodes: 0,
        totalPages: item.chapters || 0,
        category: 'MANGA' as const,
        externalUrl: item.url || '',
      };
    });
  } catch {
    return [];
  }
}

