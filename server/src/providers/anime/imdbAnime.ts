import { SearchResult } from '../../types';

export async function searchImdbAnime(query: string, year?: string): Promise<SearchResult[]> {
  try {
    const clean = query.toLowerCase().replace(/[^a-z0-9]/g, '');
    if (!clean) return [];
    const prefix = clean.slice(0, 1);
    const url = `https://v3.sg.media-imdb.com/suggestion/x/${prefix}/${encodeURIComponent(clean)}.json`;

    const res = await fetch(url, {
      signal: AbortSignal.timeout(1800),
      headers: { 'User-Agent': 'ListsEngine/2.0 (media-aggregator)' },
    });
    if (!res.ok) return [];

    const data = await res.json() as { d?: Array<{
      l: string;
      s?: string;
      y?: number;
      yr?: string;
      i?: { imageUrl: string };
      id: string;
      q?: string;
    }> };

    if (!data.d) return [];

    return data.d
      .filter(item => {
        const s = (item.s || '').toLowerCase();
        const l = item.l.toLowerCase();
        return s.includes('anime') || s.includes('animation') || s.includes('voice') || item.q === 'TV series' || item.q === 'feature';
      })
      .slice(0, 6)
      .map(item => {
        let releaseYear = item.y ? String(item.y) : (year || '');
        if (item.yr) {
          const yrMatch = item.yr.match(/^(\d{4})/);
          if (yrMatch) releaseYear = yrMatch[1];
        }

        return {
          title: item.l,
          creator: item.s || '',
          releaseYear,
          releaseDate: releaseYear,
          coverUrl: item.i?.imageUrl || '',
          overview: '',
          rating: 0,
          totalEpisodes: 0,
          totalPages: 0,
          category: 'ANIME' as const,
          externalUrl: `https://www.imdb.com/title/${item.id}/`,
        };
      });
  } catch {
    return [];
  }
}
