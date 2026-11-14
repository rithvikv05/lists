import { providerFetch } from '../http';
import { SearchResult } from '../../types';

export async function searchJikanAnime(query: string, year?: string): Promise<SearchResult[]> {
  try {
    const url = `https://api.jikan.moe/v4/anime?q=${encodeURIComponent(query)}&limit=6&sfw=true`;
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
      year?: number;
      aired?: { from?: string; prop?: { from?: { year?: number } } };
      images?: { webp?: { large_image_url?: string; image_url?: string }; jpg?: { large_image_url?: string } };
      synopsis?: string;
      score?: number;
      scored_by?: number;
      genres?: Array<{ name: string }>;
      themes?: Array<{ name: string }>;
      demographics?: Array<{ name: string }>;
      status?: string;
      episodes?: number;
      studios?: Array<{ name: string }>;
      url?: string;
    }> };

    if (!data.data || !Array.isArray(data.data)) return [];

    return data.data.map(item => {
      const releaseYear = item.year
        ? String(item.year)
        : item.aired?.prop?.from?.year
        ? String(item.aired.prop.from.year)
        : '';

      const coverUrl = item.images?.webp?.large_image_url || item.images?.jpg?.large_image_url || '';
      const studio = item.studios?.[0]?.name || '';

      return {
        title: item.title_english || item.title,
        sourceRefs: item.mal_id ? [
          { source: 'jikan', id: 'anime:' + item.mal_id, url: item.url || '' },
          { source: 'myanimelist', id: 'anime:' + item.mal_id, url: `https://myanimelist.net/anime/${item.mal_id}` },
        ] : [],
        originalTitle: item.title_japanese || '',
        aliases: [item.title, item.title_english || '', item.title_japanese || ''].filter(Boolean),
        creator: studio,
        creators: (item.studios || []).map(s => ({ name: s.name, role: 'studio' })),
        genres: (item.genres || []).map(g => g.name),
        themes: (item.themes || []).map(g => g.name),
        demographics: (item.demographics || []).map(g => g.name),
        status: item.status || '',
        ratings: item.score ? [{ source: 'myanimelist', value: item.score, scale: 10, count: item.scored_by }] : [],
        releaseYear,
        releaseDate: item.aired?.from || releaseYear,
        coverUrl,
        overview: item.synopsis || '',
        rating: item.score ? Math.round(item.score * 10) / 10 : 0,
        totalEpisodes: item.episodes || 0,
        totalPages: 0,
        category: 'ANIME' as const,
        externalUrl: item.url || '',
      };
    });
  } catch {
    return [];
  }
}

