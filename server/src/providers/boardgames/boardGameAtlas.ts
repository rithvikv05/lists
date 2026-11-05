import { SearchResult } from '../../types';

export async function searchBoardGameAtlas(query: string, year?: string): Promise<SearchResult[]> {
  try {
    const url = `https://api.boardgameatlas.com/api/search?name=${encodeURIComponent(query)}&limit=6&client_id=JLBr5npPhV`;
    const res = await fetch(url, {
      signal: AbortSignal.timeout(1800),
      headers: { 'User-Agent': 'ListsEngine/2.0 (media-aggregator)' },
    });
    if (!res.ok) return [];

    const data = await res.json() as {
      games?: Array<{
        id: string;
        name: string;
        year_published?: number;
        image_url?: string;
        thumb_url?: string;
        description_preview?: string;
        average_user_rating?: number;
        primary_publisher?: { name?: string };
        url?: string;
      }>;
    };

    if (!data.games || !Array.isArray(data.games)) return [];

    return data.games.map(game => {
      const releaseYear = game.year_published ? String(game.year_published) : (year || '');
      const coverUrl = game.image_url || game.thumb_url || '';
      const rating = game.average_user_rating ? Math.round(game.average_user_rating * 2 * 10) / 10 : 0;

      return {
        title: game.name,
        creator: game.primary_publisher?.name || '',
        releaseYear,
        releaseDate: releaseYear,
        coverUrl,
        overview: game.description_preview || '',
        rating,
        totalEpisodes: 0,
        totalPages: 0,
        category: 'BOARD_GAME' as const,
        externalUrl: game.url || `https://www.boardgameatlas.com/game/${game.id}`,
      };
    });
  } catch {
    return [];
  }
}
