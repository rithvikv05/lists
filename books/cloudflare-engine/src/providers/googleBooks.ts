import { BookMetadata } from '../types';
import { cleanIsbn, isQueryAnIsbn, isbn10ToIsbn13, isbn13ToIsbn10 } from '../normalizer/isbn';
import { mapAttributes } from '../normalizer/attributes';
import { resolveBestCover } from '../aggregator/coverResolver';

export async function searchGoogleBooks(
  query: string,
  searchType: 'all' | 'title' | 'author' | 'isbn' = 'all',
  targetLanguage?: string,
  limit: number = 20,
  apiKey?: string
): Promise<BookMetadata[]> {
  try {
    const qClean = query.trim();
    let formattedQuery = qClean;

    if (searchType === 'author') {
      formattedQuery = `inauthor:${qClean}`;
    } else if (searchType === 'title') {
      formattedQuery = `intitle:${qClean}`;
    } else if (searchType === 'isbn' || isQueryAnIsbn(qClean)) {
      formattedQuery = `isbn:${cleanIsbn(qClean)}`;
    }

    const keyParam = apiKey ? `&key=${apiKey}` : '';
    const langParam = targetLanguage ? `&langRestrict=${encodeURIComponent(targetLanguage.toLowerCase())}` : '';
    const url = `https://www.googleapis.com/books/v1/volumes?q=${encodeURIComponent(formattedQuery)}&maxResults=${Math.min(limit, 40)}${langParam}${keyParam}`;

    const res = await fetch(url, {
      headers: { 'User-Agent': 'ListsBooksCloudEngine/1.0' },
      signal: AbortSignal.timeout(6000),
    });

    if (!res.ok) return [];
    const data = await res.json() as any;
    if (!data.items || !Array.isArray(data.items)) return [];

    return data.items
      .map((item: any) => parseGoogleBookItem(item))
      .filter((b: BookMetadata | null): b is BookMetadata => b !== null);
  } catch {
    return [];
  }
}

export async function lookupGoogleBookByIsbn(isbn: string, apiKey?: string): Promise<BookMetadata | null> {
  const clean = cleanIsbn(isbn);
  if (!clean) return null;
  const results = await searchGoogleBooks(clean, 'isbn', undefined, 1, apiKey);
  return results[0] || null;
}

export function parseGoogleBookItem(item: any): BookMetadata | null {
  const volumeId = item.id;
  const vol = item.volumeInfo;
  if (!vol || !vol.title) return null;

  const title = vol.title.trim();
  const subtitle = vol.subtitle ? vol.subtitle.trim() : undefined;

  const authors: string[] = Array.isArray(vol.authors)
    ? vol.authors.map((a: any) => String(a).trim()).filter(Boolean)
    : [];

  let isbn10: string | undefined;
  let isbn13: string | undefined;
  if (Array.isArray(vol.industryIdentifiers)) {
    for (const ident of vol.industryIdentifiers) {
      const type = String(ident.type || '').toUpperCase();
      const val = cleanIsbn(ident.identifier);
      if (type.includes('ISBN_13')) isbn13 = val;
      else if (type.includes('ISBN_10')) isbn10 = val;
    }
  }

  if (isbn13 && !isbn10) isbn10 = isbn13ToIsbn10(isbn13) || undefined;
  if (isbn10 && !isbn13) isbn13 = isbn10ToIsbn13(isbn10) || undefined;

  const publisher = vol.publisher ? String(vol.publisher).trim() : undefined;
  const publishedDate = vol.publishedDate ? String(vol.publishedDate).trim() : undefined;
  const publishedYear = publishedDate && publishedDate.length >= 4 ? publishedDate.substring(0, 4) : undefined;
  const totalPages = (vol.pageCount && vol.pageCount > 0) ? vol.pageCount : (vol.printedPageCount || undefined);
  const language = vol.language ? String(vol.language).toLowerCase() : undefined;
  const overview = vol.description ? String(vol.description).trim() : undefined;
  const rating = vol.averageRating ? Number(vol.averageRating) * 2.0 : undefined; // scale to 10
  const ratingsCount = vol.ratingsCount ? Number(vol.ratingsCount) : undefined;
  const externalUrl = vol.infoLink || undefined;

  const categories: string[] = Array.isArray(vol.categories)
    ? vol.categories.map((c: any) => String(c).trim()).filter(Boolean)
    : [];

  const rawCover = vol.imageLinks?.extraLarge ||
    vol.imageLinks?.large ||
    vol.imageLinks?.medium ||
    vol.imageLinks?.small ||
    vol.imageLinks?.thumbnail ||
    (volumeId ? `https://books.google.com/books/content?id=${volumeId}&printsec=frontcover&img=1&zoom=1` : undefined);

  const { coverUrl, thumbnailCoverUrl } = resolveBestCover(isbn13 || isbn10, undefined, rawCover);
  const { normalizedGenres, suggestedMoods, suggestedPacing, lengthBracket, recommendationVector } =
    mapAttributes(categories, [], totalPages);

  const primaryIsbn = isbn13 || isbn10;
  const id = primaryIsbn || `google_${volumeId}`;

  return {
    id,
    title,
    subtitle,
    authors,
    primaryAuthor: authors[0] || 'Unknown Author',
    isbn10,
    isbn13,
    primaryIsbn,
    hasValidIsbn: Boolean(primaryIsbn),
    publisher,
    publishedYear,
    publishedDate,
    totalPages,
    language,
    overview,
    coverUrl,
    thumbnailCoverUrl,
    rating,
    ratingsCount,
    externalUrl,
    source: 'GOOGLE_BOOKS',
    rawCategories: categories,
    rawSubjects: [],
    normalizedGenres,
    suggestedMoods,
    suggestedPacing,
    lengthBracket,
    recommendationVector,
  };
}

