import { test, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import app from '../src/index';
import { apiShPlaytime, howLongToBeat, igdbPlaytime } from '../src/providers/games/playtime';

const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });
const json = (body: unknown) => new Response(JSON.stringify(body), { headers: { 'Content-Type': 'application/json' } });

test('API.SH converts HLTB hours and preserves missing values', async () => {
  globalThis.fetch = async (input, init) => {
    assert.equal(new URL(String(input)).searchParams.get('q'), 'Elden Ring');
    assert.equal(new Headers(init?.headers).get('x-api-key'), 'test-key');
    return json({ items: [{ id: 68151, name: 'Elden Ring', type: 'game', release_year: 2022,
      main_story_hours: 60.13, main_extra_hours: 101.38, completionist_hours: null }] });
  };
  assert.deepEqual(await apiShPlaytime('Elden Ring', '2022', 'test-key'), { status: 'available', estimate: {
    source: 'howlongtobeat', url: 'https://howlongtobeat.com/game/68151', mainSeconds: 216468,
    sidesSeconds: 364968, completeSeconds: null } });
});

test('API.SH refuses ambiguous remakes and treats quota failures as unavailable', async () => {
  globalThis.fetch = async () => json({ items: [
    { id: 1, name: 'Prey', type: 'game', release_year: 2006, main_story_hours: 9 },
    { id: 2, name: 'Prey', type: 'game', release_year: 2017, main_story_hours: 17 },
  ] });
  assert.deepEqual(await apiShPlaytime('Prey', '', 'test-key'), { status: 'not_found' });
  globalThis.fetch = async () => new Response(null, { status: 402 });
  assert.deepEqual(await apiShPlaytime('Prey', '', 'test-key'), { status: 'unavailable' });
});

test('HLTB extracts estimates from an exact game match', async () => {
  globalThis.fetch = async input => {
    const url = String(input);
    if (url === 'https://howlongtobeat.com/') return new Response('<script src="/_next/static/chunks/main.js"></script>');
    if (url.includes('main.js')) return new Response('fetch("/api/search/site",{method:"POST"})');
    if (url.includes('/init?')) return json({ token: 'token', key: 'hp', val: 'value' });
    if (url.endsWith('/api/search/site')) return json({ data: [{ game_id: 1, game_name: 'Hades', game_type: 'game', release_world: 2020, comp_main: 3600, comp_plus: 7200, comp_100: 14400 }] });
    throw new Error('Unexpected request');
  };
  assert.deepEqual(await howLongToBeat('Hades', '2020', AbortSignal.timeout(1000)), { status: 'available', estimate: { source: 'howlongtobeat', url: 'https://howlongtobeat.com/game/1', mainSeconds: 3600, sidesSeconds: 7200, completeSeconds: 14400 } });
});

test('IGDB returns estimates using verified game ID', async () => {
  globalThis.fetch = async input => {
    const url = String(input);
    if (url.includes('oauth2/token')) return json({ access_token: 'abc', expires_in: 3600 });
    if (url.includes('game_time_to_beats')) return json([{ game_id: 42, hastily: 3600, normally: 7200, completely: 10800 }]);
    throw new Error('Unexpected request');
  };
  assert.deepEqual(await igdbPlaytime('Game', '', '42', 'test-client', 'test-secret'), { status: 'available', estimate: { source: 'igdb', url: 'https://www.igdb.com/games/42', mainSeconds: 3600, sidesSeconds: 7200, completeSeconds: 10800 } });
});

test('API rejects invalid identity parameters', async () => {
  const response = await app.request('/v2/games/playtime?title=Hades&igdbId=abc');
  assert.equal(response.status, 400);
});
