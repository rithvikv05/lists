import { BookMetadata } from '../types';
import { titleMatchScore, cleanQuery } from '../normalizer/query';
import { mapAttributes } from '../normalizer/attributes';

export function mergeBooks(a: BookMetadata, b: BookMetadata): BookMetadata {
  const isbn13 = a.isbn13 || b.isbn13;
  const isbn10 = a.isbn10 || b.isbn10;
  const primaryIsbn = isbn13 || isbn10 || a.primaryIsbn || b.primaryIsbn;

  // Prefer longer overview
  const overview = (a.overview && b.overview)
    ? (a.overview.length >= b.overview.length ? a.overview : b.overview)
    : (a.overview || b.overview);

  // Combine authors without duplicates
  const authorSet = new Set([...a.authors, ...b.authors]);
  const authors = Array.from(authorSet);

  // Combine categories and subjects
  const allCategories = Array.from(new Set([...a.rawCategories, ...b.rawCategories]));
  const allSubjects = Array.from(new Set([...a.rawSubjects, ...b.rawSubjects]));

  // Prefer defined page count
  const totalPages = a.totalPages || b.totalPages;

  // Re-map attributes across both sets of tags
  const { normalizedGenres, suggestedMoods, suggestedPacing, lengthBracket, recommendationVector } =
    mapAttributes(allCategories, allSubjects, totalPages);

  // Cover preference: high res over thumbnail
  const coverUrl = a.coverUrl || b.coverUrl;
  const thumbnailCoverUrl = a.thumbnailCoverUrl || b.thumbnailCoverUrl || coverUrl;

  return {
    id: primaryIsbn || a.id || b.id,
    title: a.title.length >= b.title.length ? a.title : b.title,
    subtitle: a.subtitle || b.subtitle,
    authors,
    primaryAuthor: authors[0] || a.primaryAuthor || b.primaryAuthor,
    isbn10,
    isbn13,
    primaryIsbn,
    hasValidIsbn: Boolean(primaryIsbn),
    publisher: a.publisher || b.publisher,
    publishedYear: a.publishedYear || b.publishedYear,
    publishedDate: a.publishedDate || b.publishedDate,
    totalPages,
    language: a.language || b.language,
    overview,
    coverUrl,
    thumbnailCoverUrl,
    rating: a.rating || b.rating,
    ratingsCount: (a.ratingsCount || 0) + (b.ratingsCount || 0) || undefined,
    externalUrl: a.externalUrl || b.externalUrl,
    source: 'COMBINED',
    rawCategories: allCategories,
    rawSubjects: allSubjects,
    normalizedGenres,
    suggestedMoods,
    suggestedPacing,
    lengthBracket,
    recommendationVector,
  };
}

export function deduplicateAndRank(items: BookMetadata[], query: string): BookMetadata[] {
  const mergedList: BookMetadata[] = [];

  for (const item of items) {
    let matchedIndex = -1;

    for (let i = 0; i < mergedList.length; i++) {
      const existing = mergedList[i];

      // Match 1: ISBN match
      if (
        (item.isbn13 && existing.isbn13 && item.isbn13 === existing.isbn13) ||
        (item.isbn10 && existing.isbn10 && item.isbn10 === existing.isbn10)
      ) {
        matchedIndex = i;
        break;
      }

      // Match 2: Fuzzy title and author similarity
      const tScore = titleMatchScore(item.title, existing.title);
      if (tScore >= 0.85) {
        const authorA = cleanQuery(item.primaryAuthor.toLowerCase());
        const authorB = cleanQuery(existing.primaryAuthor.toLowerCase());
        if (
          !authorA ||
          !authorB ||
          authorA.includes(authorB) ||
          authorB.includes(authorA) ||
          authorA === 'unknown author' ||
          authorB === 'unknown author'
        ) {
          matchedIndex = i;
          break;
        }
      }
    }

    if (matchedIndex >= 0) {
      mergedList[matchedIndex] = mergeBooks(mergedList[matchedIndex], item);
    } else {
      mergedList.push(item);
    }
  }

  // Rank merged books by relevance to the query
  const qClean = cleanQuery(query.toLowerCase());
  return mergedList.sort((a, b) => {
    const scoreA = calculateRelevance(a, qClean);
    const scoreB = calculateRelevance(b, qClean);
    return scoreB - scoreA;
  });
}

function calculateRelevance(book: BookMetadata, qClean: string): number {
  let score = 0;
  const title = cleanQuery(book.title.toLowerCase());
  const author = cleanQuery(book.primaryAuthor.toLowerCase());

  if (title === qClean) score += 100;
  else if (title.startsWith(qClean)) score += 60;
  else if (title.includes(qClean)) score += 35;

  if (author === qClean) score += 70;
  else if (author.includes(qClean)) score += 30;

  if (book.hasValidIsbn) score += 15;
  if (book.coverUrl) score += 15;
  if (book.totalPages && book.totalPages > 0) score += 15;
  if (book.rating) score += (book.rating / 10) * 10;
  if (book.ratingsCount) score += Math.min(Math.log10(book.ratingsCount + 1) * 20, 50);

  // Bonus for English if query is ASCII
  const isAscii = /^[\x00-\x7F]*$/.test(qClean);
  if (isAscii && (!book.language || book.language === 'en' || book.language === 'eng')) {
    score += 10;
  }

  return score;
}

