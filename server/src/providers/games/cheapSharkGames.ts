import { SearchResult } from '../../types';

export async function searchCheapSharkGames(query: string, year?: string): Promise<SearchResult[]> {
  try {
    const url = `https://www.cheapshark.com/api/1.0/games?title=${encodeURIComponent(query)}&limit=6`;
    const res = await fetch(url, {
      signal: AbortSignal.timeout(1800),
      headers: { 'User-Agent': 'ListsEngine/2.0 (media-aggregator)' },
    });
    if (!res.ok) return [];

    const data = await res.json() as Array<{
      gameID: string;
      steamAppID?: string;
      cheapest: string;
      cheapestDealID: string;
      external: string;
      thumb: string;
    }>;

    if (!Array.isArray(data)) return [];

    return data.map(item => {
      let cover = item.thumb;
      // If steamAppID is present, generate high-res Steam header
      if (item.steamAppID && item.steamAppID !== '0') {
        cover = `https://cdn.akamai.steamstatic.com/steam/apps/${item.steamAppID}/header.jpg`;
      }

      return {
        title: item.external,
        creator: '',
        releaseYear: year || '',
        releaseDate: year || '',
        coverUrl: cover,
        overview: item.cheapest ? `Retail price from $${item.cheapest}` : '',
        rating: 0,
        totalEpisodes: 0,
        totalPages: 0,
        category: 'GAME' as const,
        externalUrl: `https://www.cheapshark.com/redirect?dealID=${item.cheapestDealID}`,
      };
    });
  } catch {
    return [];
  }
}

