import { SearchResult } from '../../types';

export async function searchWikidataBoardGames(query: string): Promise<SearchResult[]> {
  const results: SearchResult[] = [];
  try {
    const term = encodeURIComponent(query.trim());
    const url = `https://www.wikidata.org/w/api.php?action=wbsearchentities&search=${term}&language=en&type=item&limit=15&format=json`;

    const res = await fetch(url, {
      headers: { 'User-Agent': 'ListsSearchEngine/1.0 (contact@listsapp.com)' },
      signal: AbortSignal.timeout(2200)
    });

    if (res.ok) {
      const data = await res.json() as any;
      const searchArr = data.search || [];
      for (const item of searchArr) {
        const label = item.label;
        if (!label) continue;

        const desc = (item.description || '').toLowerCase();
        if (
          desc.includes('board game') ||
          desc.includes('tabletop') ||
          desc.includes('card game') ||
          desc.includes('roll-and-move') ||
          desc.includes('family game')
        ) {
          const yearMatch = (item.description || '').match(/\b(19\d{2}|20\d{2})\b/);
          const year = yearMatch ? yearMatch[0] : '';

          results.push({
            title: label,
            creator: '',
            releaseYear: year,
            releaseDate: year,
            coverUrl: '',
            overview: item.description || '',
            rating: 0,
            totalEpisodes: 0,
            totalPages: 0,
            category: 'BOARD_GAME',
            externalUrl: item.concepturi || ''
          });
        }
      }
    }
  } catch (_) {}
  return results;
}

