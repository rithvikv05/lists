import assert from 'node:assert/strict';
import { afterEach, test } from 'node:test';
import app from '../src/index';
import { deduplicateAndMerge, normalizeIsbn, withSource } from '../src/merger/entityMerger';
import { normalizeQuery } from '../src/normalizer/queryNormalizer';
import { parseGoodreads, resolveUrl, supportedUrl } from '../src/resolve/resolver';
import { runProvider, providerFetch } from '../src/providers/http';
import { aggregateSearch } from '../src/search/engine';
import { SearchResult } from '../src/types';

const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });
const item = (fields: Partial<SearchResult> = {}): SearchResult => ({
  title: 'Dune', creator: 'Frank Herbert', releaseYear: '1965', releaseDate: '',
  coverUrl: '', overview: '', rating: 0, totalEpisodes: 0, totalPages: 0, category: 'BOOK', externalUrl: '', ...fields,
});
const record = (source: string, id: string, fields: Partial<SearchResult> = {}) =>
  withSource(item({ sourceRefs: [{ source, id, url: `https://example.com/${id}` }], ...fields }), source);
const json = (data: unknown, status = 200) => new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } });

function permutations<T>(items: T[]): T[][] {
  if (items.length < 2) return [items];
  return items.flatMap((item, i) => permutations(items.filter((_, j) => i !== j)).map(rest => [item, ...rest]));
}

test('unions complementary metadata, retains conflicting values and gives deterministic output', () => {
  const inputs = [
    record('openlibrary', '/works/dune', { overview: 'Work description', genres: ['Science fiction'] }),
    record('googlebooks', 'gb1', { isbn: '9780441172719', publisher: 'Ace', totalPages: 412, language: 'en', rating: 9,
      releaseDate: '1990-09-01', genres: ['Fiction'], creators: [{ name: 'Frank Herbert', role: 'author' }] }),
    record('apple', 'a1', { coverUrl: 'https://example.com/dune.jpg', overview: 'Another description', rating: 8 }),
  ];
  const output = deduplicateAndMerge(inputs);
  assert.equal(output.length, 1);
  const merged = output[0];
  assert.equal(merged.overview, 'Work description');
  assert.equal(merged.totalPages, 412);
  assert.equal(merged.isbn, '9780441172719');
  assert.equal(merged.publisher, 'Ace');
  assert.equal(merged.coverUrl, 'https://example.com/dune.jpg');
  assert.equal(merged.sourceRefs?.length, 3);
  assert.equal(merged.ratings?.length, 2);
  assert.equal(merged.metadata?.length, 3);
  assert.deepEqual(merged.genres, ['Fiction', 'Science fiction']);
  assert(merged.metadata?.some(m => m.fields.overview === 'Another description'));
  assert(merged.editions?.some(e => e.coverUrl === 'https://example.com/dune.jpg'));
  for (const order of permutations(inputs)) assert.deepEqual(deduplicateAndMerge(order), output);
});

test('preserves volumes, authors, Unicode and differing known work IDs', () => {
  assert.equal(deduplicateAndMerge([item({ title: 'Example Volume 1' }), item({ title: 'Example Volume 2' })]).length, 2);
  assert.equal(deduplicateAndMerge([item({ title: 'Home', creator: 'A' }), item({ title: 'Home', creator: 'B' })]).length, 2);
  assert.equal(deduplicateAndMerge([item({ title: '進撃の巨人' })])[0].title, '進撃の巨人');
  assert.equal(deduplicateAndMerge([record('openlibrary', 'w1'), record('openlibrary', 'w2')]).length, 2);
  assert.equal(deduplicateAndMerge([record('tmdb', 'movie:1', { category: 'MOVIE' }), record('tmdb', 'movie:2', { category: 'MOVIE' })]).length, 2);
});

test('missing years merge only with corroboration and remakes stay distinct', () => {
  assert.equal(deduplicateAndMerge([item(), item({ releaseYear: '' })]).length, 1);
  assert.equal(deduplicateAndMerge([item({ category: 'MOVIE', creator: '', releaseYear: '1984' }), item({ category: 'MOVIE', creator: '', releaseYear: '2021' })]).length, 2);
  assert.equal(deduplicateAndMerge([item({ category: 'MOVIE', creator: '', releaseYear: '', coverUrl: 'https://example.com/a.jpg' }), item({ category: 'MOVIE', creator: '', releaseYear: '2021' })]).length, 2);
});

test('verified crosswalk joins Steam and IGDB despite missing year; DLC stays distinct', () => {
  const igdb = record('igdb', '42', { category: 'GAME', title: 'Portal', creator: 'Valve', entityKind: 'game',
    sourceRefs: [{ source: 'igdb', id: '42', url: '' }, { source: 'steam', id: '400', url: '' }] });
  const steam = record('steam', '400', { category: 'GAME', title: 'Portal', creator: '', releaseYear: '', platforms: ['windows'] });
  const result = deduplicateAndMerge([igdb, steam]);
  assert.equal(result.length, 1);
  assert.deepEqual(result[0].platforms, ['windows']);
  assert.equal(deduplicateAndMerge([igdb, record('steam', '400', { category: 'GAME', entityKind: 'game_dlc' })]).length, 2);
});

test('ISBN normalization validates checksum and equivalent 10/13 identifiers', () => {
  assert.equal(normalizeIsbn('0-441-17271-7'), '9780441172719');
  assert.equal(normalizeIsbn('9780441172710'), '');
  assert.equal(normalizeIsbn('nonsense'), '');
});

test('titles containing years survive; explicit parenthesized year is parsed', () => {
  for (const title of ['2001: A Space Odyssey', '1984', '1917', 'Blade Runner 2049']) assert.equal(normalizeQuery(title).cleanQuery, title);
  assert.equal(normalizeQuery('Dune (1984)').cleanQuery, 'Dune');
  assert.equal(normalizeQuery('Dune (1984)').extractedYear, '1984');
});

test('edition page counts are not combined with another ISBN or publisher', () => {
  const results = deduplicateAndMerge([
    record('googlebooks', 'one', { isbn: '9780441172719', totalPages: 412, publisher: 'Ace' }),
    record('apple', 'two', { isbn: '9780593099322', totalPages: 688, publisher: 'Other' }),
  ]);
  assert.equal(results.length, 1);
  assert.equal(results[0].isbn, '9780441172719');
  assert.equal(results[0].totalPages, 412);
  assert.equal(results[0].editions?.length, 2);
});

test('Goodreads parser supports graph/array structured data and reversed meta attributes', () => {
  const parsed = parseGoodreads(`<meta content='https://example.com/cover.jpg' property='og:image'>
    <script type='application/ld+json'>${JSON.stringify({ '@graph': [{ '@type': 'WebSite' }, { '@type': ['Book'], name: 'Dune', author: { name: 'Frank Herbert' }, isbn: '0441172717', numberOfPages: 412, inLanguage: 'en', aggregateRating: { ratingValue: 4.5, bestRating: 5 } }] })}</script>`, '123');
  assert.equal(parsed?.isbn, '9780441172719');
  assert.equal(parsed?.creator, 'Frank Herbert');
  assert.equal(parsed?.rating, 9);
  assert.equal(parsed?.coverUrl, 'https://example.com/cover.jpg');
  assert.equal(parseGoodreads('<title>Bot check</title>', '123'), null);
});

test('resolver only accepts supported HTTPS hosts and app paths', () => {
  for (const url of ['http://www.goodreads.com/book/show/123', 'https://goodreads.com.evil.test/book/show/123', 'https://127.0.0.1/app/123', 'https://store.steampowered.com/sub/123', 'https://user:pass@goodreads.com/book/show/123']) assert.equal(supportedUrl(url), false);
  assert(supportedUrl('https://www.goodreads.com/book/show/123-dune'));
  assert(supportedUrl('https://store.steampowered.com/app/400/Portal/'));
});

test('Goodreads resolves and enriches an ISBN without guessing candidate zero', async () => {
  globalThis.fetch = async input => {
    const url = String(input);
    if (url.includes('goodreads.com')) return new Response(`<script type="application/ld+json">{"@type":"Book","name":"Dune","author":{"name":"Frank Herbert"},"isbn":"0441172717"}</script>`, { headers: { 'Content-Type': 'text/html' } });
    if (url.includes('googleapis')) return json({ items: [{ id: 'gb', volumeInfo: { title: 'Dune', authors: ['Frank Herbert'], industryIdentifiers: [{ type: 'ISBN_13', identifier: '9780441172719' }], pageCount: 412, publisher: 'Ace', language: 'en', description: 'A desert planet' } }] });
    return json({ docs: [] });
  };
  const result = await resolveUrl('https://www.goodreads.com/book/show/123');
  assert.equal(result.status, 'resolved');
  assert.equal(result.results.length, 1);
  assert.equal(result.results[0].totalPages, 412);
  assert.equal(result.results[0].publisher, 'Ace');
  assert.deepEqual(new Set(result.results[0].sourceRefs?.map(r => r.source)), new Set(['googlebooks', 'goodreads']));
});

test('timeouts cancel the actual provider request; errors and invalid schema remain failures', async () => {
  let aborted = false;
  let started!: () => void;
  const transportStarted = new Promise<void>(resolve => { started = resolve; });
  globalThis.fetch = async (_, init) => new Promise((_, reject) => {
    init?.signal?.addEventListener('abort', () => { aborted = true; reject(new Error('aborted')); }, { once: true });
    started();
  });
  const controller = new AbortController();
  const pending = runProvider('test', async () => { await providerFetch('https://example.com'); return []; }, controller.signal);
  await transportStarted;
  controller.abort();
  assert.equal((await pending).status, 'timeout');
  // Let the transport's cancellation continuation settle.
  await new Promise(resolve => setTimeout(resolve, 0));
  assert(aborted);
  globalThis.fetch = async () => json({ broken: true });
  const schema = await runProvider('openlibrary', async () => { try { await (await providerFetch('https://openlibrary.org/search.json')).json(); } catch {} return []; }, AbortSignal.timeout(100));
  assert.equal(schema.status, 'invalid_response');
});

test('book API returns union even when each provider already has many hits, and caches complete responses', async () => {
  let calls = 0;
  globalThis.fetch = async input => {
    calls++;
    if (String(input).includes('openlibrary')) return json({ docs: Array.from({ length: 6 }, (_, i) => ({ key: `/works/w${i}`, title: `Unionfixture ${i}`, author_name: ['Author'], first_publish_year: 2000, first_sentence: ['Work description'] })) });
    if (String(input).includes('googleapis')) return json({ items: [{ id: 'edition', volumeInfo: { title: 'Unionfixture 0', authors: ['Author'], pageCount: 300, publisher: 'Publisher', language: 'en', imageLinks: { thumbnail: 'https://example.com/cover.jpg' } } }] });
    throw new Error('Unexpected request');
  };
  const response = await app.request('/v1/search?q=Unionfixture&category=BOOK');
  assert.equal(response.status, 200);
  const body = await response.json() as any;
  assert.equal(body.status, 'complete');
  const match = body.results.find((r: any) => r.title === 'Unionfixture 0');
  assert.equal(match.overview, 'Work description');
  assert.equal(match.totalPages, 300);
  assert.equal(match.publisher, 'Publisher');
  assert.equal(match.metadata.length, 2);
  const before = calls;
  const cached = await (await app.request('/v1/search?q=Unionfixture&category=BOOK')).json() as any;
  assert.equal(cached.source, 'cache');
  assert.equal(calls, before);
  assert.deepEqual(cached.results, body.results);
});

test('all-source outage is transient, never negative cached, and invalid filters are rejected', async () => {
  let calls = 0;
  globalThis.fetch = async () => { calls++; throw new Error('offline'); };
  for (let i = 0; i < 2; i++) {
    const before = calls;
    const response = await app.request('/v1/search?q=outagefixture&category=BOOK');
    assert.equal(response.status, 503);
    assert(calls > before);
  }
  for (const suffix of ['category=INVALID', 'category=BOOK&mode=WRONG', 'category=GAME&subFilter=MANGA', 'category=BOOK&year=NaN']) {
    assert.equal((await app.request('/v1/search?q=fixture&' + suffix)).status, 400);
  }
});

test('search recovers through backup providers and explicit recovery skips failed primaries', async () => {
  const urls: string[] = [];
  globalThis.fetch = async input => {
    const url = String(input);
    urls.push(url);
    if (!url.includes('en.wikipedia.org')) throw new Error('primary offline');
    return json({ query: { pages: { '123': { pageid: 123, title: 'Recoveryfixture',
      extract: 'Recoveryfixture is a 2020 video game.', fullurl: 'https://en.wikipedia.org/wiki/Recoveryfixture',
      thumbnail: { source: 'https://upload.wikimedia.org/recovery.jpg' } } } } });
  };
  const normal = await app.request('/v2/search?q=Recoveryfixture&category=GAME');
  assert.equal(normal.status, 200);
  const body = await normal.json() as any;
  assert.equal(body.results[0].title, 'Recoveryfixture');
  assert.equal(body.status, 'partial');
  assert(urls.some(url => url.includes('steampowered.com')));
  urls.length = 0;
  const backup = await app.request('/v2/search?q=Recoveryfixture&category=GAME&strategy=backup');
  assert.equal(backup.status, 200);
  assert.equal((await backup.json() as any).results[0].title, 'Recoveryfixture');
  assert(urls.every(url => url.includes('en.wikipedia.org')));
  assert.equal((await app.request('/v2/search?q=fixture&strategy=invalid')).status, 400);
});

test('healthy no-match is cached without triggering a client-style outage response', async () => {
  globalThis.fetch = async input => String(input).includes('openlibrary') ? json({ docs: [] }) : String(input).includes('googleapis') ? json({ totalItems: 0 }) : json({ results: [] });
  const res = await app.request('/v1/search?q=emptyfixture&category=BOOK');
  assert.equal(res.status, 200);
  const body = await res.json() as any;
  assert.equal(body.status, 'complete');
  assert.deepEqual(body.results, []);
});

test('movie details unite with search metadata by ID and preserve supplied ratings', async () => {
  globalThis.fetch = async input => {
    const url = String(input);
    if (url.includes('/search/movie')) return json({ results: [{ id: 1, title: 'Moviefixture', release_date: '2020-01-01', overview: 'Search description', vote_average: 8 }] });
    if (url.includes('itunes.apple')) return json({ results: [{ trackId: 9, trackName: 'Moviefixture', artistName: 'Director', releaseDate: '2020-01-01', artworkUrl100: 'https://example.com/art.jpg', longDescription: 'Apple description' }] });
    if (url.includes('/movie/1')) return json({ id: 1, title: 'Moviefixture', release_date: '2020-01-01', runtime: 123, genres: [{ name: 'Drama' }], credits: { crew: [{ name: 'Director', job: 'Director' }] }, external_ids: { imdb_id: 'tt123' }, vote_average: 8.5, vote_count: 100 });
    throw new Error('Unexpected request ' + url);
  };
  const response = await aggregateSearch({ query: 'Moviefixture', category: 'MOVIE', mode: 'ALL' }, { TMDB_API_KEY: 'test' });
  assert.equal(response.results.length, 1);
  assert.equal(response.results[0].runtimeMinutes, 123);
  assert.equal(response.results[0].creator, 'Director');
  assert(response.results[0].sourceRefs?.some(r => r.source === 'imdb' && r.id === 'tt123'));
  assert(response.results[0].metadata?.some(m => m.source === 'apple'));
});

test('universal and legacy book searches share richer metadata, identity rules and cache', async () => {
  let calls = 0;
  globalThis.fetch = async input => {
    calls++;
    const url = String(input);
    if (url.includes('openlibrary')) return json({ docs: [{ key: '/works/unified', title: 'Unifiedbookfixture', author_name: ['Author'], first_publish_year: 1999, subject: ['Space opera'], number_of_pages_median: 500 }] });
    return json({ items: [{ id: 'unifiededition', volumeInfo: { title: 'Unifiedbookfixture', authors: ['Author'], pageCount: 350, publisher: 'Publisher', categories: ['Science fiction'], averageRating: 4.5, ratingsCount: 100 } }] });
  };
  const universal = await (await app.request('/v1/search?q=Unifiedbookfixture&category=BOOK')).json() as any;
  assert.equal(universal.results.length, 1);
  const book = universal.results[0].bookMetadata;
  assert.equal(book.totalPages, 350);
  assert.equal(book.estimatedPages, 500);
  assert(book.rawSubjects.includes('Space opera'));
  assert(book.rawCategories.includes('Science fiction'));
  assert.equal(book.recommendationVector, undefined);
  assert.equal(book.suggestedMoods, undefined);
  assert.equal(book.ratingsCount, 100); // Never add overlapping provider rating counts.
  const before = calls;
  const legacy = await (await app.request('/api/v1/books/search?q=Unifiedbookfixture')).json() as any;
  assert.equal(calls, before);
  assert.equal(legacy.source, 'cache');
  const { metadata, sourceRefs, editions, offlineSnapshot, ...legacyFields } = legacy.results[0];
  assert.deepEqual(legacyFields, book);
  assert.equal(metadata.length, 2);
  assert.equal(sourceRefs.length, 2);
});

test('barcode lookup unites exact edition data from all configured book sources', async () => {
  globalThis.fetch = async input => {
    const url = String(input);
    if (url.includes('/api/books?')) return json({ 'ISBN:9780441172719': {
      key: '/books/edition1', title: 'Dune', authors: [{ name: 'Frank Herbert' }], number_of_pages: 412,
      publishers: [{ name: 'Ace' }], subjects: [{ name: 'Space opera' }], publish_date: '1990',
    } });
    if (url.includes('googleapis')) return json({ items: [{ id: 'gbdune', volumeInfo: {
      title: 'Dune', authors: ['Frank Herbert'], industryIdentifiers: [{ type: 'ISBN_13', identifier: '9780441172719' }],
      description: 'A desert planet', averageRating: 4.5, ratingsCount: 20, categories: ['Science fiction'], language: 'en',
    } }] });
    if (url.includes('isbndb')) return json({ book: { title: 'Dune', authors: ['Frank Herbert'], isbn13: '9780441172719', subjects: ['Adventure'], synopsis: 'ISBNdb description' } });
    throw new Error('Unexpected ISBN request ' + url);
  };
  const env = { ISBNDB_API_KEY: 'fixture-key' };
  const isbn = await app.request('/api/v1/books/isbn/9780441172719', undefined, env);
  assert.equal(isbn.status, 200);
  const result = await isbn.json() as any;
  assert.equal(result.book.totalPages, 412);
  assert.equal(result.book.publisher, 'Ace');
  assert.equal(result.book.language, 'en');
  assert.equal(result.book.overview, 'A desert planet');
  assert(result.book.rawSubjects.includes('Adventure'));
  assert.equal(result.book.sourceRefs.length, 3);
  assert.equal(result.book.metadata.length, 3);
  assert.equal((await app.request('/api/v1/books/isbn/9780441172710')).status, 400);
  const universal = await (await app.request('/v1/search?q=9780441172719&category=BOOK', undefined, env)).json() as any;
  assert.equal(universal.results[0].totalPages, 412);
  assert.equal(universal.results[0].bookMetadata.primaryIsbn, '9780441172719');
});

test('reading-speed API validates tracking data and recommendations are not enabled', async () => {
  const speed = await app.request('/api/v1/books/reading-speed', { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ totalPages: 200, currentPage: 100, sessions: [{ durationMinutes: 30, pagesRead: 20 }] }) });
  assert.equal(speed.status, 200);
  assert.equal((await speed.json() as any).estimatedMinutesToFinish, 150);
  assert.equal((await app.request('/api/v1/books/reading-speed', { method: 'POST', body: JSON.stringify({ totalPages: 100, currentPage: 1 }) })).status, 400);
  const recommend = await app.request('/api/v1/books/recommend', { method: 'POST', body: JSON.stringify({ favoriteGenres: ['SCI_FI'], candidateBooks: [
    { id: 'candidate', title: 'Book', authors: ['Author'], primaryAuthor: 'Author', source: 'GOOGLE_BOOKS', rawCategories: ['Science fiction'], rawSubjects: [] },
  ] }) });
  assert.equal(recommend.status, 404);
  assert.equal((await app.request('/api/v1/books/search?q=test&limit=-1')).status, 400);
  assert.equal((await app.request('/api/v1/books/search?q=invalid&type=isbn')).status, 400);
});

test('concurrent equivalent requests coalesce across universal and book API aliases', async () => {
  let calls = 0;
  globalThis.fetch = async input => {
    calls++;
    await new Promise(resolve => setTimeout(resolve, 20));
    return String(input).includes('openlibrary') ? json({ docs: [{ key: '/works/coalesce', title: 'Coalescefixture', author_name: ['Author'] }] }) : json({ totalItems: 0 });
  };
  const responses = await Promise.all(Array.from({ length: 10 }, (_, i) => app.request(i % 2 ? '/v1/search?q=Coalescefixture&category=BOOK' : '/api/v1/books/search?q=Coalescefixture')));
  assert(responses.every(r => r.status === 200));
  assert.equal(calls, 2);
});

test('manga counts retain their units and multilingual aliases across sources', async () => {
  globalThis.fetch = async input => {
    const url = String(input);
    if (url.includes('kitsu')) return json({ data: [{ id: '1', attributes: { canonicalTitle: 'Mangafixture', titles: { en: 'Mangafixture', ja_jp: '漫画作品' }, startDate: '2000-01-01', chapterCount: 120 } }] });
    if (url.includes('jikan')) return json({ data: [{ mal_id: 2, title: 'Mangafixture', title_japanese: '漫画作品', published: { from: '2000-01-01', prop: { from: { year: 2000 } } }, chapters: 120, authors: [{ name: 'Author' }] }] });
    return json({ data: [] });
  };
  const result = await aggregateSearch({ query: 'Mangafixture', category: 'MANGA', mode: 'ALL' }, {});
  assert.equal(result.results.length, 1);
  assert.equal(result.results[0].totalChapters, 120);
  assert.equal(result.results[0].totalPages, 0);
  assert(result.results[0].aliases?.includes('漫画作品'));
  assert.equal(result.results[0].sourceRefs?.length, 3);
});

test('Wikipedia fallback rejects disambiguations, franchises and hardware instead of relabeling them as games', async () => {
  const { searchWikipedia } = await import('../src/providers/boardgames/wikipediaBoardGames');
  globalThis.fetch = async () => json({ query: { pages: {
    1: { pageid: 1, title: 'Portal', extract: 'Portal may refer to many things.', pageprops: { disambiguation: '' } },
    2: { pageid: 2, title: 'Portal (series)', extract: 'Portal is a series of puzzle-platform video games.' },
    3: { pageid: 3, title: 'PlayStation Portal', extract: 'PlayStation Portal is a handheld accessory for a video game console.' },
    4: { pageid: 4, title: 'Portal (video game)', extract: 'Portal is a 2007 puzzle-platform video game.', fullurl: 'https://en.wikipedia.org/wiki/Portal_(video_game)' },
    5: { pageid: 5, title: 'Portal (1986 video game)', extract: 'Portal is a 1986 adventure video game.', fullurl: 'https://en.wikipedia.org/wiki/Portal_(1986_video_game)' },
    6: { pageid: 6, title: 'Portal (film)', extract: 'Portal is a film based on a 2007 video game.' },
    7: { pageid: 7, title: 'Chell (Portal)', extract: 'Chell is a fictional character in the puzzle-platform video game Portal.' },
  } } });
  const result = await runProvider('wikipedia', () => searchWikipedia('Portal', 'GAME'), AbortSignal.timeout(1000));
  assert.deepEqual(result.results.map(r => r.title), ['Portal', 'Portal (1986 video game)']);
  assert.equal(deduplicateAndMerge(result.results.map(r => withSource(r, 'wikipedia'))).length, 2);
});

test('Hardcover supported search recovers the requested author and preserves public metadata', async () => {
  let hardcoverCalls = 0;
  globalThis.fetch = async (input, options) => {
    const url = String(input);
    if (url.includes('hardcover.app')) {
      hardcoverCalls++;
      const request = JSON.parse(String(options?.body));
      assert(!request.query.includes('_ilike'));
      assert(request.query.includes('search('));
      assert.equal(request.variables.query, 'the velvet knife maureen johnson');
      return json({ data: { search: { error: null, results: { hits: [{ document: {
        id: '2355949', slug: 'the-velvet-knife', title: 'The Velvet Knife', author_names: ['Maureen Johnson'],
        description: 'A mystery novel.', pages: 400, release_year: 2026,
        image: { url: 'https://assets.hardcover.app/edition/32615033/cover.jpeg' },
        isbns: ['0063256010', '9780063256019'], genres: ['Mystery'], rating: 3.5, ratings_count: 5,
      } }] } } } });
    }
    if (url.includes('openlibrary.org')) return json({ docs: [{ key: '/works/wrong-author', title: 'The Velvet Knife', author_name: ['Irving Shulman'] }] });
    throw new Error('Other provider unavailable');
  };
  const response = await app.request('/v2/search?q=the%20velvet%20knife%20by%20maureen%20johnson&category=BOOK', {}, { HARDCOVER_API_TOKEN: 'test' });
  assert.equal(response.status, 200);
  const body = await response.json() as any;
  assert.equal(body.results.length, 1);
  const book = body.results[0];
  assert.equal(book.creator, 'Maureen Johnson');
  assert.equal(book.totalPages, 400);
  assert.equal(book.rating, 7);
  assert.equal(book.editions.length, 1);
  assert.equal(book.editions[0].isbn, '9780063256019');
  assert(!book.editions[0].totalPages);
  assert(book.offlineSnapshot.ratings.some((r: any) => r.source === 'hardcover' && r.value === 3.5 && r.scale === 5));
  assert.equal(hardcoverCalls, 1);
});

test('Wikipedia book fallback never returns an author page as a book', async () => {
  const { searchWikipedia } = await import('../src/providers/boardgames/wikipediaBoardGames');
  globalThis.fetch = async () => json({ query: { pages: {
    1: { pageid: 1, title: 'Maureen Johnson', extract: 'Maureen Johnson is an American author of young adult fiction. Her books include Truly Devious.' },
    2: { pageid: 2, title: 'Truly Devious', extract: 'Truly Devious is a 2018 mystery novel by Maureen Johnson.' },
  } } });
  const results = await searchWikipedia('Maureen Johnson', 'BOOK');
  assert.deepEqual(results.map(r => r.title), ['Truly Devious']);
});

test('a book edition without pages still contributes ISBN/publisher/language to a work result', () => {
  const result = deduplicateAndMerge([
    record('openlibrary', '/works/withoutpages', { title: 'No pages', totalPages: 0 }),
    record('googlebooks', 'editionwithoutpages', { title: 'No pages', isbn: '9780441172719', publisher: 'Ace', language: 'en', totalPages: 0 }),
  ]);
  assert.equal(result.length, 1);
  assert.equal(result[0].isbn, '9780441172719');
  assert.equal(result[0].publisher, 'Ace');
  assert.equal(result[0].language, 'en');
  assert.equal(result[0].totalPages, 0);
});

test('game search hides sparse storefront duplicates and non-game products by default', async () => {
  const { visibleGame, gameKind, extraProductTitle } = await import('../src/search/gamePolicy');
  for (const type of ['dlc_addon', 'expansion', 'standalone_expansion', 'bundle', 'compilation', 'demo', 'mod', 'port', 'expanded_game']) {
    const r = record('igdb', type, { category: 'GAME', entityKind: gameKind(type) });
    assert.equal(visibleGame(r), false, type);
    assert.equal(visibleGame(r, true), true, type);
  }
  assert.equal(gameKind('remaster'), 'game');
  assert.equal(extraProductTitle('Demon Souls'), false);
  assert.equal(extraProductTitle('Game - Supporter Pack'), true);
  assert.equal(extraProductTitle('Game Collection'), true);
  assert.equal(extraProductTitle("Elden Ring: Collector's Edition"), true);
  assert.equal(extraProductTitle('Age of Empires II: Definitive Edition'), false);
  assert.equal(extraProductTitle('Elden Ring: Deluxe Edition'), true);
  assert.equal(extraProductTitle('Elden Ring: Launch Edition'), true);
  assert.equal(visibleGame(record('steam', '42', { category: 'GAME', creator: '', releaseYear: '', overview: '' })), false);
  assert.equal(visibleGame(item({ category: 'GAME', creator: '$19.99', overview: '' })), false);
  assert.equal(visibleGame(item({ category: 'GAME', creator: '11 bit studios', overview: 'A game', entityKind: 'game' })), true);
});

test('Steam uses supplied artwork URLs instead of guessing header paths', async () => {
  const { searchSteamStore } = await import('../src/providers/games/steamStore');
  const image = 'https://cdn.example.com/custom/assets/capsule_184x69.jpg?t=123';
  globalThis.fetch = async () => json({ items: [
    { id: 400, name: 'Portal', tiny_image: image, price: { final: 1999 }, type: 'app' },
    { id: 12, name: 'Bundle', type: 'bundle' },
  ] });
  const results = await searchSteamStore('Portal');
  assert.equal(results.length, 1);
  assert.equal(results[0].coverUrl, image);
  assert.equal(results[0].creator, '');
  assert(!JSON.stringify(results).includes('19.99'));
});

test('typed IGDB products and Steam hits converge to a single base game without extras', async () => {
  globalThis.fetch = async input => {
    const url = String(input);
    if (url.includes('oauth2')) return json({ access_token: 'game-test-token', expires_in: 3600 });
    if (url.includes('igdb.com')) return json([
      { id: 12, name: "Gamefixture: Collector's Edition", game_type: { type: 'Main Game' }, summary: 'Merchandise edition' },
      { id: 1, name: 'Gamefixture', game_type: { type: 'Main Game' }, summary: 'The real game', first_release_date: 1_600_000_000,
        external_games: [{ uid: '400', external_game_source: { name: 'Steam' } }], involved_companies: [{ developer: true, company: { name: 'Developer' } }] },
      ...['Expansion', 'DLC Addon', 'Bundle', 'Standalone Expansion', 'Compilation', 'Demo'].map((type, i) => ({ id: i + 2, name: `Gamefixture ${type}`, game_type: { type }, summary: 'Extra content' })),
    ]);
    if (url.includes('storesearch')) return json({ items: [{ id: 400, type: 'app', name: 'Gamefixture', tiny_image: 'https://example.com/tiny.jpg', price: { final: 1999 } }, { id: 401, type: 'app', name: 'Gamefixture - Supporter Pack' }] });
    if (url.includes('appdetails')) return json({ '400': { success: true, data: { steam_appid: 400, name: 'Gamefixture', type: 'game', developers: ['Developer'], short_description: 'Steam description', header_image: 'https://example.com/header.jpg', release_date: { date: 'Sep 13, 2020' } } } });
    throw new Error('Unexpected game request');
  };
  const result = await aggregateSearch({ query: 'Gamefixture', category: 'GAME', mode: 'ALL' }, { TWITCH_CLIENT_ID: 'game-client', TWITCH_CLIENT_SECRET: 'game-secret' });
  assert.equal(result.results.length, 1);
  assert.equal(result.results[0].title, 'Gamefixture');
  assert(result.results[0].sourceRefs?.some(r => r.source === 'igdb'));
  assert(result.results[0].sourceRefs?.some(r => r.source === 'steam' && r.id === '400'));
  assert.equal(result.results[0].creator, 'Developer');
  assert.equal(result.results[0].overview, 'The real game');
});

test('v2 search is the new-engine contract and supports the app decade filter', async () => {
  globalThis.fetch = async input => {
    if (String(input).includes('tvmaze')) return json([
      { show: { id: 1, name: 'V2fixture', premiered: '2005-01-01', summary: 'A show' } },
      { show: { id: 2, name: 'V2fixture Older', premiered: '1995-01-01', summary: 'An older show' } },
    ]);
    throw new Error('Unexpected v2 request');
  };
  const response = await app.request('/v2/search?q=V2fixture&category=TV_SHOW&year=2000s');
  assert.equal(response.status, 200);
  const body = await response.json() as any;
  assert.equal(body.schemaVersion, 2);
  assert.equal(body.results.length, 1);
  assert.equal(body.results[0].releaseYear, '2005');
  assert.equal((await app.request('/v2/resolve', { method: 'POST', body: '{"url":"http://localhost/"}' })).status, 400);
});
