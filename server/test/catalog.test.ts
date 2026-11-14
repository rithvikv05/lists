import assert from 'node:assert/strict';
import { test } from 'node:test';
import { DatabaseSync } from 'node:sqlite';
import { readFileSync } from 'node:fs';
import { CatalogStore } from '../src/catalog/store';
import { deduplicateAndMerge, withSource } from '../src/merger/entityMerger';
import { SearchResult } from '../src/types';
import { rankResults } from '../src/search/engine';

function database() {
  const sql = new DatabaseSync(':memory:');
  sql.exec('PRAGMA foreign_keys=ON');
  sql.exec(readFileSync(new URL('../migrations/0001_catalog.sql', import.meta.url), 'utf8'));
  const prepare = (query: string) => {
    let params: any[] = [];
    const stmt = sql.prepare(query);
    const wrapper = {
      bind(...values: any[]) { params = values; return wrapper; },
      async first() { return stmt.get(...params) || null; },
      async all() { return { results: stmt.all(...params), success: true, meta: {} }; },
      async run() { const result = stmt.run(...params); return { success: true, results: [], meta: { changes: Number(result.changes) } }; },
    };
    return wrapper;
  };
  const db = { prepare, async batch(statements: any[]) {
    sql.exec('BEGIN IMMEDIATE');
    try { const result = []; for (const statement of statements) result.push(await statement.run()); sql.exec('COMMIT'); return result; }
    catch (error) { sql.exec('ROLLBACK'); throw error; }
  } } as unknown as D1Database;
  return { sql, db };
}
function record(source = 'igdb', id = '1', fields: Partial<SearchResult> = {}): SearchResult {
  return withSource({ title: 'Portal', creator: 'Valve', releaseYear: '2007', releaseDate: '2007-10-10',
    coverUrl: 'https://example.com/cover.jpg', overview: 'A puzzle game', rating: 9, totalEpisodes: 0,
    totalPages: 0, category: 'GAME', entityKind: 'game', externalUrl: '',
    sourceRefs: [{ source, id, url: '' }], ...fields }, source);
}

test('catalog assigns durable IDs, unions metadata across sources and retains IDs during ranking', async () => {
  const { sql, db } = database(); const store = new CatalogStore(db);
  const first = await store.reconcile([record()], 'GAME', 'Portal');
  assert.equal(first.state, 'stored');
  assert.match(first.results[0].id!, /^catalog:/);
  const id = first.results[0].id!;
  const next = await store.reconcile([record('steam', '400', { platforms: ['windows'], overview: 'Steam description' })], 'GAME', 'Portal');
  assert.equal(next.state, 'stored');
  assert.equal(next.results[0].id, id);
  assert.deepEqual(next.results[0].platforms, ['windows']);
  assert.equal(next.results[0].overview, 'A puzzle game');
  assert.equal((await store.get(id))?.sourceRefs?.length, 2);
  assert.equal(rankResults(next.results, { category: 'GAME', query: 'Portal', mode: 'ALL' })[0].id, id);
  assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM catalog_entities').get()!.n, 1);
});

test('unchanged metadata is idempotent and fresh source values replace old evidence', async () => {
  const { sql, db } = database(); const store = new CatalogStore(db);
  const first = await store.reconcile([record()], 'GAME');
  const id = first.results[0].id!;
  const before = sql.prepare('SELECT total_changes() AS n').get()!.n;
  await store.reconcile([record()], 'GAME');
  assert.equal(sql.prepare('SELECT total_changes() AS n').get()!.n, before);
  assert.equal((await store.get(id))?.catalogRevision, 1);
  await store.reconcile([record('igdb', '1', { rating: 9.5 })], 'GAME');
  const changed = await store.get(id);
  assert.equal(changed?.rating, 9.5);
  assert.equal(changed?.ratings?.[0].value, 9.5);
  assert.equal(changed?.catalogRevision, 2);
  assert.equal(changed?.metadata?.length, 1);
  assert.equal(sql.prepare('SELECT entity_writes_today AS n FROM catalog_budget').get()!.n, 2);
});

test('different verified works are never collapsed just to reuse a catalog ID', async () => {
  const { db } = database(); const store = new CatalogStore(db);
  const a = await store.reconcile([record()], 'GAME', 'Portal');
  const b = await store.reconcile([record('igdb', '2')], 'GAME', 'Portal');
  assert.notEqual(a.results[0].id, b.results[0].id);
  assert.equal(deduplicateAndMerge([a.results[0], b.results[0]]).length, 2);
});

test('catalog capacity fails gracefully and the atomic batch never leaves orphan mappings', async () => {
  const { sql, db } = database(); const store = new CatalogStore(db);
  sql.exec('UPDATE catalog_budget SET max_entities=1');
  const result = await store.reconcile([record(), record('igdb', '2', { title: 'Other game' })], 'GAME');
  assert.equal(result.state, 'capacity');
  assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM catalog_entities').get()!.n, 0);
  assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM catalog_refs').get()!.n, 0);
  assert.equal(sql.prepare('SELECT entity_count AS n FROM catalog_budget').get()!.n, 0);
  assert.equal(result.results.length, 2); // Live search data survives persistence failure.
});

test('daily write guard preserves existing entities and search remains usable', async () => {
  const { sql, db } = database(); const store = new CatalogStore(db);
  const first = await store.reconcile([record()], 'GAME');
  sql.exec('UPDATE catalog_budget SET max_entity_writes_per_day=1');
  const second = await store.reconcile([record('igdb', '1', { rating: 8 })], 'GAME');
  assert.equal(second.state, 'capacity');
  assert.equal(second.results[0].id, first.results[0].id);
  assert.equal((await store.get(first.results[0].id!))?.rating, 9);
  assert.equal(second.results[0].rating, 8);
});

test('busy cross-isolate lease prevents competing catalog writes', async () => {
  const { sql, db } = database(); const store = new CatalogStore(db);
  sql.exec("INSERT INTO catalog_lock VALUES(1,'other-writer',unixepoch()+20)");
  const result = await store.reconcile([record()], 'GAME');
  assert.equal(result.state, 'busy');
  assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM catalog_entities').get()!.n, 0);
});

test('aggregate completeness counters reflect updates without scanning the catalog', async () => {
  const { db } = database(); const store = new CatalogStore(db);
  await store.reconcile([record('igdb', '1', { genres: ['Puzzle'] })], 'GAME');
  await store.reconcile([record('igdb', '1', { language: 'en' })], 'GAME');
  const coverage: any = await store.coverage();
  assert.equal(coverage.budget.entity_count, 1);
  assert.equal(coverage.categories[0].genres, 1);
  assert.equal(coverage.categories[0].languages, 1);
});

test('catalog can recover known exact-title results during a provider outage', async () => {
  const { db } = database(); const store = new CatalogStore(db);
  const saved = await store.reconcile([record()], 'GAME', 'Portal');
  const fallback = await store.reconcile([], 'GAME', 'Portal');
  assert.equal(fallback.results[0].id, saved.results[0].id);
  assert.equal(fallback.state, 'read_only');
});

test('ISBN context selects the requested edition without changing canonical work identity', async () => {
  const { selectBookEdition } = await import('../src/catalog/bookEdition');
  const { db } = database(); const store = new CatalogStore(db);
  const first = record('googlebooks', 'editionA', { category: 'BOOK', entityKind: undefined, title: 'Dune', creator: 'Frank Herbert',
    isbn: '9780441172719', totalPages: 412, publisher: 'Ace', language: 'en', releaseDate: '1990-01-01' });
  const other = record('googlebooks', 'editionB', { category: 'BOOK', entityKind: undefined, title: 'Dune', creator: 'Frank Herbert',
    isbn: '9780593099322', totalPages: 688, publisher: 'Other', language: 'fr', releaseDate: '2019-01-01' });
  const a = await store.reconcile([first], 'BOOK');
  const b = await store.reconcile([other], 'BOOK');
  assert.equal(a.results[0].id, b.results[0].id);
  const selected = selectBookEdition(b.results[0], '9780593099322');
  assert.equal(selected.isbn, '9780593099322');
  assert.equal(selected.totalPages, 688);
  assert.equal(selected.language, 'fr');
  assert.equal(selected.publisher, 'Other');
  assert.equal(selected.releaseYear, '2019');
  assert.equal(selected.id, a.results[0].id);
});

test('offline snapshots are bounded and contain no raw provider evidence or recommendation features', async () => {
  const { offlineSnapshot } = await import('../src/catalog/offlineSnapshot');
  const snapshot = offlineSnapshot(record('igdb', '1', { overview: 'x'.repeat(10000), genres: Array.from({ length: 40 }, () => 'long genre'.repeat(40)),
    sourceRefs: Array.from({ length: 20 }, (_, i) => ({ source: 'test', id: 'x'.repeat(500) + i, url: 'https://example.com' })) }));
  assert(new TextEncoder().encode(JSON.stringify(snapshot)).byteLength <= 2048);
  assert.equal(snapshot.metadata, undefined);
  assert.equal(snapshot.overview, undefined);
  assert.equal(snapshot.recommendationVector, undefined);
});

test('a full catalog still permits updates to existing entities', async () => {
  const { sql, db } = database(); const store = new CatalogStore(db);
  const first = await store.reconcile([record()], 'GAME');
  sql.exec('UPDATE catalog_budget SET max_entities=1');
  const changed = await store.reconcile([record('igdb', '1', { rating: 8 })], 'GAME');
  assert.equal(changed.state, 'stored');
  assert.equal((await store.get(first.results[0].id!))?.rating, 8);
});
