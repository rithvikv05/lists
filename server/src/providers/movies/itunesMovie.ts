import { SearchResult } from '../../types';

export async function searchITunesMovies(
  query: string,
  year?: string,
  lang?: string
): Promise<SearchResult[]> {
  const results: SearchResult[] = [];
  try {
    const term = encodeURIComponent(query);
    const country = lang && ['hi', 'ta', 'te', 'ml', 'kn', 'bn'].includes(lang.toLowerCase()) ? 'in' : 'us';
    const url = `https://itunes.apple.com/search?term=${term}&media=movie&limit=25&country=${country}`;

    const res = await fetch(url, {
      headers: { 'User-Agent': 'ListsSearchEngine/1.0' },
      signal: AbortSignal.timeout(2200)
    });

    if (res.ok) {
      const data = await res.json() as any;
      const items = data.results || [];
      for (const item of items) {
        const title = item.trackName || item.collectionName;
        if (!title) continue;

        const releaseDate = item.releaseDate || '';
        const releaseYear = releaseDate.slice(0, 4);
        if (year && releaseYear && releaseYear !== year) continue;

        const artwork = (item.artworkUrl100 || '')
          .replace('100x100bb.', '1200x1200bb.');

        results.push({
          title,
          creator: item.artistName || '',
          releaseYear,
          releaseDate: releaseDate.slice(0, 10),
          coverUrl: artwork,
          overview: (item.longDescription || item.shortDescription || '').replace(/<[^>]*>/g, ''),
          rating: 0,
          totalEpisodes: 0,
          totalPages: 0,
          category: 'MOVIE',
          externalUrl: item.trackViewUrl || ''
        });
      }
    }
  } catch (_) {}
  return results;
}

