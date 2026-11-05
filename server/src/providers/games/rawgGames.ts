import { SearchResult } from '../../types';

export async function searchRawgGames(query: string, year?: string, apiKey?: string): Promise<SearchResult[]> {
  const key = apiKey;
  if (!key) return [];

  try {
    const url = `https://api.rawg.io/api/games?key=${key}&search=${encodeURIComponent(query)}&page_size=6`;
    const res = await fetch(url, {
      signal: AbortSignal.timeout(1800),
      headers: { 'User-Agent': 'ListsEngine/2.0 (media-aggregator)' },
    });
    if (!res.ok) return [];

    const data = await res.json() as {
      results?: Array<{
        id: number;
        name: string;
        released?: string;
        background_image?: string;
        rating?: number;
        metacritic?: number;
        platforms?: Array<{ platform: { name: string } }>;
      }>;
    };

    if (!data.results || !Array.isArray(data.results)) return [];

    return data.results.map(item => {
      const releaseYear = item.released ? item.released.slice(0, 4) : (year || '');
      const rating = item.metacritic ? Math.round(item.metacritic / 10 * 10) / 10 : (item.rating ? Math.round(item.rating * 2 * 10) / 10 : 0);
      const platforms = item.platforms?.map(p => p.platform.name).slice(0, 3).join(', ') || '';

      return {
        title: item.name,
        creator: platforms,
        releaseYear,
        releaseDate: item.released || releaseYear,
        coverUrl: item.background_image || '',
        overview: '',
        rating,
        totalEpisodes: 0,
        totalPages: 0,
        category: 'GAME' as const,
        externalUrl: `https://rawg.io/games/${item.id}`,
      };
    });
  } catch {
    return [];
  }
}
