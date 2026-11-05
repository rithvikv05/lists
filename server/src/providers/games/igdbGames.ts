import { SearchResult } from '../../types';

let cachedTwitchToken: { token: string; expiresAt: number } | null = null;

async function getTwitchToken(clientId: string, clientSecret: string): Promise<string | null> {
  if (cachedTwitchToken && Date.now() < cachedTwitchToken.expiresAt - 60000) {
    return cachedTwitchToken.token;
  }

  try {
    const url = `https://id.twitch.tv/oauth2/token?client_id=${clientId}&client_secret=${clientSecret}&grant_type=client_credentials`;
    const res = await fetch(url, { method: 'POST', signal: AbortSignal.timeout(3000) });
    if (!res.ok) return null;

    const data = await res.json() as { access_token?: string; expires_in?: number };
    if (!data.access_token) return null;

    cachedTwitchToken = {
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
      fields name, first_release_date, summary, total_rating, cover.image_id, involved_companies.company.name, url;
      limit 6;
    `;

    const res = await fetch('https://api.igdb.com/v4/games', {
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
      cover?: { image_id?: string };
      involved_companies?: Array<{ company?: { name?: string } }>;
      url?: string;
    }>;

    if (!Array.isArray(data)) return [];

    return data.map(item => {
      let releaseYear = year || '';
      let releaseDate = '';
      if (item.first_release_date) {
        const d = new Date(item.first_release_date * 1000);
        releaseDate = d.toISOString().slice(0, 10);
        releaseYear = String(d.getFullYear());
      }

      const coverUrl = item.cover?.image_id
        ? `https://images.igdb.com/igdb/image/upload/t_cover_big/${item.cover.image_id}.jpg`
        : '';

      const developer = item.involved_companies?.[0]?.company?.name || '';
      const rating = item.total_rating ? Math.round(item.total_rating / 10 * 10) / 10 : 0;

      return {
        title: item.name,
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
