import { SearchResult } from '../types';

export function isValidSearchResult(item: SearchResult): boolean {
  const title = (item.title || '').trim();
  if (!title) return false;

  const lower = title.toLowerCase();
  if (
    lower === 'not found' ||
    lower === 'error' ||
    lower === '404' ||
    lower.startsWith('list of ') ||
    lower.startsWith('lists of ') ||
    lower.includes('(disambiguation)')
  ) {
    return false;
  }

  // Must have at least one meaningful piece of metadata
  return Boolean(
    item.coverUrl ||
    item.overview ||
    item.creator ||
    item.releaseYear
  );
}

