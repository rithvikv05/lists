import { SearchResult } from '../../types';

export async function searchTvmazeAnime(query: string, year?: string): Promise<SearchResult[]> {
  try {
    const url = `https://api.tvmaze.com/search/shows?q=${encodeURIComponent(query)}`;
    const res = await fetch(url, {
      signal: AbortSignal.timeout(1800),
      headers: { 'User-Agent': 'ListsEngine/2.0 (media-aggregator)' },
    });
    if (!res.ok) return [];

    const data = await res.json() as Array<{
      show: {
        id: number;
        name: string;
        type?: string;
        genres?: string[];
        premiered?: string;
        image?: { original?: string; medium?: string };
        summary?: string;
        rating?: { average?: number };
        network?: { name: string };
        webChannel?: { name: string };
        url?: string;
      };
    }>;

    if (!Array.isArray(data)) return [];

    return data
      .filter(item => {
        const genres = item.show.genres || [];
        const type = item.show.type || '';
        return genres.includes('Anime') || type === 'Animation';
      })
      .map(item => {
        const s = item.show;
        const releaseYear = s.premiered ? s.premiered.slice(0, 4) : (year || '');
        const cleanSummary = (s.summary || '').replace(/<[^>]*>/g, '').trim();

        return {
          title: s.name,
          creator: s.network?.name || s.webChannel?.name || '',
          releaseYear,
          releaseDate: s.premiered || releaseYear,
          coverUrl: s.image?.original || s.image?.medium || '',
          overview: cleanSummary,
          rating: s.rating?.average || 0,
          totalEpisodes: 0,
          totalPages: 0,
          category: 'ANIME' as const,
          externalUrl: s.url || `https://www.tvmaze.com/shows/${s.id}`,
        };
      });
  } catch {
    return [];
  }
}
