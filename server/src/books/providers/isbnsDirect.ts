import { BookMetadata } from '../types';
import { isValidIsbn, cleanIsbn, isbn10ToIsbn13 } from '../normalizer/isbn';
import { lookupGoogleBookByIsbn } from './googleBooks';
import { lookupOpenLibraryByIsbn } from './openLibrary';
import { lookupIsbnDb } from './isbnDb';
import { deduplicateAndMerge, normalizeIsbn, withSource } from '../../merger/entityMerger';
import { toSearchResult, projectBookMetadata } from '../projection';
import { ProviderOutcome, runProvider } from '../../providers/http';

export async function lookupIsbnRecords(rawIsbn: string, options: { googleApiKey?: string; isbndbApiKey?: string } = {}, signal = AbortSignal.timeout(3000)): Promise<ProviderOutcome[]> {
  const clean = cleanIsbn(rawIsbn);
  if (!isValidIsbn(clean)) return [];
  const canonical = clean.length === 10 ? isbn10ToIsbn13(clean)! : clean;
  const convert = (book: BookMetadata | null) => book && normalizeIsbn(book.primaryIsbn || '') === canonical ? [toSearchResult(book)] : [];
  const tasks = [
    runProvider('openlibrary', async () => convert(await lookupOpenLibraryByIsbn(clean)), signal),
    runProvider('googlebooks', async () => convert(await lookupGoogleBookByIsbn(clean, options.googleApiKey)), signal),
  ];
  if (options.isbndbApiKey) tasks.push(runProvider('isbndb', async () => convert(await lookupIsbnDb(clean, options.isbndbApiKey)), signal));
  return Promise.all(tasks);
}

export async function lookupDirectIsbn(rawIsbn: string, options?: { googleApiKey?: string; isbndbApiKey?: string }): Promise<BookMetadata | null> {
  const outcomes = await lookupIsbnRecords(rawIsbn, options);
  const records = outcomes.flatMap(o => o.results.map(r => withSource(r, o.source)));
  const merged = deduplicateAndMerge(records);
  return merged.length === 1 ? projectBookMetadata(merged[0]) : null;
}
