import { BookMetadata } from '../types';
import { cleanIsbn, resolveIsbnPair } from '../normalizer/isbn';
import { mapAttributes } from '../normalizer/attributes';

export async function lookupIsbnDb(isbn: string, apiKey?: string): Promise<BookMetadata | null> {
  if (!apiKey) return null;
  const clean = cleanIsbn(isbn);
  if (!clean) return null;

  try {
    const url = `https://api2.isbndb.com/book/${clean}`;
    const res = await fetch(url, {
      headers: {
        'Authorization': apiKey,
        'User-Agent': 'ListsBooksCloudEngine/1.0',
      },
      signal: AbortSignal.timeout(5000),
    });

    if (!res.ok) return null;
    const data = await res.json() as any;
    const b = data.book;
    if (!b || !b.title) return null;

    const { isbn10, isbn13 } = resolveIsbnPair(b.isbn13 || b.isbn || b.isbn10 || clean);
    const authors = Array.isArray(b.authors) ? b.authors.map(String) : [];
    const subjects = Array.isArray(b.subjects) ? b.subjects.map(String) : [];

    const { normalizedGenres, suggestedMoods, suggestedPacing, lengthBracket, recommendationVector } =
      mapAttributes([], subjects, b.pages);

    const primaryIsbn = isbn13 || isbn10 || clean;

    return {
      id: primaryIsbn,
      title: b.title.trim(),
      subtitle: b.title_long && b.title_long !== b.title ? b.title_long : undefined,
      authors,
      primaryAuthor: authors[0] || 'Unknown Author',
      isbn10,
      isbn13,
      primaryIsbn,
      hasValidIsbn: true,
      publisher: b.publisher,
      publishedYear: b.date_published ? String(b.date_published).substring(0, 4) : undefined,
      publishedDate: b.date_published,
      totalPages: b.pages || undefined,
      language: b.language,
      overview: b.synopsis,
      coverUrl: b.image || `https://covers.openlibrary.org/b/isbn/${primaryIsbn}-L.jpg?default=false`,
      thumbnailCoverUrl: b.image,
      source: 'ISBNDB',
      rawCategories: [],
      rawSubjects: subjects,
      normalizedGenres,
      suggestedMoods,
      suggestedPacing,
      lengthBracket,
      recommendationVector,
    };
  } catch {
    return null;
  }
}

