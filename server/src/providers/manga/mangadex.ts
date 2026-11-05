import { SearchResult } from '../../types';

export async function searchMangaDex(query: string, year?: string): Promise<SearchResult[]> {
  try {
    const url = `https://api.mangadex.org/manga?title=${encodeURIComponent(query)}&limit=6&includes[]=cover_art`;
    const res = await fetch(url, {
      signal: AbortSignal.timeout(1800),
      headers: { 'User-Agent': 'ListsEngine/2.0 (media-aggregator)' },
    });
    if (!res.ok) return [];

    const data = await res.json() as {
      data?: Array<{
        id: string;
        attributes: {
          title: Record<string, string>;
          altTitles?: Array<Record<string, string>>;
          description?: Record<string, string>;
          year?: number;
          lastVolume?: string;
          lastChapter?: string;
        };
        relationships?: Array<{
          id: string;
          type: string;
          attributes?: { fileName?: string };
        }>;
      }>;
    };

    if (!data.data || !Array.isArray(data.data)) return [];

    return data.data.map(item => {
      const titles = item.attributes.title || {};
      const title = titles.en || Object.values(titles)[0] || '';
      const releaseYear = item.attributes.year ? String(item.attributes.year) : (year || '');
      const desc = item.attributes.description?.en || Object.values(item.attributes.description || {})[0] || '';

      // Find cover_art relationship
      const coverRel = item.relationships?.find(r => r.type === 'cover_art');
      const fileName = coverRel?.attributes?.fileName;
      const coverUrl = fileName
        ? `https://uploads.mangadex.org/covers/${item.id}/${fileName}.512.jpg`
        : '';

      return {
        title,
        creator: '',
        releaseYear,
        releaseDate: releaseYear,
        coverUrl,
        overview: desc,
        rating: 0,
        totalEpisodes: 0,
        totalPages: 0,
        category: 'MANGA' as const,
        externalUrl: `https://mangadex.org/title/${item.id}`,
      };
    });
  } catch {
    return [];
  }
}

