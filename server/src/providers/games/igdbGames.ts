import { providerFetch } from '../http';
import { gameKind, extraProductTitle } from '../../search/gamePolicy';
import { SearchResult } from '../../types';

let cachedTwitchToken: { clientId: string; token: string; expiresAt: number } | null = null;

async function getTwitchToken(clientId: string, clientSecret: string): Promise<string | null> {
  if (cachedTwitchToken && cachedTwitchToken.clientId === clientId && Date.now() < cachedTwitchToken.expiresAt - 60000) {
    return cachedTwitchToken.token;
  }

  try {
    const url = "https://id.twitch.tv/oauth2/token";
    const res = await providerFetch(url, { method: 'POST', body: new URLSearchParams({ client_id: clientId, client_secret: clientSecret, grant_type: 'client_credentials' }), signal: AbortSignal.timeout(1800) });
    if (!res.ok) return null;

    const data = await res.json() as { access_token?: string; expires_in?: number };
    if (!data.access_token) return null;

    cachedTwitchToken = {
      clientId,
      token: data.access_token,
      expiresAt: Date.now() + (data.expires_in || 3600) * 1000,
    };
    return cachedTwitchToken.token;
  } catch {
    return null;
  }
}

export async function searchIgdbGames(
  query: string,
  year?: string,
  clientId?: string,
  clientSecret?: string
): Promise<SearchResult[]> {
  const cid = clientId;
  const secret = clientSecret;
  if (!cid || !secret) return [];

  const token = await getTwitchToken(cid, secret);
  if (!token) return [];

  try {
    const cleanQ = query.replace(/"/g, '\\"');
    const body = `
      search "${cleanQ}";
      fields name, first_release_date, summary, total_rating, total_rating_count, cover.image_id, involved_companies.company.name, involved_companies.developer, involved_companies.publisher, platforms.name, genres.name, alternative_names.name, external_games.url, external_games.uid, external_games.external_game_source.name, game_type.type, url;
      limit 15;
    `;

    const res = await providerFetch('https://api.igdb.com/v4/games', {
      method: 'POST',
      headers: {
        'Client-ID': cid,
        'Authorization': `Bearer ${token}`,
        'Accept': 'application/json',
      },
      body,
      signal: AbortSignal.timeout(2000),
    });

    if (!res.ok) return [];

    const data = await res.json() as Array<{
      id: number;
      name: string;
      first_release_date?: number;
      summary?: string;
      total_rating?: number;
      total_rating_count?: number;
      platforms?: Array<{ name: string }>;
      genres?: Array<{ name: string }>;
      alternative_names?: Array<{ name: string }>;
      external_games?: Array<{ url?: string; uid?: string; external_game_source?: { name?: string } }>;
      game_type?: { type?: string };
      cover?: { image_id?: string };
      involved_companies?: Array<{ company?: { name?: string }; developer?: boolean; publisher?: boolean }>;
      url?: string;
    }>;

    if (!Array.isArray(data)) return [];

    return data.map(item => {
      let releaseYear = '';
      let releaseDate = '';
      if (item.first_release_date) {
        const d = new Date(item.first_release_date * 1000);
        releaseDate = d.toISOString().slice(0, 10);
        releaseYear = String(d.getFullYear());
      }

      const coverUrl = item.cover?.image_id
        ? `https://images.igdb.com/igdb/image/upload/t_cover_big/${item.cover.image_id}.jpg`
        : '';

      const developer = item.involved_companies?.filter(c => c.developer).map(c => c.company?.name).filter(Boolean).join(', ') || '';
      const rating = item.total_rating ? Math.round(item.total_rating / 10 * 10) / 10 : 0;

      return {
        title: item.name,
        sourceRefs: [
          { source: 'igdb', id: String(item.id), url: item.url || '' },
          ...(item.external_games || []).flatMap(e => {
            const match = e.url?.match(/^https?:\/\/store\.steampowered\.com\/app\/(\d+)(?:\/|$|[?#])/i);
            const id = match?.[1] || (e.external_game_source?.name?.toLowerCase() === 'steam' && /^\d+$/.test(e.uid || '') ? e.uid : undefined);
            return id ? [{ source: 'steam', id, url: `https://store.steampowered.com/app/${id}` }] : [];
          }),
        ],
        entityKind: extraProductTitle(item.name) ? 'game_extra' : gameKind(item.game_type?.type),
        aliases: (item.alternative_names || []).map(n => n.name),
        platforms: (item.platforms || []).map(p => p.name),
        genres: (item.genres || []).map(g => g.name),
        creators: (item.involved_companies || []).flatMap(c => c.company?.name ? [
          ...(c.developer ? [{ name: c.company.name, role: 'developer' }] : []),
          ...(c.publisher ? [{ name: c.company.name, role: 'publisher' }] : []),
        ] : []),
        ratings: item.total_rating ? [{ source: 'igdb', value: item.total_rating, scale: 100, count: item.total_rating_count }] : [],
        creator: developer,
        releaseYear,
        releaseDate,
        coverUrl,
        overview: item.summary || '',
        rating,
        totalEpisodes: 0,
        totalPages: 0,
        category: 'GAME' as const,
        externalUrl: item.url || `https://www.igdb.com/games/${item.id}`,
      };
    });
  } catch {
    return [];
  }
}
