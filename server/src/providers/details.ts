import { gameKind, extraProductTitle } from '../search/gamePolicy';
import { SearchResult } from '../types';
import { providerFetch } from './http';

const text = (value: unknown): string => typeof value === 'string' ? value.replace(/<[^>]*>/g, '').trim() : '';

export async function tmdbDetails(id: string, category: 'MOVIE' | 'TV_SHOW', token: string, language = 'en'): Promise<SearchResult[]> {
  const kind = category === 'MOVIE' ? 'movie' : 'tv';
  if (!/^\d+$/.test(id)) return [];
  const url = new URL(`https://api.themoviedb.org/3/${kind}/${id}`);
  url.searchParams.set('append_to_response', 'credits,external_ids,alternative_titles');
  url.searchParams.set('language', language);
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (token.length > 40) headers.Authorization = `Bearer ${token}`;
  else url.searchParams.set('api_key', token);
  const response = await providerFetch(url, { headers });
  if (!response.ok) return [];
  const d: any = await response.json();
  if (String(d.id) !== id || !(d.title || d.name)) throw new Error('Invalid TMDB entity');
  const crew = (d.credits?.crew || []).filter((p: any) => ['Director', 'Writer', 'Screenplay'].includes(p.job));
  const creators = [
    ...(d.created_by || []).map((p: any) => ({ name: p.name, role: 'creator' })),
    ...crew.map((p: any) => ({ name: p.name, role: String(p.job).toLowerCase() })),
    ...(d.credits?.cast || []).slice(0, 20).map((p: any) => ({ name: p.name, role: 'actor' })),
  ];
  const date = d.release_date || d.first_air_date || '';
  return [{
    title: d.title || d.name, originalTitle: d.original_title || d.original_name || '',
    creator: creators.filter((p: any) => ['director', 'creator'].includes(p.role)).map((p: any) => p.name).join(', '), creators,
    aliases: (d.alternative_titles?.titles || d.alternative_titles?.results || []).map((t: any) => t.title),
    sourceRefs: [
      { source: 'tmdb', id: `${kind}:${id}`, url: `https://www.themoviedb.org/${kind}/${id}` },
      ...(d.external_ids?.imdb_id ? [{ source: 'imdb', id: d.external_ids.imdb_id, url: `https://www.imdb.com/title/${d.external_ids.imdb_id}/` }] : []),
      ...(category === 'TV_SHOW' && Number.isInteger(d.external_ids?.tvdb_id) && d.external_ids.tvdb_id > 0 ? [{ source: 'tvdb', id: String(d.external_ids.tvdb_id), url: `https://thetvdb.com/?tab=series&id=${d.external_ids.tvdb_id}` }] : []),
    ],
    releaseYear: date.slice(0, 4), releaseDate: date,
    coverUrl: d.poster_path ? `https://image.tmdb.org/t/p/w780${d.poster_path}` : '',
    backdropUrl: d.backdrop_path ? `https://image.tmdb.org/t/p/w1280${d.backdrop_path}` : '',
    overview: text(d.overview), rating: Number(d.vote_average) || 0,
    ratings: d.vote_average ? [{ source: 'tmdb', value: d.vote_average, scale: 10, count: d.vote_count }] : [],
    totalEpisodes: Number(d.number_of_episodes) || 0, totalPages: 0,
    genres: (d.genres || []).map((g: any) => g.name), language: d.original_language || '',
    runtimeMinutes: d.runtime || d.episode_run_time?.[0] || 0, status: d.status || '', category,
    externalUrl: `https://www.themoviedb.org/${kind}/${id}`,
  }];
}

export async function steamDetails(id: string, country = 'US', language = 'english'): Promise<SearchResult[]> {
  if (!/^\d+$/.test(id)) return [];
  const response = await providerFetch(`https://store.steampowered.com/api/appdetails?appids=${id}&cc=${encodeURIComponent(country)}&l=${encodeURIComponent(language)}`);
  if (!response.ok) return [];
  const body: any = await response.json();
  if (!body[id]?.success) return [];
  const d = body[id].data;
  if (!d?.name || String(d.steam_appid) !== id) throw new Error('Invalid Steam entity');
  const date = !d.release_date?.coming_soon ? Date.parse(d.release_date?.date || '') : NaN;
  const iso = Number.isFinite(date) ? new Date(date).toISOString().slice(0, 10) : '';
  const sourceUrl = `https://store.steampowered.com/app/${id}`;
  return [{
    title: d.name, creator: (d.developers || []).join(', '),
    creators: [...(d.developers || []).map((name: string) => ({ name, role: 'developer' })),
      ...(d.publishers || []).map((name: string) => ({ name, role: 'publisher' }))],
    entityKind: extraProductTitle(d.name) ? 'game_extra' : gameKind(d.type) || 'game_unknown',
    sourceRefs: [{ source: 'steam', id, url: sourceUrl }],
    releaseYear: iso.slice(0, 4), releaseDate: iso,
    coverUrl: d.capsule_image || d.header_image || '', backdropUrl: d.background_raw || d.background || '',
    overview: text(d.short_description || d.about_the_game), rating: 0,
    totalEpisodes: 0, totalPages: 0, category: 'GAME', externalUrl: sourceUrl,
    genres: (d.genres || []).map((g: any) => g.description),
    platforms: Object.entries(d.platforms || {}).filter(([, supported]) => supported).map(([name]) => name),
    status: d.release_date?.coming_soon ? 'Coming soon' : 'Released',
  }];
}
