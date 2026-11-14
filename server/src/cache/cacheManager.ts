import { SearchResponse } from '../types';
interface CacheEntry { data: SearchResponse; expiresAt: number; staleUntil: number; bytes: number }
const cache = new Map<string, CacheEntry>();
const inFlight = new Map<string, Promise<SearchResponse>>();
const LIMIT = 200;
const BYTE_LIMIT = 16 * 1024 * 1024;
let usedBytes = 0;
function remove(key: string): void { usedBytes -= cache.get(key)?.bytes || 0; cache.delete(key); }
export function getCachedSearch(key: string, now = Date.now()): SearchResponse | null {
  const entry = cache.get(key);
  if (!entry) return null;
  if (now >= entry.staleUntil) { remove(key); return null; }
  if (now >= entry.expiresAt) return null;
  cache.delete(key); cache.set(key, entry);
  return structuredClone(entry.data);
}
export function getStaleSearch(key: string, now = Date.now()): SearchResponse | null {
  const entry = cache.get(key);
  if (!entry || !entry.data.results.length) return null;
  if (now >= entry.staleUntil) { remove(key); return null; }
  return structuredClone(entry.data);
}
export function setCachedSearch(key: string, data: SearchResponse, now = Date.now()): void {
  // An outage must never be cached as an authoritative absence. Useful partial
  // results get a short lifetime so unavailable providers can recover quickly.
  if (data.status === 'partial' && !data.results.length) return;
  const ttl = data.status === 'partial' ? 30_000 : data.results.length ? 10 * 60_000 : 30_000;
  const bytes = new TextEncoder().encode(JSON.stringify(data)).byteLength;
  if (bytes > 512 * 1024) return;
  remove(key);
  cache.set(key, { data: structuredClone(data), expiresAt: now + ttl,
    staleUntil: now + ttl + (data.results.length ? 60 * 60_000 : 0), bytes });
  usedBytes += bytes;
  while (cache.size > LIMIT || usedBytes > BYTE_LIMIT) remove(cache.keys().next().value!);
}
export function coalesceSearch(key: string, run: () => Promise<SearchResponse>): Promise<SearchResponse> {
  const active = inFlight.get(key);
  if (active) return active;
  // Bound coalescing memory without refusing to serve uncached requests.
  if (inFlight.size >= LIMIT) return run();
  const promise = run().finally(() => inFlight.delete(key));
  inFlight.set(key, promise);
  return promise;
}
