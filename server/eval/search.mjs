import { readFile } from 'node:fs/promises';
import { performance } from 'node:perf_hooks';

// Opt-in live checks, not a scheduled job. Exactly one search per fixture.
const fixtures = JSON.parse(await readFile(new URL('./search-cases.json', import.meta.url), 'utf8'));
const base = (process.env.SEARCH_EVAL_BASE || 'https://lists-search-engine.minimal-lists.workers.dev/v2').replace(/\/$/, '');
const normalize = s => (s || '').normalize('NFKC').toLowerCase().replace(/[^\p{L}\p{N}]+/gu, ' ').trim();
const records = [];
for (const fixture of fixtures) {
  const start = performance.now();
  try {
    const url = new URL(`${base}/search`);
    url.searchParams.set('q', fixture.query);
    url.searchParams.set('category', fixture.category);
    const response = await fetch(url, { signal: AbortSignal.timeout(12_000) });
    const body = await response.json();
    const results = body.results || [];
    const rank = results.findIndex(r => normalize(r.title) === normalize(fixture.expectedTitle) &&
      (!fixture.expectedCreator || normalize(r.creator).includes(normalize(fixture.expectedCreator))) &&
      (!fixture.expectedRef || r.sourceRefs?.some(ref => ref.source === fixture.expectedRef.source && ref.id === fixture.expectedRef.id)));
    const seen = new Set();
    const repeatedIdentityRefs = new Set();
    for (const result of results) {
      const own = new Set((result.sourceRefs || []).map(r => `${r.source}:${r.id}`));
      for (const id of own) { if (seen.has(id)) repeatedIdentityRefs.add(id); seen.add(id); }
    }
    records.push({ query: fixture.query, category: fixture.category, httpStatus: response.status,
      latencyMs: Math.round(performance.now() - start), expectedRank: rank < 0 ? null : rank + 1,
      count: results.length, status: body.status, source: body.source, freshness: body.freshness,
      topResult: results[0] ? { title: results[0].title, creator: results[0].creator } : null,
      repeatedIdentityRefs: [...repeatedIdentityRefs],
      coverPresent: rank >= 0 && !!results[rank].coverUrl,
      matchedMetadata: rank >= 0 ? { genres: results[rank].genres || [], themes: results[rank].themes || [],
        demographics: results[rank].demographics || [], totalEpisodes: results[rank].totalEpisodes || 0,
        totalChapters: results[rank].totalChapters || 0, totalVolumes: results[rank].totalVolumes || 0,
        ratingSources: (results[rank].ratings || []).map(r => ({ source: r.source, count: r.count })) } : null,
    });
  } catch (error) {
    records.push({ query: fixture.query, category: fixture.category,
      latencyMs: Math.round(performance.now() - start), expectedRank: null, error: error.message });
  }
}
const latencies = records.map(r => r.latencyMs).sort((a, b) => a - b);
const percentile = p => latencies[Math.min(latencies.length - 1, Math.ceil(latencies.length * p) - 1)] || 0;
const summary = { at: new Date().toISOString(), queries: fixtures.length,
  top1: records.filter(r => r.expectedRank === 1).length,
  found: records.filter(r => r.expectedRank !== null).length,
  meanReciprocalRank: records.reduce((sum, r) => sum + (r.expectedRank ? 1 / r.expectedRank : 0), 0) / records.length,
  p50Ms: percentile(0.5), p95Ms: percentile(0.95),
  note: 'Small smoke set, not a representative recall or latency benchmark. Cover presence does not verify image delivery.',
};
console.log(JSON.stringify({ summary, records }, null, 2));
if (summary.found !== fixtures.length || records.some(r => r.httpStatus !== 200 || r.repeatedIdentityRefs?.length)) process.exitCode = 1;
