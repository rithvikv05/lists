import { SearchResult } from '../../types';

export async function searchJustWatchMovie(query: string, year?: string): Promise<SearchResult[]> {
  try {
    const payload = {
      query,
      page: 1,
      page_size: 5,
      content_types: ['movie'],
    };

    const res = await fetch('https://apis.justwatch.com/content/titles/en_US/popular', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'User-Agent': 'ListsEngine/2.0 (media-aggregator)',
      },
      body: JSON.stringify(payload),
      signal: AbortSignal.timeout(1800),
    });

    if (!res.ok) return [];

    const data = await res.json() as { items?: Array<{
      title: string;
      original_release_year?: number;
      poster?: string;
      short_description?: string;
      scoring?: Array<{ provider_type: string; value: number }>;
      id: number;
      full_path?: string;
    }> };

    if (!data.items || !Array.isArray(data.items)) return [];

    return data.items.map(item => {
      let rating = 0;
      const imdbScore = item.scoring?.find(s => s.provider_type === 'imdb:score');
      if (imdbScore) {
        rating = imdbScore.value;
      }

      const posterPath = item.poster ? `https://images.justwatch.com${item.poster.replace('{profile}', 's718')}` : '';

      return {
        title: item.title,
        creator: '',
        releaseYear: item.original_release_year ? String(item.original_release_year) : (year || ''),
        releaseDate: item.original_release_year ? String(item.original_release_year) : '',
        coverUrl: posterPath,
        overview: item.short_description || '',
        rating,
        totalEpisodes: 0,
        totalPages: 0,
        category: 'MOVIE' as const,
        externalUrl: item.full_path ? `https://www.justwatch.com${item.full_path}` : `https://www.justwatch.com/us/movie/${item.id}`,
      };
    });
  } catch {
    return [];
  }
}

