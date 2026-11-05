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
  if (!t1.size || !t2.size) return 0;

  let intersection = 0;
  for (const token of t1) {
    if (t2.has(token)) intersection++;
  }
  const union = new Set([...t1, ...t2]).size;
  return intersection / union;
}

