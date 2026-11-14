import assert from 'node:assert/strict';
import { afterEach, test } from 'node:test';
import { getCachedSearch, getStaleSearch, setCachedSearch } from '../src/cache/cacheManager';
import { ProviderCooldown, providerQuotaKey } from '../src/providers/cooldown';
import { providerFetch } from '../src/providers/http';
import { searchMetadata } from '../src/search/service';
import { SearchResponse, SearchResult } from '../src/types';
import { searchInventaire } from '../src/providers/books/inventaire';
import { searchJikanManga } from '../src/providers/manga/jikanManga';
import { searchMangaDex } from '../src/providers/manga/mangadex';
import { searchWikipedia } from '../src/providers/boardgames/wikipediaBoardGames';
import { searchKitsuAnime } from '../src/providers/anime/kitsuAnime';
import { deduplicateAndMerge, withSource } from '../src/merger/entityMerger';

const originalFetch = globalThis.fetch;
const originalNow = Date.now;
afterEach(() => { globalThis.fetch = originalFetch; Date.now = originalNow; });
const json = (value: unknown) => new Response(JSON.stringify(value), { headers: { 'Content-Type': 'application/json' } });
const result: SearchResult = { title: 'Dune', creator: 'Frank Herbert', category: 'BOOK', releaseYear: '1965',
  releaseDate: '', coverUrl: '', overview: '', rating: 0, totalEpisodes: 0, totalPages: 0, externalUrl: '' };
const response = (partial = false, empty = false): SearchResponse => ({ query: 'Dune', normalizedQuery: 'Dune',
  category: 'BOOK', source: 'live', schemaVersion: 2, count: empty ? 0 : 1, results: empty ? [] : [result],
  status: partial ? 'partial' : 'complete', incompleteSources: partial ? ['googlebooks'] : [] });

test('useful partial responses have a short cache lifetime; outages never become negative cache hits', () => {
  setCachedSearch('partial-fixture', response(true), 1000);
  assert.equal(getCachedSearch('partial-fixture', 30_999)?.status, 'partial');
  assert.equal(getCachedSearch('partial-fixture', 31_000), null);
  assert.equal(getStaleSearch('partial-fixture', 31_000)?.results[0].title, 'Dune');
  setCachedSearch('failed-fixture', response(true, true), 1000);
  assert.equal(getCachedSearch('failed-fixture', 1001), null);
  assert.equal(getStaleSearch('failed-fixture', 1001), null);
  assert.equal(getStaleSearch('partial-fixture', 3_631_000), null);
});

test('cached data is isolated from mutation, query keys and healthy no-match entries', () => {
  setCachedSearch('positive-fixture', response(), 1000);
  getCachedSearch('positive-fixture', 1001)!.results[0].title = 'Mutated';
  assert.equal(getCachedSearch('positive-fixture', 1002)?.results[0].title, 'Dune');
  assert.equal(getCachedSearch('different-query-fixture', 1002), null);
  setCachedSearch('empty-fixture', response(false, true), 1000);
  assert.equal(getCachedSearch('empty-fixture', 1001)?.count, 0);
  assert.equal(getCachedSearch('empty-fixture', 31_000), null);
  assert.equal(getStaleSearch('empty-fixture', 31_000), null);
});

test('search serves an expired usable result during an outage and labels its freshness', async () => {
  const now = originalNow();
  Date.now = () => now;
  globalThis.fetch = async input => String(input).includes('openlibrary.org')
    ? json({ docs: [{ key: '/works/stalefixture', title: 'Stalefixture', author_name: ['Author'] }] })
    : json({ totalItems: 0 });
  const options = { query: 'Stalefixture', category: 'BOOK' as const, mode: 'ALL' as const };
  const fresh = await searchMetadata(options, {});
  assert.equal(fresh.status, 'complete');
  Date.now = () => now + 11 * 60_000;
  globalThis.fetch = async () => { throw new Error('upstreams offline'); };
  const stale = await searchMetadata(options, {});
  assert.equal(stale.source, 'cache');
  assert.equal(stale.freshness, 'stale');
  assert.equal(stale.status, 'partial');
  assert.equal(stale.results[0].title, 'Stalefixture');
  assert(stale.incompleteSources?.includes('openlibrary'));
});

test('rate-limit backoff honors seconds/date values, expires, and bounds unreasonable waits', () => {
  const cooldown = new ProviderCooldown();
  cooldown.record('seconds', '30', 1000);
  assert.equal(cooldown.remaining('seconds', 2000), 29);
  assert.equal(cooldown.remaining('seconds', 31_000), 0);
  cooldown.record('date', new Date(60_000).toUTCString(), 1000);
  assert.equal(cooldown.remaining('date', 1000), 59);
  cooldown.record('huge', '99999999', 1000);
  assert.equal(cooldown.remaining('huge', 1000), 900);
  cooldown.record('invalid', 'bad-value', 1000);
  assert.equal(cooldown.remaining('invalid', 1000), 60);
});

test('provider cooldown uses credential identity and skips repeated rate-limited HTTP calls', async () => {
  const a = await providerQuotaKey('https://quota-fixture.test/search?q=private&key=key-a', {});
  const b = await providerQuotaKey('https://quota-fixture.test/search?q=different&key=key-a', {});
  const c = await providerQuotaKey('https://quota-fixture.test/search?key=key-b', {});
  assert.equal(a, b);
  assert.notEqual(a, c);
  assert(!a.includes('key-a'));
  let calls = 0;
  globalThis.fetch = async () => { calls++; return new Response(null, { status: 429, headers: { 'Retry-After': '30' } }); };
  await providerFetch('https://quota-fixture.test/search?key=key-a');
  const skipped = await providerFetch('https://quota-fixture.test/search?key=key-a&q=second');
  assert.equal(skipped.status, 429);
  assert.equal(calls, 1);
  await providerFetch('https://quota-fixture.test/search?key=key-b');
  assert.equal(calls, 2);
});

test('Inventaire only returns hydrated book works and never trusts ambiguous external crosswalks', async () => {
  let calls = 0;
  globalThis.fetch = async input => {
    calls++;
    const url = new URL(String(input));
    if (url.pathname === '/api/search') return json({ results: [
      { uri: 'wd:Q190192', type: 'works', label: 'Dune' },
      { uri: 'wd:Q7934', type: 'humans', label: 'Frank Herbert' },
      { uri: 'wd:Q42', type: 'works', label: 'Unrelated' },
    ] });
    assert.equal(url.searchParams.get('uris'), 'wd:Q190192');
    return json({ entities: {
      'wd:Q190192': { uri: 'wd:Q190192', type: 'work', labels: { en: 'Dune' }, claims: {
        'wdt:P50': ['wd:Q7934'], 'wdt:P577': ['1965'], 'wdt:P648': ['OL1W', 'OL2W'],
      }, image: { url: '/img/entities/' + 'a'.repeat(40) } },
      'wd:Q7934': { type: 'human', labels: { en: 'Frank Herbert' } },
    } });
  };
  const books = await searchInventaire('Dune');
  assert.equal(calls, 2);
  assert.equal(books.length, 1);
  assert.equal(books[0].creator, 'Frank Herbert');
  assert.equal(books[0].releaseYear, '1965');
  assert(books[0].coverUrl.startsWith('https://inventaire.io/img/entities/'));
  assert(!books[0].sourceRefs?.some(r => r.source === 'openlibrary'));
});

test('MangaDex verified MAL IDs merge alternate titles while unioning richer Jikan metadata', async () => {
  globalThis.fetch = async input => String(input).includes('jikan.moe') ? json({ data: [{
    mal_id: 23390, title: 'Shingeki no Kyojin', title_english: 'Attack on Titan', score: 8.5, scored_by: 100,
    authors: [{ name: 'Hajime Isayama' }], chapters: 141, volumes: 34,
    genres: [{ name: 'Action' }], themes: [{ name: 'Military' }], demographics: [{ name: 'Shounen' }], status: 'Finished',
  }] }) : json({ data: [{ id: 'dex-id', attributes: {
    title: { en: 'A differently localized title' }, links: { mal: '23390' }, originalLanguage: 'ja',
    tags: [{ attributes: { group: 'genre', name: { en: 'Drama' } } }, { attributes: { group: 'theme', name: { en: 'Survival' } } }],
  }, relationships: [{ type: 'author', attributes: { name: 'Hajime Isayama' } }] }] });
  const jikan = await searchJikanManga('Attack on Titan');
  const dex = await searchMangaDex('Attack on Titan');
  const merged = deduplicateAndMerge([...jikan.map(r => withSource(r, 'jikan')), ...dex.map(r => withSource(r, 'mangadex'))]);
  assert.equal(merged.length, 1);
  assert.deepEqual(merged[0].genres, ['Action', 'Drama']);
  assert.deepEqual(merged[0].themes, ['Military', 'Survival']);
  assert.equal(merged[0].totalVolumes, 34);
  assert.equal(merged[0].totalChapters, 141);
  assert(merged[0].ratings?.some(r => r.source === 'myanimelist' && r.count === 100));
});

test('distinct book ISBNs survive even when supplied under one provider work ID', () => {
  const record = withSource({ ...result, sourceRefs: [{ source: 'hardcover', id: 'work', url: '' }],
    editions: [{ source: 'hardcover', sourceId: 'work', isbn: '9780441172719' },
      { source: 'hardcover', sourceId: 'work', isbn: '9780063256019' }] }, 'hardcover');
  assert.equal(deduplicateAndMerge([record])[0].editions?.length, 2);
});

test('Wikipedia and Inventaire share verified Wikidata identity instead of duplicating a book', async () => {
  globalThis.fetch = async () => json({ query: { pages: { '42': { pageid: 42, title: 'Dune (novel)',
    extract: 'Dune is a 1965 science fiction novel by Frank Herbert.',
    pageprops: { wikibase_item: 'Q190192' }, fullurl: 'https://en.wikipedia.org/wiki/Dune_(novel)',
  } } } });
  const wikipedia = await searchWikipedia('Dune', 'BOOK');
  const inventaire = withSource({ ...result, sourceRefs: [
    { source: 'inventaire', id: 'wd:Q190192', url: 'https://inventaire.io/entity/wd:Q190192' },
    { source: 'wikidata', id: 'Q190192', url: 'https://www.wikidata.org/wiki/Q190192' },
  ] }, 'inventaire');
  const merged = deduplicateAndMerge([...wikipedia.map(r => withSource(r, 'wikipedia')), inventaire]);
  assert.equal(merged.length, 1);
  assert.equal(merged[0].creator, 'Frank Herbert');
});

test('Kitsu includes genres in the same request and keeps each result genre-specific', async () => {
  let requests = 0;
  globalThis.fetch = async input => {
    requests++;
    assert(new URL(String(input)).searchParams.get('include') === 'genres');
    return json({ data: [
      { id: '1', attributes: { canonicalTitle: 'Spacefixture', episodeCount: 26, episodeLength: 24 },
        relationships: { genres: { data: [{ type: 'genres', id: 'g1' }] } } },
      { id: '2', attributes: { canonicalTitle: 'Romancefixture' },
        relationships: { genres: { data: [{ type: 'genres', id: 'g2' }] } } },
    ], included: [
      { id: 'g1', type: 'genres', attributes: { name: 'Sci-Fi' } },
      { id: 'g2', type: 'genres', attributes: { name: 'Romance' } },
    ] });
  };
  const results = await searchKitsuAnime('fixture');
  assert.equal(requests, 1);
  assert.deepEqual(results[0].genres, ['Sci-Fi']);
  assert.deepEqual(results[1].genres, ['Romance']);
  assert.equal(results[0].runtimeMinutes, 24);
});

test('book fallback excludes franchises and book-series pages from individual works', async () => {
  globalThis.fetch = async () => json({ query: { pages: {
    1: { pageid: 1, title: 'Dune (franchise)', extract: 'Dune is a science fiction media franchise that originated with a novel by Frank Herbert.' },
    2: { pageid: 2, title: 'Dune prequel series', extract: 'The Dune prequel series is a series of novels written by Brian Herbert.' },
    3: { pageid: 3, title: 'Dune (novel)', extract: 'Dune is a 1965 novel by Frank Herbert.' },
    4: { pageid: 4, title: 'Dune prequel series', extract: 'The Dune prequel series is a sequence of novel trilogies written by Brian Herbert.' },
  } } });
  assert.deepEqual((await searchWikipedia('Dune', 'BOOK')).map(r => r.title), ['Dune']);
});
