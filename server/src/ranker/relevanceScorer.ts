import { SearchResult } from '../types';
import { levenshteinDistance, tokenJaccardSimilarity } from './fuzzyMatcher';
import { stripLeadingArticles } from '../normalizer/queryNormalizer';

export function calculateRelevance(
  query: string,
  cleanQuery: string,
  extractedYear: string | undefined,
  item: SearchResult
): number {
  const titleLower = item.title.toLowerCase().trim();
  const cleanQLower = cleanQuery.toLowerCase().trim();
  const origQLower = query.toLowerCase().trim();

  let score = 0;

  // Exact match
  if (titleLower === cleanQLower || titleLower === origQLower) {
    score += 100;
  } else if (titleLower.startsWith(cleanQLower) || titleLower.startsWith(origQLower)) {
    score += 65;
  } else if (titleLower.includes(cleanQLower) || titleLower.includes(origQLower)) {
    score += 40;
  }

  // Article stripped match
  const strippedTitle = stripLeadingArticles(titleLower);
  const strippedCleanQ = stripLeadingArticles(cleanQLower);
  if (strippedTitle === strippedCleanQ) {
    score += 85;
  } else if (strippedTitle.startsWith(strippedCleanQ)) {
    score += 55;
  }

  // Word token Jaccard similarity
  const jaccard = tokenJaccardSimilarity(titleLower, cleanQLower);
  score += jaccard * 45;

  // Typo tolerance via Levenshtein edit distance
  const maxLen = Math.max(titleLower.length, cleanQLower.length);
  if (maxLen > 3 && Math.abs(titleLower.length - cleanQLower.length) <= 3) {
    const dist = levenshteinDistance(titleLower.slice(0, cleanQLower.length), cleanQLower);
    if (dist === 1) score += 28;
    else if (dist === 2) score += 14;
  }

  // Creator match
  if (item.creator) {
    const creatorLower = item.creator.toLowerCase();
    if (creatorLower.includes(cleanQLower) || creatorLower.includes(origQLower)) {
      score += 35;
    }
  }

  // Year alignment bonus
  if (extractedYear) {
    if (item.releaseYear.includes(extractedYear) || item.releaseDate.includes(extractedYear)) {
      score += 40;
    }
  }

  // Quality bonuses
  if (item.coverUrl) score += 12;
  if (item.overview) score += 8;
  if (item.rating > 0) score += (item.rating / 10) * 6;

  return score;
}

