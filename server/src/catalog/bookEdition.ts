import { SearchResult } from '../types';
import { normalizeIsbn } from '../merger/entityMerger';
import { projectBookMetadata } from '../books/projection';

/** Canonical work identity is stable; an ISBN request must still return that exact edition's fields. */
export function selectBookEdition(result: SearchResult, requested: string): SearchResult {
  const isbn = normalizeIsbn(requested);
  if (result.category !== 'BOOK' || !isbn) return result;
  const editions = (result.editions || []).filter(e => normalizeIsbn(e.isbn || '') === isbn);
  if (!editions.length) return result;
  const selected = { ...result, isbn };
  for (const field of ['publisher', 'language', 'totalPages', 'releaseDate', 'coverUrl'] as const) {
    const evidence = editions.find(e => !!e[field]);
    (selected as unknown as Record<string, unknown>)[field] = evidence?.[field] ?? (field === 'totalPages' ? 0 : '');
  }
  const record = result.metadata?.find(e => normalizeIsbn(String(e.fields.isbn || '')) === isbn);
  if (typeof record?.fields.title === 'string') selected.title = record.fields.title;
  selected.releaseYear = selected.releaseDate.match(/(?:18|19|20)\d{2}/)?.[0] || result.releaseYear;
  const { metadata, sourceRefs, editions: _, ...bookFields } = projectBookMetadata(selected);
  selected.bookMetadata = bookFields;
  return selected;
}
