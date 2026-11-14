import { BookMetadata } from '../types';
import { toSearchResult, projectBookMetadata } from '../projection';
import { deduplicateAndMerge, withSource } from '../../merger/entityMerger';
import { calculateRelevance } from '../../ranker/relevanceScorer';

export function deduplicateAndRank(items: BookMetadata[], query: string): BookMetadata[] {
  const records = items.map(book => {
    const result = toSearchResult(book);
    return book.metadata?.length ? result : withSource(result, result.sourceRefs![0].source);
  });
  return deduplicateAndMerge(records)
    .map(result => ({ result, score: calculateRelevance(query, query, undefined, result) }))
    .sort((a, b) => b.score - a.score || (a.result.id || '').localeCompare(b.result.id || ''))
    .map(({ result }) => projectBookMetadata(result));
}
export function mergeBooks(a: BookMetadata, b: BookMetadata): BookMetadata {
  const merged = deduplicateAndRank([a, b], a.title);
  if (merged.length !== 1) throw new Error('Cannot merge books without compatible identity evidence');
  return merged[0];
}
