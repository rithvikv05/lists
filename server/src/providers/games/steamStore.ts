import { SearchResult } from '../../types';

export async function searchSteamStore(query: string): Promise<SearchResult[]> {
  const results: SearchResult[] = [];
  try {
    const term = encodeURIComponent(query.trim());
    const url = `https://store.steampowered.com/api/storesearch/?term=${term}&l=english&cc=US`;

    const res = await fetch(url, {
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

        let img = item.tiny_image || '';
        // Upgrade steam thumbnail to full header
        if (img) {
          img = img.replace('/capsule_sm_120.jpg', '/header.jpg').replace('/capsule_184x69.jpg', '/header.jpg');
        }

        results.push({
          title: item.name,
          creator: item.price ? `$${(item.price.final / 100).toFixed(2)}` : '',
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

