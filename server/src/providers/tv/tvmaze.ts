import { SearchResult } from '../../types';

export async function searchTvMaze(query: string, year?: string): Promise<SearchResult[]> {
  const results: SearchResult[] = [];
  try {
    const term = encodeURIComponent(query);
    const url = `https://api.tvmaze.com/search/shows?q=${term}`;

    const res = await fetch(url, {
      headers: { 'User-Agent': 'ListsSearchEngine/1.0' },
      signal: AbortSignal.timeout(2200)
    });

    if (res.ok) {
      const array = await res.json() as any[];
      for (const elem of array) {
        const show = elem.show;
        if (!show || !show.name) continue;

        const premiered = show.premiered || '';
        const releaseYear = premiered.slice(0, 4);
        if (year && releaseYear && releaseYear !== year) continue;

        const image = show.image;
        const cover = image?.original || image?.medium || '';
        const network = show.network?.name || show.webChannel?.name || '';
        const overview = (show.summary || '').replace(/<[^>]*>/g, '');

        results.push({
          title: show.name,
          creator: network,
          releaseYear,
          releaseDate: premiered,
          coverUrl: cover,
          overview,
          rating: show.rating?.average ? Number(show.rating.average) : 0,
          totalEpisodes: 0,
          totalPages: 0,
          category: 'TV_SHOW',
          externalUrl: show.url || ''
        });
      }
    }
  } catch (_) {}
  return results;
}

