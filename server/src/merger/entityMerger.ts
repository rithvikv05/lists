import { SearchResult } from '../types';
import { cleanCanonicalTitle, toCleanKey } from '../normalizer/canonicalTitle';
import { enhanceCoverUrl } from './imageUpscaler';
import { isValidSearchResult } from './validator';

export function deduplicateAndMerge(results: SearchResult[]): SearchResult[] {
  const mergedMap = new Map<string, SearchResult>();

  for (const item of results) {
    if (!isValidSearchResult(item)) continue;

    const canonical = cleanCanonicalTitle(item.title);
    const cleanKey = toCleanKey(canonical);
    if (!cleanKey) continue;

    const yearDigits = (item.releaseYear || '').replace(/\D/g, '').slice(0, 4);
    const key = yearDigits ? `${cleanKey}#${yearDigits}` : cleanKey;

    const enhancedItem: SearchResult = {
      ...item,
      title: canonical,
      coverUrl: enhanceCoverUrl(item.coverUrl)
    };

    const existing = mergedMap.get(key);
    if (!existing) {
      mergedMap.set(key, enhancedItem);
    } else {
      const betterCover = (!existing.coverUrl && enhancedItem.coverUrl)
        ? enhancedItem.coverUrl
        : (enhancedItem.coverUrl.length > existing.coverUrl.length && enhancedItem.coverUrl.includes('1200'))
          ? enhancedItem.coverUrl
          : existing.coverUrl;

      const betterOverview = (existing.overview || '').length < (enhancedItem.overview || '').length
        ? enhancedItem.overview
        : existing.overview;

      const betterYear = !existing.releaseYear && enhancedItem.releaseYear
        ? enhancedItem.releaseYear
        : existing.releaseYear;

      const betterCreator = !existing.creator && enhancedItem.creator
        ? enhancedItem.creator
        : existing.creator;

      const betterRating = existing.rating <= 0 && enhancedItem.rating > 0
        ? enhancedItem.rating
        : existing.rating;

      const betterEpisodes = existing.totalEpisodes <= 0 && enhancedItem.totalEpisodes > 0
        ? enhancedItem.totalEpisodes
        : existing.totalEpisodes;

      const betterPages = existing.totalPages <= 0 && enhancedItem.totalPages > 0
        ? enhancedItem.totalPages
        : existing.totalPages;

      const betterExtUrl = !existing.externalUrl && enhancedItem.externalUrl
        ? enhancedItem.externalUrl
        : existing.externalUrl;

      mergedMap.set(key, {
        ...existing,
        coverUrl: betterCover,
        overview: betterOverview,
        releaseYear: betterYear,
        creator: betterCreator,
        rating: betterRating,
        totalEpisodes: betterEpisodes,
        totalPages: betterPages,
        externalUrl: betterExtUrl
      });
    }
  }

  return Array.from(mergedMap.values());
}

