import { BookMetadata } from '../types';
import { cleanIsbn, isQueryAnIsbn, isbn10ToIsbn13, isbn13ToIsbn10 } from '../normalizer/isbn';
import { mapAttributes } from '../normalizer/attributes';
import { resolveBestCover } from '../aggregator/coverResolver';

export async function searchOpenLibrary(
  query: string,
  searchType: 'all' | 'title' | 'author' | 'isbn' = 'all',
  targetLanguage?: string,
  limit: number = 20
): Promise<BookMetadata[]> {
  try {
    const qClean = query.trim();
    const encoded = encodeURIComponent(qClean);
    const limitParam = Math.min(limit, 40);
    const langParam = targetLanguage ? `&language=${mapOlLang(targetLanguage)}` : '';

    const fieldsParam = '&fields=key,title,subtitle,author_name,isbn,number_of_pages_median,number_of_pages,cover_i,publisher,first_publish_year,subject,ratings_average,ratings_count,language,edition_count';

    let url: string;
    if (searchType === 'author') {
      url = `https://openlibrary.org/search.json?author=${encoded}${langParam}&limit=${limitParam}${fieldsParam}`;
    } else if (searchType === 'title') {
      url = `https://openlibrary.org/search.json?title=${encoded}${langParam}&limit=${limitParam}${fieldsParam}`;
    } else if (searchType === 'isbn' || isQueryAnIsbn(qClean)) {
      url = `https://openlibrary.org/search.json?isbn=${cleanIsbn(qClean)}&limit=${limitParam}${fieldsParam}`;
    } else {
      url = `https://openlibrary.org/search.json?q=${encoded}${langParam}&limit=${limitParam}${fieldsParam}`;
    }

    const res = await fetch(url, {
      headers: { 'User-Agent': 'ListsBooksCloudEngine/1.0' },
      signal: AbortSignal.timeout(10000),
    });

    if (!res.ok) return [];
    const data = await res.json() as any;
    if (!data.docs || !Array.isArray(data.docs)) return [];

    return data.docs
      .map((doc: any) => parseOpenLibraryDoc(doc))
      .filter((b: BookMetadata | null): b is BookMetadata => b !== null);
  } catch {
    return [];
  }
}

export async function lookupOpenLibraryByIsbn(isbn: string): Promise<BookMetadata | null> {
  const clean = cleanIsbn(isbn);
  if (!clean) return null;

  try {
    const bibUrl = `https://openlibrary.org/api/books?bibkeys=ISBN:${clean}&jscmd=data&format=json`;
    const res = await fetch(bibUrl, {
      headers: { 'User-Agent': 'ListsBooksCloudEngine/1.0' },
      signal: AbortSignal.timeout(5000),
    });

    if (res.ok) {
      const data = await res.json() as Record<string, any>;
      const key = `ISBN:${clean}`;
      if (data[key]) {
        return parseBibBook(clean, data[key]);
      }
    }
  } catch {}

  const searchResults = await searchOpenLibrary(clean, 'isbn', undefined, 1);
  return searchResults[0] || null;
}

function parseOpenLibraryDoc(doc: any): BookMetadata | null {
  const title = (doc.title || '').trim();
  if (!title) return null;
  const subtitle = doc.subtitle ? String(doc.subtitle).trim() : undefined;

  const authors: string[] = Array.isArray(doc.author_name)
    ? doc.author_name.map((a: any) => String(a).trim()).filter(Boolean)
    : [];

  let isbn10: string | undefined;
  let isbn13: string | undefined;
  if (Array.isArray(doc.isbn)) {
    for (const cand of doc.isbn) {
      const cleaned = cleanIsbn(cand);
      if (cleaned.length === 13 && !isbn13) isbn13 = cleaned;
      else if (cleaned.length === 10 && !isbn10) isbn10 = cleaned;
      if (isbn10 && isbn13) break;
    }
  }

  if (isbn13 && !isbn10) isbn10 = isbn13ToIsbn10(isbn13) || undefined;
  if (isbn10 && !isbn13) isbn13 = isbn10ToIsbn13(isbn10) || undefined;

  const publisher = Array.isArray(doc.publisher) ? String(doc.publisher[0]).trim() : undefined;
  const publishedYear = doc.first_publish_year ? String(doc.first_publish_year) : undefined;
  const totalPages = doc.number_of_pages_median || doc.number_of_pages || undefined;

  const subjects: string[] = Array.isArray(doc.subject)
    ? doc.subject.slice(0, 20).map((s: any) => String(s).trim()).filter(Boolean)
    : [];

  const coverId = typeof doc.cover_i === 'number' && doc.cover_i > 0 ? doc.cover_i : undefined;
  const { coverUrl, thumbnailCoverUrl } = resolveBestCover(isbn13 || isbn10, coverId);

  const overview = typeof doc.first_sentence === 'string'
    ? doc.first_sentence
    : Array.isArray(doc.first_sentence)
    ? doc.first_sentence[0]
    : undefined;

  const rating = doc.ratings_average ? Number(doc.ratings_average) * 2.0 : undefined; // scale to 10
  const ratingsCount = doc.ratings_count ? Number(doc.ratings_count) : undefined;
  const olKey = doc.key ? String(doc.key) : undefined;

  const { normalizedGenres, suggestedMoods, suggestedPacing, lengthBracket, recommendationVector } =
    mapAttributes([], subjects, totalPages);

  const primaryIsbn = isbn13 || isbn10;
  const id = primaryIsbn || (olKey ? `openlib_${olKey.replace(/[^a-zA-Z0-9_]/g, '')}` : `openlib_${title}`);

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
    totalPages,
    overview,
    coverUrl,
    thumbnailCoverUrl,
    rating,
    ratingsCount,
    externalUrl: olKey ? `https://openlibrary.org${olKey}` : undefined,
    source: 'OPEN_LIBRARY',
    rawCategories: [],
    rawSubjects: subjects,
    normalizedGenres,
    suggestedMoods,
    suggestedPacing,
    lengthBracket,
    recommendationVector,
  };
}

function parseBibBook(isbn: string, b: any): BookMetadata | null {
  const title = (b.title || '').trim();
  if (!title) return null;
  const subtitle = b.subtitle ? String(b.subtitle).trim() : undefined;

  const authors: string[] = Array.isArray(b.authors)
    ? b.authors.map((a: any) => (a.name ? String(a.name).trim() : '')).filter(Boolean)
    : [];

  const cleaned = cleanIsbn(isbn);
  const isbn10 = cleaned.length === 10 ? cleaned : isbn13ToIsbn10(cleaned) || undefined;
  const isbn13 = cleaned.length === 13 ? cleaned : isbn10ToIsbn13(cleaned) || undefined;

  const publisher = Array.isArray(b.publishers) ? b.publishers[0]?.name : undefined;
  const publishedDate = b.publish_date ? String(b.publish_date) : undefined;
  const publishedYear = publishedDate ? (publishedDate.match(/\b(19\d{2}|20\d{2})\b/)?.[0] || undefined) : undefined;
  const totalPages = b.number_of_pages || undefined;

  const subjects: string[] = Array.isArray(b.subjects)
    ? b.subjects.slice(0, 20).map((s: any) => (s.name ? String(s.name).trim() : '')).filter(Boolean)
    : [];

  const rawCover = b.cover?.large || b.cover?.medium;
  const { coverUrl, thumbnailCoverUrl } = resolveBestCover(isbn13 || isbn10, undefined, rawCover);

  const { normalizedGenres, suggestedMoods, suggestedPacing, lengthBracket, recommendationVector } =
    mapAttributes([], subjects, totalPages);

  const primaryIsbn = isbn13 || isbn10;
  return {
    id: primaryIsbn || `openlib_${isbn}`,
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
    coverUrl,
    thumbnailCoverUrl,
    externalUrl: b.url || undefined,
    source: 'OPEN_LIBRARY',
    rawCategories: [],
    rawSubjects: subjects,
    normalizedGenres,
    suggestedMoods,
    suggestedPacing,
    lengthBracket,
    recommendationVector,
  };
}

function mapOlLang(lang: string): string {
  const map: Record<string, string> = {
    hi: 'hin',
    ta: 'tam',
    te: 'tel',
    ml: 'mal',
    kn: 'kan',
    bn: 'ben',
    en: 'eng',
    es: 'spa',
    fr: 'fre',
    de: 'ger',
    zh: 'chi',
    ja: 'jpn',
    ko: 'kor',
  };
  return map[lang.toLowerCase()] || lang.toLowerCase();
}
