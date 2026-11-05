import { SearchResult } from '../../types';

export async function searchHardcover(query: string, apiToken?: string): Promise<SearchResult[]> {
  const results: SearchResult[] = [];
  try {
    const qEscaped = query.trim().replace(/"/g, '\\"');
    const gql = `
      query SearchBooks {
        books(where: {title: {_ilike: "%${qEscaped}%"}}, limit: 10) {
          id
          title
          release_year
          cached_image
          description
          contributions {
            author {
              name
            }
          }
        }
      }
    `;

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      'User-Agent': 'ListsSearchEngine/1.0'
    };
    if (apiToken) {
      headers['Authorization'] = `Bearer ${apiToken}`;
    }

    const res = await fetch('https://api.hardcover.app/v1/graphql', {
      method: 'POST',
      headers,
      body: JSON.stringify({ query: gql }),
      signal: AbortSignal.timeout(2200)
    });

    if (res.ok) {
      const data = await res.json() as any;
      const books = data.data?.books || [];
      for (const b of books) {
        if (!b.title) continue;

        const author = b.contributions?.[0]?.author?.name || '';
        const img = b.cached_image?.url || '';

        results.push({
          title: b.title,
          creator: author,
          releaseYear: b.release_year ? String(b.release_year) : '',
          releaseDate: b.release_year ? String(b.release_year) : '',
          coverUrl: img,
          overview: (b.description || '').replace(/<[^>]*>/g, ''),
          rating: 0,
          totalEpisodes: 0,
          totalPages: 0,
          category: 'BOOK',
          externalUrl: b.id ? `https://hardcover.app/books/${b.id}` : ''
        });
      }
    }
  } catch (_) {}
  return results;
}

