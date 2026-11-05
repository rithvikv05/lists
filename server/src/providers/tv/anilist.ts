import { SearchResult } from '../../types';

export async function searchAniList(
  query: string,
  type: 'ANIME' | 'MANGA' = 'ANIME',
  year?: string
): Promise<SearchResult[]> {
  const results: SearchResult[] = [];
  try {
    const gql = `
      query (${'$'}search: String, ${'$'}type: MediaType) {
        Page(page: 1, perPage: 25) {
          media(search: ${'$'}search, type: ${'$'}type, sort: SEARCH_MATCH) {
            id
            title {
              english
              romaji
              native
            }
            startDate {
              year
              month
              day
            }
            coverImage {
              extraLarge
              large
            }
            description
            averageScore
            episodes
            chapters
            volumes
            studios(isMain: true) {
              nodes {
                name
              }
            }
            siteUrl
          }
        }
      }
    `;

    const res = await fetch('https://graphql.anilist.co', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'User-Agent': 'ListsSearchEngine/1.0'
      },
      body: JSON.stringify({
        query: gql,
        variables: { search: query.trim(), type }
      }),
      signal: AbortSignal.timeout(2200)
    });

    if (res.ok) {
      const data = await res.json() as any;
      const mediaList = data.data?.Page?.media || [];
      for (const m of mediaList) {
        const title = m.title.english || m.title.romaji || m.title.native;
        if (!title) continue;

        const startYear = m.startDate?.year ? String(m.startDate.year) : '';
        if (year && startYear && startYear !== year) continue;

        const cover = m.coverImage?.extraLarge || m.coverImage?.large || '';
        const studio = m.studios?.nodes?.[0]?.name || '';
        const overview = (m.description || '').replace(/<[^>]*>/g, '');
        const score = m.averageScore ? m.averageScore / 10 : 0;

        results.push({
          title,
          creator: studio,
          releaseYear: startYear,
          releaseDate: startYear,
          coverUrl: cover,
          overview,
          rating: score,
          totalEpisodes: m.episodes || 0,
          totalPages: m.chapters || m.volumes || 0,
          category: type === 'ANIME' ? 'ANIME' : 'MANGA',
          externalUrl: m.siteUrl || `https://anilist.co/${type.toLowerCase()}/${m.id}`
        });
      }
    }
  } catch (_) {}
  return results;
}

