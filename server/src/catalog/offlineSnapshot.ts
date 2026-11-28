import { SearchResult } from '../types';

/** Small recovery/tracking context. Full descriptions, source evidence and alternatives stay server-side. */
export function offlineSnapshot(result: SearchResult): Record<string, unknown> {
  const snapshot: Record<string, unknown> = {
    schemaVersion: 1, id: result.id || '', revision: result.catalogRevision || 0,
    entityKind: result.entityKind || '',
    sourceRefs: (result.sourceRefs || []).slice(0, 8).map(({ source, id }) => ({ source, id })),
    ...(result.isbn ? { isbn: result.isbn } : {}),
    genres: (result.genres || []).slice(0, 20), platforms: (result.platforms || []).slice(0, 12),
    ratings: (result.ratings || []).slice(0, 8).map(({ source, value, scale, count }) => ({ source, value, scale, ...(count === undefined ? {} : { count }) })),
    totalChapters: result.totalChapters || 0, totalVolumes: result.totalVolumes || 0,
    totalIssues: result.totalIssues || 0, runtimeMinutes: result.runtimeMinutes || 0,
    ...(result.status ? { status: result.status } : {}),
  };
  // Titles, overview, cover, personal notes and progress already have typed local columns.
  for (const key of ['genres', 'platforms', 'ratings']) {
    if (new TextEncoder().encode(JSON.stringify(snapshot)).byteLength > 2048) delete snapshot[key];
  }
  while (new TextEncoder().encode(JSON.stringify(snapshot)).byteLength > 2048 && (snapshot.sourceRefs as unknown[]).length) {
    (snapshot.sourceRefs as unknown[]).pop();
  }
  return snapshot;
}
