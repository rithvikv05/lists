import { SearchResult } from '../../types';

export async function searchGetComics(query: string): Promise<SearchResult[]> {
  const results: SearchResult[] = [];
  try {
    const term = encodeURIComponent(query.trim());
    const url = `https://getcomics.org/wp-json/wp/v2/posts?search=${term}&_embed=1&per_page=15`;

    const res = await fetch(url, {
      headers: { 'User-Agent': 'ListsSearchEngine/1.0 (Mozilla/5.0)' },
      signal: AbortSignal.timeout(2200)
    });

    if (res.ok) {
      const posts = await res.json() as any[];
      for (const item of posts) {
        const rawTitle = item.title?.rendered || '';
        const title = rawTitle.replace(/<[^>]*>/g, '').trim();
        if (!title) continue;

        let cover = item.jetpack_featured_media_url || '';
        if (!cover) {
          cover = item.yoast_head_json?.og_image?.[0]?.url || '';
        }
        if (!cover) {
          cover = item._embedded?.['wp:featuredmedia']?.[0]?.source_url || '';
        }
        if (cover.startsWith('http://')) {
          cover = cover.replace('http://', 'https://');
        }

        const dateStr = item.date || '';
        const dateYear = dateStr.slice(0, 4);
        const titleYearMatch = title.match(/\b(19\d{2}|20\d{2})\b/);
        const year = titleYearMatch ? titleYearMatch[0] : dateYear;

        const excerpt = (item.excerpt?.rendered || '').replace(/<[^>]*>/g, '');
        const yoastDesc = item.yoast_head_json?.og_description || '';

        results.push({
          title,
          creator: '',
          releaseYear: year,
          releaseDate: dateStr.slice(0, 10),
          coverUrl: cover,
          overview: yoastDesc || excerpt,
          rating: 0,
          totalEpisodes: 0,
          totalPages: 0,
          category: 'COMIC',
          externalUrl: item.link || ''
        });
      }
    }
  } catch (_) {}
  return results;
}

