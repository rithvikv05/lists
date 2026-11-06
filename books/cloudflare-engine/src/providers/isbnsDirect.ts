import { BookMetadata } from '../types';
import { cleanIsbn, isValidIsbn } from '../normalizer/isbn';
import { lookupGoogleBookByIsbn } from './googleBooks';
import { lookupOpenLibraryByIsbn } from './openLibrary';
import { lookupIsbnDb } from './isbnDb';
import { mergeBooks } from '../aggregator/merger';

export async function lookupDirectIsbn(
  rawIsbn: string,
  options?: { googleApiKey?: string; isbndbApiKey?: string }
): Promise<BookMetadata | null> {
  const cleaned = cleanIsbn(rawIsbn);
  if (!isValidIsbn(cleaned)) return null;

  const [olRes, googleRes, isbndbRes] = await Promise.allSettled([
    lookupOpenLibraryByIsbn(cleaned),
    lookupGoogleBookByIsbn(cleaned, options?.googleApiKey),
    options?.isbndbApiKey ? lookupIsbnDb(cleaned, options.isbndbApiKey) : Promise.resolve(null),
  ]);

  const candidates: BookMetadata[] = [];
  if (olRes.status === 'fulfilled' && olRes.value) candidates.push(olRes.value);
  if (googleRes.status === 'fulfilled' && googleRes.value) candidates.push(googleRes.value);
  if (isbndbRes.status === 'fulfilled' && isbndbRes.value) candidates.push(isbndbRes.value);

  if (candidates.length === 0) return null;
  if (candidates.length === 1) return candidates[0];

  return candidates.reduce((prev, curr) => mergeBooks(prev, curr));
}

