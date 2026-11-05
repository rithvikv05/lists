import { SearchResult, Category } from '../../types';

export async function searchWikipedia(
  query: string,
  category: Category
): Promise<SearchResult[]> {
  const results: SearchResult[] = [];
  try {
    const suffix = category === 'BOARD_GAME' ? ' board game' :
      category === 'GAME' ? ' video game' :
      category === 'MOVIE' ? ' film' :
      category === 'TV_SHOW' ? ' series' :
      category === 'ANIME' ? ' anime' :
      category === 'BOOK' ? ' novel' :
      category === 'MANGA' ? ' manga' :
      category === 'COMIC' ? ' comic' :
      category === 'PODCAST' ? ' podcast' : ' album';

    const cleanQ = query.toLowerCase().endsWith(suffix.trim()) ? query : `${query}${suffix}`;
    const term = encodeURIComponent(cleanQ);
    const url = `https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=${term}&gsrlimit=15&prop=pageimages|extracts|info&inprop=url&piprop=thumbnail&pithumbsize=600&exintro=1&explaintext=1&format=json`;

    const res = await fetch(url, {
      headers: { 'User-Agent': 'ListsSearchEngine/1.0 (contact@listsapp.com)' },
      signal: AbortSignal.timeout(2200)
    });

    if (res.ok) {
      const data = await res.json() as any;
      const pages = data.query?.pages || {};
      for (const key of Object.keys(pages)) {
        const page = pages[key];
        const rawTitle = page.title || '';
        if (
          rawTitle.startsWith('List of') ||
          rawTitle.startsWith('Lists of') ||
          rawTitle.includes('(disambiguation)')
        ) {
          continue;
        }

        const cleanTitle = rawTitle.replace(/\s*\([^)]*\)/g, '').trim();
        const extract = page.extract || '';
        const thumb = page.thumbnail?.source || '';
        const fullUrl = page.fullurl || '';
        const yearMatch = extract.match(/\b(19\d{2}|20\d{2})\b/);
        const year = yearMatch ? yearMatch[0] : '';

        results.push({
          title: cleanTitle,
          creator: '',
          releaseYear: year,
          releaseDate: year,
          coverUrl: thumb,
          overview: extract,
          rating: 0,
          totalEpisodes: 0,
          totalPages: 0,
          category,
          externalUrl: fullUrl
        });
      }
    }
  } catch (_) {}
  return results;
}

export async function searchWikipediaBoardGames(query: string): Promise<SearchResult[]> {
  return searchWikipedia(query, 'BOARD_GAME');
}


