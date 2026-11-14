import { visibleGame } from './gamePolicy';
import { matchesBookAuthor, parseBookQuery } from '../normalizer/bookQuery';
import { isQueryAnIsbn } from '../books/normalizer/isbn';
import { lookupIsbnRecords } from '../books/providers/isbnsDirect';
import { SearchResponse, SearchResult } from '../types';
import { deduplicateAndMerge, withSource } from '../merger/entityMerger';
import { calculateRelevance } from '../ranker/relevanceScorer';
import { ProviderOutcome, runProvider } from '../providers/http';
import { steamDetails, tmdbDetails } from '../providers/details';
import { EngineEnv, fallbackPlan, providerPlan, SearchOptions } from './providers';

export function rankResults(records: SearchResult[], options: SearchOptions, allowSparseGames = false): SearchResult[] {
  return deduplicateAndMerge(records.filter(r => visibleGame(r, options.includeExtras, allowSparseGames)))
    .filter(r => !options.author || matchesBookAuthor(r.creator, options.author))
    .filter(r => !options.year || r.releaseYear === options.year || r.editions?.some(e => e.releaseDate?.startsWith(options.year!)))
    .filter(r => options.yearStart === undefined || (Number(r.releaseYear) >= options.yearStart && Number(r.releaseYear) <= options.yearEnd!))
    .map(result => ({ result, score: Math.max(...[result.title, ...(result.aliases || [])].map(title =>
      calculateRelevance(options.query, options.query, options.year, { ...result, title }))) }))
    .filter(({ score }) => score >= 30)
    .sort((a, b) => b.score - a.score || (a.result.id || a.result.title).localeCompare(b.result.id || b.result.title))
    .slice(0, 40).map(({ result }) => result);
}

export async function aggregateSearch(options: SearchOptions, env: EngineEnv): Promise<SearchResponse> {
  if (options.category === 'BOOK' && options.mode === 'ALL') {
    const parsed = parseBookQuery(options.query);
    if (parsed.author) options = { ...options, query: parsed.title, author: parsed.author };
  }
  // Leave a real recovery window after discovery. The previous 3s total budget
  // could abort backup providers before they had time to respond.
  const deadline = AbortSignal.timeout(5500);
  if (options.category === 'BOOK' && isQueryAnIsbn(options.query)) {
    const outcomes = await lookupIsbnRecords(options.query, { googleApiKey: env.GOOGLE_BOOKS_API_KEY, isbndbApiKey: env.ISBNDB_API_KEY }, deadline);
    const results = deduplicateAndMerge(outcomes.flatMap(o => o.results.map(r => withSource(r, o.source))));
    const incompleteSources = outcomes.filter(o => !['ok', 'empty'].includes(o.status)).map(o => o.source);
    return { query: options.query, normalizedQuery: options.query, category: 'BOOK', source: 'live', schemaVersion: 2,
      status: incompleteSources.length ? 'partial' : 'complete', incompleteSources, count: results.length, results };
  }
  const discovery = AbortSignal.any([deadline, AbortSignal.timeout(options.backup ? 3500 : 2500)]);
  const plan = options.backup ? fallbackPlan(options) : providerPlan(options, env);
  const outcomes = await Promise.all(plan.map(p => runProvider(p.source, p.run, discovery)));
  const records = outcomes.flatMap(o => o.results.map(r => withSource(r, o.source)));
  let ranked = rankResults(records, options, true);
  let usedFallback = !!options.backup;
  if (!ranked.length && !usedFallback) {
    usedFallback = true;
    const fallback = await Promise.all(fallbackPlan(options).map(p => runProvider(p.source, p.run, deadline)));
    outcomes.push(...fallback);
    records.push(...fallback.flatMap(o => o.results.map(r => withSource(r, o.source))));
    ranked = rankResults(records, options, true);
  }
  // Fetch details only for top candidates; never an N+1 fan-out for the entire result set.
  const enrichment: Promise<ProviderOutcome>[] = [];
  const tmdb = env.TMDB_API_KEY || env.TMDB_READ_ACCESS_TOKEN;
  const unique = new Set<string>();
  for (const item of ranked.slice(0, 3)) {
    const tmdbRef = item.sourceRefs?.find(r => r.source === 'tmdb');
    const steamRef = item.sourceRefs?.find(r => r.source === 'steam');
    if (tmdb && tmdbRef && (item.category === 'MOVIE' || item.category === 'TV_SHOW') && !unique.has(tmdbRef.id)) {
      unique.add(tmdbRef.id);
      enrichment.push(runProvider('tmdb', () => tmdbDetails(tmdbRef.id.split(':')[1], item.category as 'MOVIE' | 'TV_SHOW', tmdb, options.lang), deadline));
    }
    if (steamRef && !unique.has(steamRef.id)) {
      unique.add(steamRef.id);
      enrichment.push(runProvider('steam', () => steamDetails(steamRef.id, options.country), deadline));
    }
  }
  const details = await Promise.all(enrichment);
  outcomes.push(...details);
  // Detail responses replace their source's sparse fields while retaining other sources.
  for (const outcome of details) for (const detail of outcome.results) {
    const match = records.findIndex(r => r.metadata?.some(m => m.source === outcome.source) && (r.sourceRefs || []).some(ref =>
      ref.source === outcome.source && detail.sourceRefs?.some(d => d.source === ref.source && d.id === ref.id)));
    if (match >= 0) {
      const previous = records[match];
      const supplied = Object.fromEntries(Object.entries(detail).filter(([, value]) =>
        value !== undefined && value !== null && value !== '' && value !== 0 && (!Array.isArray(value) || value.length > 0)));
      records[match] = withSource({ ...previous, ...supplied,
        aliases: [...new Set([...(previous.aliases || []), ...(detail.aliases || [])])],
        sourceRefs: [...(previous.sourceRefs || []), ...(detail.sourceRefs || [])],
        metadata: undefined, editions: undefined,
      }, outcome.source);
    }
    else records.push(withSource(detail, outcome.source));
  }
  ranked = rankResults(records, options);
  if (!ranked.length && !usedFallback && !deadline.aborted) {
    const fallback = await Promise.all(fallbackPlan(options).map(p => runProvider(p.source, p.run, deadline)));
    outcomes.push(...fallback);
    records.push(...fallback.flatMap(o => o.results.map(r => withSource(r, o.source))));
    ranked = rankResults(records, options);
  }
  const incompleteSources = [...new Set(outcomes.filter(o => !['ok', 'empty'].includes(o.status)).map(o => o.source))];
  return { query: options.query, normalizedQuery: options.query, category: options.category,
    source: 'live', schemaVersion: 2, status: incompleteSources.length ? 'partial' : 'complete',
    incompleteSources, count: ranked.length, results: ranked };
}
