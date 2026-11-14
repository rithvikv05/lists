export function parseBookQuery(query: string): { title: string; author?: string } {
  const match = query.trim().match(/^(.+)\s+by\s+([\p{L}\p{M}.'’\-]+(?:\s+[\p{L}\p{M}.'’\-]+){1,5})$/iu);
  return match ? { title: match[1].trim().replace(/^["“]|["”]$/g, ''), author: match[2].trim() } : { title: query };
}

export function matchesBookAuthor(actual: string, wanted: string): boolean {
  const tokens = (s: string) => s.normalize('NFKC').toLowerCase().match(/[\p{L}\p{N}]+/gu) || [];
  const available = new Set(tokens(actual));
  return tokens(wanted).every(part => available.has(part));
}
