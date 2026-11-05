import { SearchResult } from '../../types';

export async function searchComicVine(query: string, year?: string, apiKey?: string): Promise<SearchResult[]> {
  const key = apiKey;
  if (!key) return [];

  try {
    const url = `https://comicvine.gamespot.com/api/search/?api_key=${key}&format=json&resources=volume,issue&query=${encodeURIComponent(query)}&limit=6`;
    const res = await fetch(url, {
      signal: AbortSignal.timeout(1800),
      headers: { 'User-Agent': 'ListsEngine/2.0 (media-aggregator)' },
    });
    if (!res.ok) return [];

    const data = await res.json() as {
      results?: Array<{
        name?: string;
        volume?: { name?: string };
        start_year?: string;
        image?: { super_url?: string; medium_url?: string };
        deck?: string;
        publisher?: { name?: string };
        site_detail_url?: string;
      }>;
    };

    if (!data.results || !Array.isArray(data.results)) return [];

    return data.results.map(item => {
      const title = item.name || item.volume?.name || '';
      const releaseYear = item.start_year || (year || '');
      const coverUrl = item.image?.super_url || item.image?.medium_url || '';
      const publisher = item.publisher?.name || '';

      return {
        title,
        creator: publisher,
        releaseYear,
        releaseDate: releaseYear,
        coverUrl,
        overview: item.deck || '',
        rating: 0,
        totalEpisodes: 0,
        totalPages: 0,
        category: 'COMIC' as const,
        externalUrl: item.site_detail_url || '',
      };
    });
  } catch {
    return [];
  }
}
