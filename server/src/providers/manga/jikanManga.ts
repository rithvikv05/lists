import { providerFetch } from '../http';
import { SearchResult } from '../../types';

export async function searchJikanManga(query: string, year?: string): Promise<SearchResult[]> {
  try {
    const url = `https://api.jikan.moe/v4/manga?q=${encodeURIComponent(query)}&limit=6&sfw=true`;
    const res = await providerFetch(url, {
      signal: AbortSignal.timeout(1800),
      headers: { 'User-Agent': 'ListsEngine/2.0 (media-aggregator)' },
    });
    if (!res.ok) return [];

    const data = await res.json() as { data?: Array<{
      mal_id?: number;
      title: string;
      title_english?: string;
      title_japanese?: string;
      published?: { from?: string; prop?: { from?: { year?: number } } };
      images?: { webp?: { large_image_url?: string }; jpg?: { large_image_url?: string } };
      synopsis?: string;
      score?: number;
      scored_by?: number;
      genres?: Array<{ name: string }>;
      themes?: Array<{ name: string }>;
      demographics?: Array<{ name: string }>;
      status?: string;
      chapters?: number;
      volumes?: number;
      authors?: Array<{ name: string }>;
      url?: string;
    }> };

    if (!data.data || !Array.isArray(data.data)) return [];

    return data.data.map(item => {
      const releaseYear = item.published?.prop?.from?.year
        ? String(item.published.prop.from.year)
        : '';

      const coverUrl = item.images?.webp?.large_image_url || item.images?.jpg?.large_image_url || '';
      const author = item.authors?.[0]?.name || '';

      return {
        title: item.title_english || item.title,
        sourceRefs: item.mal_id ? [
          { source: 'jikan', id: 'manga:' + item.mal_id, url: item.url || '' },
          { source: 'myanimelist', id: 'manga:' + item.mal_id, url: `https://myanimelist.net/manga/${item.mal_id}` },
        ] : [],
        originalTitle: item.title_japanese || '',
        aliases: [item.title, item.title_english || '', item.title_japanese || ''].filter(Boolean),
        creator: author,
        creators: (item.authors || []).map(a => ({ name: a.name, role: 'author' })),
        genres: (item.genres || []).map(g => g.name),
        themes: (item.themes || []).map(g => g.name),
        demographics: (item.demographics || []).map(g => g.name),
        status: item.status || '',
        ratings: item.score ? [{ source: 'myanimelist', value: item.score, scale: 10, count: item.scored_by }] : [],
        releaseYear,
        releaseDate: item.published?.from || releaseYear,
        coverUrl,
        overview: item.synopsis || '',
        rating: item.score ? Math.round(item.score * 10) / 10 : 0,
        totalEpisodes: 0,
        totalPages: 0,
        totalChapters: item.chapters || 0,
        totalVolumes: item.volumes || 0,
        category: 'MANGA' as const,
        externalUrl: item.url || '',
      };
    });
  } catch {
    return [];
  }
}

