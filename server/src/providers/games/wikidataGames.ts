import { SearchResult } from '../../types';

export async function searchWikidataGames(query: string, year?: string): Promise<SearchResult[]> {
  try {
    const url = `https://www.wikidata.org/w/api.php?action=wbsearchentities&search=${encodeURIComponent(query)}&language=en&format=json&type=item&limit=6&origin=*`;
    const res = await fetch(url, {
      signal: AbortSignal.timeout(1800),
      headers: { 'User-Agent': 'ListsEngine/2.0 (media-aggregator)' },
    });
    if (!res.ok) return [];

    const data = await res.json() as {
      search?: Array<{
        id: string;
        label: string;
        description?: string;
        concepturi?: string;
      }>;
    };

    if (!data.search || !Array.isArray(data.search)) return [];

    return data.search
      .filter(item => {
        const desc = (item.description || '').toLowerCase();
        return desc.includes('video game') || desc.includes('game') || desc.includes('rpg');
      })
      .map(item => {
        const yearMatch = item.description?.match(/\b(19\d{2}|20\d{2})\b/);
        const releaseYear = yearMatch ? yearMatch[0] : (year || '');

        return {
          title: item.label,
          creator: '',
          releaseYear,
          releaseDate: releaseYear,
          coverUrl: '',
          overview: item.description || '',
          rating: 0,
          totalEpisodes: 0,
          totalPages: 0,
          category: 'GAME' as const,
          externalUrl: item.concepturi || `https://www.wikidata.org/wiki/${item.id}`,
        };
      });
  } catch {
    return [];
  }
}
