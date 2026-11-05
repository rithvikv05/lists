import { SearchResult } from '../../types';

export async function searchAppleBooks(
  query: string,
  lang?: string,
  category: 'BOOK' | 'COMIC' = 'BOOK'
): Promise<SearchResult[]> {
  const results: SearchResult[] = [];
  try {
    const term = encodeURIComponent(query);
    const country = lang && ['hi', 'ta', 'te', 'ml', 'kn', 'bn'].includes(lang.toLowerCase()) ? 'in' : 'us';
    const url = `https://itunes.apple.com/search?term=${term}&entity=ebook&limit=25&country=${country}`;

    const res = await fetch(url, {
      headers: { 'User-Agent': 'ListsSearchEngine/1.0' },
      signal: AbortSignal.timeout(2200)
    });

    if (res.ok) {
      const data = await res.json() as any;
      const items = data.results || [];
      for (const item of items) {
        const title = item.trackName || item.trackCensoredName;
        if (!title) continue;

        const releaseDate = item.releaseDate || '';
        const releaseYear = releaseDate.slice(0, 4);
        const artwork = (item.artworkUrl100 || '').replace(/100x100bb\./g, '1200x1200bb.');
        const rating = item.averageUserRating ? item.averageUserRating * 2 : 0;
        const overview = (item.description || '').replace(/<[^>]*>/g, '');

        results.push({
          title,
          creator: item.artistName || '',
          releaseYear,
          releaseDate: releaseDate.slice(0, 10),
          coverUrl: artwork,
          overview,
          rating,
          totalEpisodes: 0,
          totalPages: 0,
          category,
          externalUrl: item.trackViewUrl || ''
        });
      }
    }
  } catch (_) {}
  return results;
}

