import { providerFetch } from './http';

export async function screenInfo(
  kind: 'movie' | 'tv', id: string, title: string, year: string, country: string, token: string,
) {
  const signal = AbortSignal.timeout(4500);
  const request = async (path: string, params: Record<string, string> = {}) => {
    const url = new URL(`https://api.themoviedb.org/3/${path}`);
    const headers: Record<string, string> = { Accept: 'application/json' };
    if (token.length > 40) headers.Authorization = `Bearer ${token}`;
    else url.searchParams.set('api_key', token);
    for (const [key, value] of Object.entries(params)) url.searchParams.set(key, value);
    const response = await providerFetch(url, { headers, signal });
    if (!response.ok) throw new Error('Screen information unavailable');
    return response.json() as Promise<any>;
  };

  if (!id) {
    const normalized = (value: string) => value.toLowerCase().normalize('NFKC').replace(/[^\p{L}\p{N}]+/gu, '');
    const search = await request(`search/${kind}`, { query: title, include_adult: 'false',
      ...(year ? { [kind === 'movie' ? 'primary_release_year' : 'first_air_date_year']: year } : {}) });
    const exact = (search.results || []).filter((entry: any) =>
      [entry.title, entry.original_title, entry.name, entry.original_name].some(value =>
        typeof value === 'string' && normalized(value) === normalized(title)) &&
      (!year || (entry.release_date || entry.first_air_date || '').startsWith(year)));
    if (exact.length !== 1 || search.total_pages > 1 || !Number.isSafeInteger(exact[0].id)) return null;
    id = String(exact[0].id);
  }

  const data = await request(`${kind}/${id}`, kind === 'movie' ? { append_to_response: 'release_dates' } : {});
  if (String(data.id) !== id) throw new Error('Unexpected TMDB identity');
  const releaseTypes = [3, 2, 4, 5, 6, 1];
  function regionalReleases(region: string): { type: number; release_date?: string; certification?: string }[] {
    const dates = kind === 'movie'
      ? data.release_dates?.results?.find((entry: any) => entry.iso_3166_1 === region)?.release_dates || []
      : [];
    return [...dates].sort((a, b) => releaseTypes.indexOf(a.type) - releaseTypes.indexOf(b.type));
  }
  const releases = regionalReleases(country);
  const certification = (region: string) => regionalReleases(region)
    .find(entry => entry.certification?.trim())?.certification?.trim() || '';
  const next = data.next_episode_to_air;
  return {
    status: data.status || '',
    releaseDate: data.release_date || data.first_air_date || '',
    regionalReleaseDate: (releases.find(pair => [2, 3, 4].includes(pair.type))?.release_date || '').slice(0, 10),
    runtimeMinutes: data.runtime || data.episode_run_time?.[0] || 0,
    certification: certification(country),
    indiaCertification: country === 'IN' ? '' : certification('IN'),
    country,
    genres: (data.genres || []).map((genre: any) => genre.name),
    networks: (data.networks || []).map((network: any) => network.name),
    seasons: data.number_of_seasons || 0,
    nextEpisode: next ? { name: next.name || '', airdate: next.air_date || '', season: next.season_number, number: next.episode_number } : null,
  };
}
