import { SearchResult } from '../../types';

export async function searchOmdbMovies(
  query: string,
  apiKey: string = 'trilogy',
  year?: string
): Promise<SearchResult[]> {
  const results: SearchResult[] = [];
  try {
    const term = encodeURIComponent(query);
    const yearParam = year ? `&y=${encodeURIComponent(year)}` : '';
    const url = `https://www.omdbapi.com/?s=${term}${yearParam}&type=movie&apikey=${apiKey}`;

    const res = await fetch(url, {
      headers: { 'User-Agent': 'ListsSearchEngine/1.0' },
      signal: AbortSignal.timeout(2200)
    });

    if (res.ok) {
      const data = await res.json() as any;
      if (data.Response === 'True') {
        const searchArr = data.Search || [];
        for (const item of searchArr) {
          const title = item.Title;
          if (!title) continue;

          let poster = item.Poster || '';
          if (poster === 'N/A') poster = '';
          else if (poster) {
            poster = poster.replace(/_SX\d+\.jpg/, '_SX1200.jpg').replace(/_SY\d+\.jpg/, '_SY1200.jpg');
          }

          results.push({
            title,
            creator: '',
            releaseYear: item.Year || '',
            releaseDate: item.Year || '',
            coverUrl: poster,
            overview: '',
            rating: 0,
            totalEpisodes: 0,
            totalPages: 0,
            category: 'MOVIE',
            externalUrl: item.imdbID ? `https://www.imdb.com/title/${item.imdbID}` : ''
          });
        }
      }
    }
  } catch (_) {}
  return results;
}

