import { SearchResult } from '../../types';

export async function searchBgg(query: string): Promise<SearchResult[]> {
  const results: SearchResult[] = [];
  try {
    const term = encodeURIComponent(query.trim());
    const url = `https://boardgamegeek.com/xmlapi2/search?query=${term}&type=boardgame`;

    const res = await fetch(url, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36'
      },
      signal: AbortSignal.timeout(2000)
    });

    if (res.ok) {
      const text = await res.text();
      const itemRegex = /<item type="boardgame" id="(\d+)">[\s\S]*?<name [^>]*value="([^"]+)"[\s\S]*?(?:<yearpublished value="(\d+)")?/g;
      let match;
      while ((match = itemRegex.exec(text)) !== null) {
        const id = match[1];
        const name = match[2];
        const year = match[3] || '';

        results.push({
          title: name,
          creator: '',
          releaseYear: year,
          releaseDate: year,
          coverUrl: '',
          overview: '',
          rating: 0,
          totalEpisodes: 0,
          totalPages: 0,
          category: 'BOARD_GAME',
          externalUrl: `https://boardgamegeek.com/boardgame/${id}`
        });
      }
    }
  } catch (_) {}
  return results;
}

