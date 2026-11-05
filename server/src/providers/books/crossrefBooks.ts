import { SearchResult } from '../../types';

export async function searchCrossrefBooks(query: string, year?: string): Promise<SearchResult[]> {
  try {
    const url = `https://api.crossref.org/works?query.bibliographic=${encodeURIComponent(query)}&filter=type:book&rows=5`;
    const res = await fetch(url, {
      signal: AbortSignal.timeout(1800),
      headers: {
        'User-Agent': 'ListsEngine/2.0 (mailto:dev@minimal-lists.com)',
      },
    });
    if (!res.ok) return [];

    const data = await res.json() as {
      message?: {
        items?: Array<{
          title?: string[];
          author?: Array<{ given?: string; family?: string }>;
          published?: { 'date-parts'?: number[][] };
          publisher?: string;
          URL?: string;
          DOI?: string;
          ISBN?: string[];
        }>;
      };
    };

    const items = data.message?.items;
    if (!items || !Array.isArray(items)) return [];

    return items
      .filter(item => item.title && item.title.length > 0)
      .map(item => {
        const title = item.title![0];
        const author = item.author?.map(a => `${a.given || ''} ${a.family || ''}`.trim()).filter(Boolean).join(', ') || item.publisher || '';
        const dateParts = item.published?.['date-parts']?.[0];
        const pubYear = dateParts?.[0] ? String(dateParts[0]) : (year || '');

        let coverUrl = '';
        if (item.ISBN && item.ISBN.length > 0) {
          const isbn = item.ISBN[0].replace(/[^0-9X]/gi, '');
          coverUrl = `https://covers.openlibrary.org/b/isbn/${isbn}-L.jpg`;
        }

        return {
          title,
          creator: author,
          releaseYear: pubYear,
          releaseDate: pubYear,
          coverUrl,
          overview: item.publisher ? `Published by ${item.publisher}` : '',
          rating: 0,
          totalEpisodes: 0,
          totalPages: 0,
          category: 'BOOK' as const,
          externalUrl: item.URL || (item.DOI ? `https://doi.org/${item.DOI}` : ''),
        };
      });
  } catch {
    return [];
  }
}

