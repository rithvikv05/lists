export function foldDiacritics(str: string): string {
  return str.normalize('NFD').replace(/[\u0300-\u036f]/g, '');
}

export function stripLeadingArticles(str: string): string {
  const lower = str.trim();
  const articles = ['the ', 'a ', 'an ', 'der ', 'die ', 'das ', 'le ', 'la ', 'les ', 'el ', 'los '];
  for (const art of articles) {
    if (lower.toLowerCase().startsWith(art)) {
      return lower.substring(art.length).trim();
    }
  }
  return lower;
}

export function cleanQuery(str: string): string {
  return foldDiacritics(str).trim();
}

export function levenshteinDistance(s1: string, s2: string): number {
  const a = s1.toLowerCase();
  const b = s2.toLowerCase();
  if (a === b) return 0;
  if (!a.length) return b.length;
  if (!b.length) return a.length;

  const dp: number[] = Array.from({ length: b.length + 1 }, (_, i) => i);
  for (let i = 1; i <= a.length; i++) {
    let prev = dp[0];
    dp[0] = i;
    for (let j = 1; j <= b.length; j++) {
      const temp = dp[j];
      dp[j] = a[i - 1] === b[j - 1] ? prev : Math.min(prev, dp[j], dp[j - 1]) + 1;
      prev = temp;
    }
  }
  return dp[b.length];
}

export function tokenJaccardSimilarity(s1: string, s2: string): number {
  const t1 = new Set(s1.toLowerCase().split(/[^a-z0-9]+/).filter(Boolean));
  const t2 = new Set(s2.toLowerCase().split(/[^a-z0-9]+/).filter(Boolean));
  if (t1.size === 0 || t2.size === 0) return 0.0;
  let intersection = 0;
  for (const token of t1) {
    if (t2.has(token)) intersection++;
  }
  const union = new Set([...t1, ...t2]).size;
  return intersection / union;
}

export function titleMatchScore(titleA: string, titleB: string): number {
  const a = cleanQuery(stripLeadingArticles(titleA.toLowerCase()));
  const b = cleanQuery(stripLeadingArticles(titleB.toLowerCase()));
  if (a === b) return 1.0;

  if (a.includes(b) || b.includes(a)) {
    const minLen = Math.min(a.length, b.length);
    const maxLen = Math.max(a.length, b.length);
    if (minLen / maxLen > 0.6) return 0.85;
  }

  const jaccard = tokenJaccardSimilarity(a, b);
  if (jaccard > 0.7) return jaccard;

  const maxLen = Math.max(a.length, b.length);
  if (maxLen > 3 && Math.abs(a.length - b.length) <= 3) {
    const dist = levenshteinDistance(a, b);
    if (dist <= 2) return 0.8;
  }

  return jaccard * 0.7;
}

