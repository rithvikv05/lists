import { providerFetch } from '../http';
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
    const url = `https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=${term}&gsrlimit=15&prop=pageimages|extracts|info|pageprops&inprop=url&piprop=thumbnail&pithumbsize=600&exintro=1&explaintext=1&format=json`;

    const res = await providerFetch(url, {
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
          rawTitle.includes('(disambiguation)') ||
          page.pageprops?.disambiguation !== undefined
        ) {
          continue;
        }

        const extract = page.extract || '';
        const introduction = extract.slice(0, 350);
        const types: Record<Category, RegExp> = {
          GAME: /\b(?:video|computer|arcade|platform|adventure|role-playing) game\b/i,
          BOARD_GAME: /\b(?:board|tabletop|card) game\b/i,
          MOVIE: /\bfilm\b/i, TV_SHOW: /\b(?:television|tv|web|animated).{0,35}series\b/i,
          ANIME: /\b(?:anime|animated (?:film|series))\b/i,
          BOOK: /\b(?:novel|book)\b/i, MANGA: /\bmanga\b/i, COMIC: /\bcomic/i,
          PODCAST: /\bpodcast\b/i, MUSIC: /\b(?:album|song|single|musical composition)\b/i,
        };
        // Classify what the subject IS, not a game/book merely mentioned later
        // in an author's, character's or adaptation's introduction.
        const definition = introduction.split(/\b(?:is|was|are|were)\s+/i)[1] || introduction;
        const descriptor = definition.split(/\b(?:based on|by|in|for|about|featuring|developed|written|directed)\b/i)[0];
        if (/\bfranchise\b/i.test(descriptor)) continue;
        if (category === 'BOOK') {
          const work = descriptor.search(/\b(?:novel|book)\b/i);
          const group = descriptor.search(/\b(?:series|sequence|trilogy|trilogies|bibliography)\b/i);
          if (group >= 0 && (work < 0 || group < work)) continue;
        }
        const expansion = category === 'GAME' && /\b(?:expansion pack|downloadable content|dlc)\b/i.test(descriptor);
        if (!types[category].test(descriptor) && !expansion) continue;
        if (category === 'GAME' && (/\(series\)/i.test(rawTitle) || /\b(?:series of|game series|franchise|handheld|hardware|accessory)\b/i.test(introduction))) continue;
        // Retain numbered and other identity-bearing disambiguators.
        const cleanTitle = rawTitle.replace(/\s*\((?:film|movie|tv series|video game|board game|anime|manga|novel|comic)\)\s*$/i, '').trim();
        const thumb = page.thumbnail?.source || '';
        const fullUrl = page.fullurl || '';
        const yearMatch = extract.match(/\b(19\d{2}|20\d{2})\b/);
        const year = yearMatch ? yearMatch[0] : '';

        results.push({
          title: cleanTitle,
          ...(category === 'GAME' ? { entityKind: expansion ? 'game_expansion' : 'game' } : {}),
          sourceRefs: [{ source: 'wikipedia', id: String(page.pageid || key), url: fullUrl },
            ...(/^Q\d+$/.test(page.pageprops?.wikibase_item || '') ? [{ source: 'wikidata', id: page.pageprops.wikibase_item, url: `https://www.wikidata.org/wiki/${page.pageprops.wikibase_item}` }] : [])],
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


