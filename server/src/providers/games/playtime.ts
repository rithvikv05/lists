import { providerFetch } from '../http';
import { getTwitchToken, searchIgdbGames } from './igdbGames';

export type Estimate = { source: 'howlongtobeat' | 'igdb'; url: string; mainSeconds: number | null; sidesSeconds: number | null; completeSeconds: number | null };
type Outcome = { status: 'available'; estimate: Estimate } | { status: 'not_found' | 'unavailable' };
const norm = (s: string) => s.normalize('NFKD').replace(/\p{M}/gu, '').toLowerCase().replace(/[^\p{L}\p{N}]+/gu, ' ').trim();
const duration = (x: unknown): number | null => typeof x === 'number' && Number.isFinite(x) && x > 0 && x < 100_000_000 ? Math.round(x) : null;

/** API.SH is optional; it returns HowLongToBeat's estimates in hours. */
export async function apiShPlaytime(title: string, year: string, apiKey: string): Promise<Outcome> {
  try {
    const url = new URL('https://api.api.sh/howlongtobeat/v1/search');
    url.searchParams.set('q', title);
    url.searchParams.set('modifier', 'hide_dlc');
    const response = await providerFetch(url, { headers: { 'x-api-key': apiKey, Accept: 'application/json' }, signal: AbortSignal.timeout(6000) });
    if (response.status === 404) return { status: 'not_found' };
    if (!response.ok) return { status: 'unavailable' };
    const data = await response.json() as { items?: any[] };
    if (!Array.isArray(data.items)) return { status: 'unavailable' };
    const matches = data.items.filter(g => (!g.type || ['game', 'multi'].includes(String(g.type).toLowerCase())) &&
      [g.name, g.alias].some(n => typeof n === 'string' && n && norm(n) === norm(title)));
    const yearMatches = year ? matches.filter(g => Number(g.release_year) === Number(year)) : [];
    const match = yearMatches.length === 1 ? yearMatches[0] : !yearMatches.length && matches.length === 1 &&
      (!year || !Number(matches[0].release_year) || Number(matches[0].release_year) === Number(year)) ? matches[0] : null;
    if (!match || !/^\d{1,12}$/.test(String(match.id))) return { status: 'not_found' };
    const hours = (value: unknown) => typeof value === 'number' ? duration(value * 3600) : null;
    const estimate: Estimate = { source: 'howlongtobeat', url: `https://howlongtobeat.com/game/${match.id}`,
      mainSeconds: hours(match.main_story_hours), sidesSeconds: hours(match.main_extra_hours), completeSeconds: hours(match.completionist_hours) };
    return estimate.mainSeconds || estimate.sidesSeconds || estimate.completeSeconds ? { status: 'available', estimate } : { status: 'not_found' };
  } catch { return { status: 'unavailable' }; }
}

export async function howLongToBeat(title: string, year: string, signal: AbortSignal): Promise<Outcome> {
  const base = 'https://howlongtobeat.com';
  try {
    const opts = { headers: { Referer: `${base}/`, Accept: '*/*' }, signal };
    const home = await providerFetch(`${base}/`, opts);
    if (!home.ok) return { status: 'unavailable' };
    const html = await home.text();
    const scripts = [...html.matchAll(/<script[^>]+src=["']([^"']*\/_next\/static\/chunks\/[^"']+\.js[^"']*)["']/gi)]
      .map(m => m[1]).filter(path => path.startsWith('/_next/') || path.startsWith(`${base}/_next/`)).slice(0, 16);
    let endpoint = '';
    for (const script of scripts) {
      const response = await providerFetch(new URL(script, base), opts);
      if (!response.ok) continue;
      const code = await response.text();
      endpoint = /fetch\s*\(\s*["'](\/api\/[a-zA-Z0-9_/]+)[^"']*["']\s*,\s*\{[^}]*method\s*:\s*["']POST["']/is.exec(code)?.[1]?.replace(/\/$/, '') || '';
      if (endpoint) break;
    }
    if (!endpoint) return { status: 'unavailable' };
    const authRes = await providerFetch(`${base}${endpoint}/init?t=${Date.now()}`, opts);
    if (!authRes.ok) return { status: 'unavailable' };
    const auth = await authRes.json() as Record<string, unknown>;
    const token = String(auth.token || '');
    const hpKey = String(Object.entries(auth).find(([k]) => /key/i.test(k))?.[1] || '');
    const hpValue = String(Object.entries(auth).find(([k]) => /val/i.test(k))?.[1] || '');
    if (!token || !hpKey || !hpValue || hpKey.length > 100) return { status: 'unavailable' };
    const payload = { searchType: 'games', searchTerms: title.trim().split(/\s+/), searchPage: 1, size: 20,
      searchOptions: { games: { userId: 0, platform: '', sortCategory: 'popular', rangeCategory: 'main', rangeTime: { min: 0, max: 0 }, gameplay: { perspective: '', flow: '', genre: '', difficulty: '' }, rangeYear: { min: '', max: '' }, modifier: 'hide_dlc' }, users: { sortCategory: 'postcount' }, lists: { sortCategory: 'follows' }, filter: '', sort: 0, randomizer: 0 }, useCache: true, [hpKey]: hpValue };
    const search = await providerFetch(`${base}${endpoint}`, { method: 'POST', signal, headers: { 'Content-Type': 'application/json', Referer: `${base}/`, Origin: base, 'x-auth-token': token, 'x-hp-key': hpKey, 'x-hp-val': hpValue }, body: JSON.stringify(payload) });
    if (!search.ok) return { status: 'unavailable' };
    const data = await search.json() as { data?: any[] };
    if (!Array.isArray(data.data)) return { status: 'unavailable' };
    const matches = data.data.filter(g => (!g.game_type || String(g.game_type).toLowerCase() === 'game') && [g.game_name, g.game_alias].some(n => typeof n === 'string' && n && norm(n) === norm(title)));
    const yearMatches = year ? matches.filter(g => Number(g.release_world) === Number(year)) : [];
    const match = yearMatches.length === 1 ? yearMatches[0] : !yearMatches.length && matches.length === 1 && (!year || !Number(matches[0].release_world) || Number(matches[0].release_world) === Number(year)) ? matches[0] : null;
    if (!match || !/^\d+$/.test(String(match.game_id))) return { status: 'not_found' };
    const estimate: Estimate = { source: 'howlongtobeat', url: `${base}/game/${match.game_id}`, mainSeconds: duration(match.comp_main), sidesSeconds: duration(match.comp_plus), completeSeconds: duration(match.comp_100) };
    return estimate.mainSeconds || estimate.sidesSeconds || estimate.completeSeconds ? { status: 'available', estimate } : { status: 'not_found' };
  } catch { return { status: 'unavailable' }; }
}

export async function igdbPlaytime(title: string, year: string, id: string, clientId?: string, secret?: string): Promise<Outcome> {
  if (!clientId || !secret) return { status: 'unavailable' };
  try {
    let gameId = id;
    if (!gameId) {
      const games = await searchIgdbGames(title, undefined, clientId, secret);
      const exact = games.filter(g => norm(g.title) === norm(title) && g.entityKind === 'game');
      const yearMatches = year ? exact.filter(g => g.releaseYear === year) : [];
      const match = yearMatches.length === 1 ? yearMatches[0] : !yearMatches.length && exact.length === 1 && (!year || !exact[0].releaseYear || exact[0].releaseYear === year) ? exact[0] : undefined;
      gameId = match?.sourceRefs?.find(ref => ref.source === 'igdb')?.id || '';
      if (!gameId) return { status: 'not_found' };
    }
    if (!/^\d{1,12}$/.test(gameId)) return { status: 'not_found' };
    const token = await getTwitchToken(clientId, secret);
    if (!token) return { status: 'unavailable' };
    const res = await providerFetch('https://api.igdb.com/v4/game_time_to_beats', { method: 'POST', headers: { 'Client-ID': clientId, Authorization: `Bearer ${token}`, Accept: 'application/json' }, body: `fields game_id,hastily,normally,completely,count; where game_id = ${gameId}; limit 1;`, signal: AbortSignal.timeout(3500) });
    if (!res.ok) return { status: 'unavailable' };
    const entries = await res.json() as any[];
    if (!Array.isArray(entries)) return { status: 'unavailable' };
    const game = entries[0];
    if (!game) return { status: 'not_found' };
    const estimate: Estimate = { source: 'igdb', url: `https://www.igdb.com/games/${gameId}`, mainSeconds: duration(game.hastily), sidesSeconds: duration(game.normally), completeSeconds: duration(game.completely) };
    return estimate.mainSeconds || estimate.sidesSeconds || estimate.completeSeconds ? { status: 'available', estimate } : { status: 'not_found' };
  } catch { return { status: 'unavailable' }; }
}

export async function gamePlaytime(title: string, year: string, id: string, clientId?: string, secret?: string, apiShKey?: string): Promise<Outcome> {
  if (apiShKey) {
    const apiSh = await apiShPlaytime(title, year, apiShKey);
    if (apiSh.status === 'available') return apiSh;
  }
  const [hltb, igdb] = await Promise.all([
    howLongToBeat(title, year, AbortSignal.timeout(3500)), igdbPlaytime(title, year, id, clientId, secret),
  ]);
  if (hltb.status === 'available') return hltb;
  if (igdb.status === 'available') return igdb;
  return hltb.status === 'not_found' && igdb.status === 'not_found' ? { status: 'not_found' } : { status: 'unavailable' };
}
