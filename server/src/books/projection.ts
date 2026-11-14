import { SearchResult } from '../types';
import { BookMetadata } from './types';
import { resolveIsbnPair } from './normalizer/isbn';

const sourceName: Record<string, string> = { GOOGLE_BOOKS: 'googlebooks', OPEN_LIBRARY: 'openlibrary', ISBNDB: 'isbndb' };
export function toSearchResult(book: BookMetadata, category: 'BOOK' | 'MANGA' | 'COMIC' = 'BOOK'): SearchResult {
  const source = sourceName[book.source] || book.source.toLowerCase();
  const sourceId = book.providerId || book.externalUrl || book.id;
  const { metadata, sourceRefs, editions, ...sourceBook } = book;
  const authors = book.authors.filter(name => name && name.toLowerCase() !== 'unknown author');
  return {
    title: book.title, creator: authors.join(', '), creators: authors.map(name => ({ name, role: 'author' })),
    releaseYear: book.publishedYear || '', releaseDate: book.publishedDate || '',
    coverUrl: book.coverUrl || '', overview: (book.overview || '').replace(/<[^>]*>/g, ''),
    rating: book.rating || 0, ratings: book.rating ? [{ source, value: book.rating, scale: 10, count: book.ratingsCount }] : [],
    totalPages: book.totalPages || 0, totalEpisodes: 0, category, externalUrl: book.externalUrl || '',
    sourceRefs: sourceRefs || [{ source, id: sourceId, url: book.externalUrl || '' }],
    metadata, editions,
    isbn: book.primaryIsbn || '', publisher: book.publisher || '', language: book.language || '',
    aliases: book.subtitle ? [`${book.title}: ${book.subtitle}`] : [],
    genres: [...book.rawCategories, ...book.rawSubjects].slice(0, 40),
    bookMetadata: sourceBook,
  };
}

/** One rich book projection for universal search and the legacy /api/v1/books API. */
export function projectBookMetadata(result: SearchResult): BookMetadata {
  const supplied = (result.metadata || []).flatMap(e => e.fields.bookMetadata ? [e.fields.bookMetadata as BookMetadata] : []);
  if (!supplied.length && result.bookMetadata) supplied.push(result.bookMetadata);
  const pair = resolveIsbnPair(result.isbn);
  const primaryIsbn = pair.isbn13 || pair.isbn10;
  const primary = supplied.find(b => b.primaryIsbn && [pair.isbn10, pair.isbn13].includes(b.primaryIsbn)) || supplied[0];
  const rawCategories = [...new Set(supplied.flatMap(b => b.rawCategories || []))].sort();
  const rawSubjects = [...new Set(supplied.flatMap(b => b.rawSubjects || []))].sort();
  const authors = [...new Set((result.creators || []).filter(c => c.role === 'author').map(c => c.name))];
  if (!authors.length && result.creator) authors.push(result.creator);
  const estimate = supplied.find(b => b.estimatedPages)?.estimatedPages;
  return {
    id: primaryIsbn || primary?.id || result.id || '',
    canonicalId: result.id, providerId: primary?.providerId, title: result.title, subtitle: primary?.subtitle,
    authors, primaryAuthor: authors[0] || '', ...pair, primaryIsbn, hasValidIsbn: !!primaryIsbn,
    publisher: result.publisher || undefined, publishedDate: result.releaseDate || undefined,
    publishedYear: result.releaseYear || undefined, totalPages: result.totalPages || undefined,
    estimatedPages: estimate, language: result.language || undefined,
    overview: result.overview || undefined, coverUrl: result.coverUrl || undefined,
    thumbnailCoverUrl: primary?.thumbnailCoverUrl || result.coverUrl || undefined,
    rating: result.rating || undefined,
    ratingsCount: result.ratings?.find(r => Math.abs(r.value / r.scale * 10 - result.rating) < 0.001)?.count,
    externalUrl: result.externalUrl || undefined,
    source: supplied.length === 1 ? primary.source : 'COMBINED',
    rawCategories, rawSubjects,
    sourceRefs: result.sourceRefs, metadata: result.metadata, editions: result.editions,
  };
}
