import { SearchResult } from '../../types';

export async function searchWikipediaGames(query: string, year?: string): Promise<SearchResult[]> {
  try {
    const searchQuery = year ? `${query} ${year} video game` : `${query} video game`;
    const searchUrl = `https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=${encodeURIComponent(searchQuery)}&utf8=&format=json&origin=*`;
    
    const searchRes = await fetch(searchUrl, {
      signal: AbortSignal.timeout(1800),
      headers: { 'User-Agent': 'ListsEngine/2.0 (media-aggregator)' },
    });
    if (!searchRes.ok) return [];

    const searchData = await searchRes.json() as { query?: { search?: Array<{ pageid: number; title: string; snippet: string }> } };
    const items = searchData.query?.search;
    if (!items || items.length === 0) return [];

    const topTitles = items.slice(0, 5).map(i => i.title);
    const detailsUrl = `https://en.wikipedia.org/w/api.php?action=query&titles=${encodeURIComponent(topTitles.join('|'))}&prop=pageimages|extracts|info&inprop=url&pithumbsize=1200&exintro=1&explaintext=1&exchars=240&format=json&origin=*`;

    const detailsRes = await fetch(detailsUrl, {
      signal: AbortSignal.timeout(1800),
      headers: { 'User-Agent': 'ListsEngine/2.0 (media-aggregator)' },
    });
    if (!detailsRes.ok) return [];

    const detailsData = await detailsRes.json() as { query?: { pages?: Record<string, {
      pageid: number;
      title: string;
      extract?: string;
      thumbnail?: { source: string };
      fullurl?: string;
    }> } };

    const pages = Object.values(detailsData.query?.pages || {});
    return pages
      .filter(p => p.title && !p.title.includes('disambiguation'))
      .map(p => {
        const cleanTitle = p.title.replace(/\s*\([^)]*video game[^)]*\)/i, '').trim();
        const yearMatch = p.title.match(/\b(19\d{2}|20\d{2})\b/) || p.extract?.match(/\b(19\d{2}|20\d{2})\b/);
        const extractedYear = yearMatch ? yearMatch[1] : (year || '');

        return {
          title: cleanTitle,
          creator: '',
          releaseYear: extractedYear,
          releaseDate: extractedYear,
          coverUrl: p.thumbnail?.source || '',
          overview: p.extract || '',
          rating: 0,
          totalEpisodes: 0,
          totalPages: 0,
          category: 'GAME' as const,
          externalUrl: p.fullurl || `https://en.wikipedia.org/?curid=${p.pageid}`,
        };
      });
  } catch {
    return [];
  }
}

