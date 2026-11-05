import { SearchResult } from '../../types';

export async function searchOmdbTv(query: string, year?: string, apiKey?: string): Promise<SearchResult[]> {
  const key = apiKey;
  if (!key) return [];

  try {
    let url = `https://www.omdbapi.com/?apikey=${key}&s=${encodeURIComponent(query)}&type=series`;
    if (year) url += `&y=${year}`;

    const res = await fetch(url, {
      signal: AbortSignal.timeout(1800),
      headers: { 'User-Agent': 'ListsEngine/2.0 (media-aggregator)' },
    });
    if (!res.ok) return [];

    const data = await res.json() as {
      Search?: Array<{
        Title: string;
        Year: string;
        imdbID: string;
        Type: string;
        Poster: string;
      }>;
      Response?: string;
    };

    if (data.Response !== 'True' || !data.Search) return [];

    return data.Search.map(item => {
      const yearMatch = item.Year.match(/^(\d{4})/);
      const releaseYear = yearMatch ? yearMatch[1] : (year || '');
      const coverUrl = item.Poster && item.Poster !== 'N/A' ? item.Poster : '';

      return {
        title: item.Title,
        creator: '',
        releaseYear,
        releaseDate: releaseYear,
        coverUrl,
        overview: '',
        rating: 0,
        totalEpisodes: 0,
        totalPages: 0,
        category: 'TV_SHOW' as const,
        externalUrl: `https://www.imdb.com/title/${item.imdbID}/`,
      };
    });
  } catch {
    return [];
  }
}
