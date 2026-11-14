import { providerFetch } from '../http';
import { extraProductTitle } from '../../search/gamePolicy';
import { SearchResult } from '../../types';

export async function searchSteamStore(query: string): Promise<SearchResult[]> {
  const results: SearchResult[] = [];
  try {
    const term = encodeURIComponent(query.trim());
    const url = `https://store.steampowered.com/api/storesearch/?term=${term}&l=english&cc=US`;

    const res = await providerFetch(url, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)',
        'Accept': 'application/json'
      },
      signal: AbortSignal.timeout(2200)
    });

    if (res.ok) {
      const data = await res.json() as any;
      const items = data.items || [];
      for (const item of items) {
        if (!item.name) continue;

        if (!/^\d+$/.test(String(item.id)) || (item.type && item.type !== 'app')) continue;
        // Keep the supplied URL. Rewriting a capsule URL to an invented header path breaks artwork.
        const img = typeof item.tiny_image === 'string' ? item.tiny_image : '';

        results.push({
          title: item.name,
          entityKind: extraProductTitle(item.name) ? 'game_extra' : undefined,
          creator: '',
          sourceRefs: [{ source: 'steam', id: String(item.id), url: `https://store.steampowered.com/app/${item.id}` }],
          releaseYear: '',
          releaseDate: '',
          coverUrl: img,
          overview: '',
          rating: 0,
          totalEpisodes: 0,
          totalPages: 0,
          category: 'GAME',
          externalUrl: item.id ? `https://store.steampowered.com/app/${item.id}` : ''
        });
      }
    }
  } catch (_) {}
  return results;
}

