import { SearchResult } from '../../types';

export async function searchImdbMovies(query: string, year?: string): Promise<SearchResult[]> {
  const results: SearchResult[] = [];
  try {
    const qSanitized = query.trim().toLowerCase().replace(/[^a-z0-9]+/g, '_');
    if (!qSanitized) return [];

    const url = `https://v3.sg.media-imdb.com/suggestion/x/${qSanitized}.json`;
    const res = await fetch(url, {
      headers: { 'User-Agent': 'Mozilla/5.0' },
      signal: AbortSignal.timeout(2200)
    });

    if (res.ok) {
      const data = await res.json() as any;
      const d = data.d || [];
      for (const item of d) {
        const qType = (item.q || '').toLowerCase();
        if (qType === 'video game' || qType === 'podcast') continue;

        const title = item.l;
        if (!title) continue;

        const yr = item.y ? String(item.y) : '';
        if (year && yr && yr !== year) continue;

        const rawImg = item.i?.imageUrl || '';
        const cover = rawImg ? rawImg.replace(/\._V1_.*\.jpg/, '._V1_SX1200.jpg') : '';
        const id = item.id || '';

        results.push({
          title,
          creator: item.s || '',
          releaseYear: yr,
          releaseDate: yr,
          coverUrl: cover,
          overview: '',
          rating: 0,
          totalEpisodes: 0,
          totalPages: 0,
          category: 'MOVIE',
          externalUrl: id ? `https://www.imdb.com/title/${id}` : ''
        });
      }
    }
  } catch (_) {}
  return results;
}

