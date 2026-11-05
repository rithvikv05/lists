import { SearchResult } from '../../types';

export async function searchCataloggdComicDb(query: string): Promise<SearchResult[]> {
  const results: SearchResult[] = [];
  try {
    const term = encodeURIComponent(query.trim());
    const url = `https://cataloggd.com/api/comicdb/series?q=${term}`;

    const res = await fetch(url, {
      headers: { 'User-Agent': 'Mozilla/5.0' },
      signal: AbortSignal.timeout(2200)
    });

    if (res.ok) {
      const data = await res.json() as any;
      const series = data.series || data.results || [];
      for (const item of series) {
        const title = item.name || item.title;
        if (!title) continue;

        const year = item.year_began ? String(item.year_began) : item.year ? String(item.year) : '';
        const publisher = item.publisher || '';
        const issueCount = item.issue_count || 0;

        results.push({
          title,
          creator: publisher,
          releaseYear: year,
          releaseDate: year,
          coverUrl: item.cover_url || '',
          overview: issueCount > 0 ? `${issueCount} issues published by ${publisher}` : publisher,
          rating: 0,
          totalEpisodes: 0,
          totalPages: issueCount,
          category: 'COMIC',
          externalUrl: item.id ? `https://www.comics.org/series/${item.id}` : ''
        });
      }
    }
  } catch (_) {}
  return results;
}

