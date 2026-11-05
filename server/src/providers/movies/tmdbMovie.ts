import { SearchResult } from '../../types';

export async function searchTmdbMovie(query: string, year?: string, apiKey?: string): Promise<SearchResult[]> {
  const token = apiKey;
  if (!token) return [];

  try {
    const isBearer = token.length > 40;
    const url = new URL('https://api.themoviedb.org/3/search/movie');
    url.searchParams.set('query', query);
    url.searchParams.set('include_adult', 'false');
    if (year) url.searchParams.set('year', year);

    const headers: Record<string, string> = { 'Accept': 'application/json' };
    if (isBearer) {
      headers['Authorization'] = `Bearer ${token}`;
    } else {
      url.searchParams.set('api_key', token);
    }

    const res = await fetch(url.toString(), {
      headers,
      signal: AbortSignal.timeout(1800),
    });

    if (!res.ok) return [];
    const data = await res.json() as { results?: Array<{
      title: string;
      original_title?: string;
      release_date?: string;
      poster_path?: string;
      overview?: string;
      vote_average?: number;
      id: number;
    }> };

    if (!data.results || !Array.isArray(data.results)) return [];

    return data.results.slice(0, 8).map(m => {
      const releaseYear = m.release_date?.slice(0, 4) || '';
      const coverUrl = m.poster_path ? `https://image.tmdb.org/t/p/w780${m.poster_path}` : '';
      return {
        title: m.title || m.original_title || '',
        creator: '',
        releaseYear,
        releaseDate: m.release_date || '',
        coverUrl,
        overview: m.overview || '',
        rating: m.vote_average ? Math.round(m.vote_average * 10) / 10 : 0,
        totalEpisodes: 0,
        totalPages: 0,
        category: 'MOVIE' as const,
        externalUrl: `https://www.themoviedb.org/movie/${m.id}`,
      };
    });
  } catch {
    return [];
  }
}
