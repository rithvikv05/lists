import { SearchResult } from '../../types';

export async function searchAnilistManga(query: string, year?: string): Promise<SearchResult[]> {
  const gqlQuery = `
    query ($search: String) {
      Page(page: 1, perPage: 6) {
        media(search: $search, type: MANGA) {
          id
          title {
            romaji
            english
            native
          }
          startDate {
            year
          }
          coverImage {
            extraLarge
            large
          }
          description(asHtml: false)
          averageScore
          chapters
          volumes
          staff(perPage: 3) {
            nodes {
              name {
                full
              }
            }
          }
          siteUrl
        }
      }
    }
  `;

  try {
    const res = await fetch('https://graphql.anilist.co', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        'User-Agent': 'ListsEngine/2.0 (media-aggregator)',
      },
      body: JSON.stringify({ query: gqlQuery, variables: { search: query } }),
      signal: AbortSignal.timeout(1800),
    });

    if (!res.ok) return [];

    const json = await res.json() as {
      data?: {
        Page?: {
          media?: Array<{
            id: number;
            title: { romaji?: string; english?: string; native?: string };
            startDate?: { year?: number };
            coverImage?: { extraLarge?: string; large?: string };
            description?: string;
            averageScore?: number;
            chapters?: number;
            volumes?: number;
            staff?: { nodes?: Array<{ name: { full: string } }> };
            siteUrl?: string;
          }>;
        };
      };
    };

    const mediaList = json.data?.Page?.media;
    if (!mediaList) return [];

    return mediaList.map(item => {
      const title = item.title.english || item.title.romaji || item.title.native || '';
      const releaseYear = item.startDate?.year ? String(item.startDate.year) : (year || '');
      const coverUrl = item.coverImage?.extraLarge || item.coverImage?.large || '';
      const rawRating = item.averageScore ? item.averageScore / 10 : 0;
      const creator = item.staff?.nodes?.[0]?.name?.full || '';

      return {
        title,
        creator,
        releaseYear,
        releaseDate: releaseYear,
        coverUrl,
        overview: item.description || '',
        rating: Math.round(rawRating * 10) / 10,
        totalEpisodes: 0,
        totalPages: item.chapters || 0,
        category: 'MANGA' as const,
        externalUrl: item.siteUrl || `https://anilist.co/manga/${item.id}`,
      };
    });
  } catch {
    return [];
  }
}
