import { SearchResult } from '../types';

interface CacheEntry {
  data: SearchResult[];
  timestamp: number;
}

const MEMORY_CACHE = new Map<string, CacheEntry>();
const NEGATIVE_CACHE = new Map<string, number>();

const POSITIVE_TTL_MS = 24 * 60 * 60 * 1000; // 24 hours
const NEGATIVE_TTL_MS = 10 * 60 * 1000;      // 10 minutes

export function getCachedSearch(key: string): SearchResult[] | null {
  const now = Date.now();

  // 1. Check negative cache
  const negTime = NEGATIVE_CACHE.get(key);
  if (negTime) {
    if (now - negTime < NEGATIVE_TTL_MS) {
      return [];
    } else {
      NEGATIVE_CACHE.delete(key);
    }
  }

  // 2. Check positive cache
  const entry = MEMORY_CACHE.get(key);
  if (entry) {
    if (now - entry.timestamp < POSITIVE_TTL_MS) {
      return entry.data;
    } else {
      MEMORY_CACHE.delete(key);
    }
  }

  return null;
}

export function setCachedSearch(key: string, data: SearchResult[]): void {
  const now = Date.now();
  if (data.length > 0) {
    MEMORY_CACHE.set(key, { data, timestamp: now });
    NEGATIVE_CACHE.delete(key);

    // Limit memory cache size to 500 entries per worker instance
    if (MEMORY_CACHE.size > 500) {
      const firstKey = MEMORY_CACHE.keys().next().value;
      if (firstKey) MEMORY_CACHE.delete(firstKey);
    }
  } else {
    NEGATIVE_CACHE.set(key, now);
  }
}

