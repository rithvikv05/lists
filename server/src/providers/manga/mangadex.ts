import { providerFetch } from '../http';
import { SearchResult } from '../../types';

export async function searchMangaDex(query: string, year?: string): Promise<SearchResult[]> {
  try {
    const url = `https://api.mangadex.org/manga?title=${encodeURIComponent(query)}&limit=6&order[relevance]=desc&includes[]=cover_art&includes[]=author&includes[]=artist`;
    const res = await providerFetch(url, {
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
          links?: Record<string, string>;
          originalLanguage?: string;
          status?: string;
          publicationDemographic?: string;
          tags?: Array<{ attributes?: { name?: Record<string, string>; group?: string } }>;
        };
        relationships?: Array<{
          id: string;
          type: string;
          attributes?: { fileName?: string; name?: string };
        }>;
      }>;
    };

    if (!data.data || !Array.isArray(data.data)) return [];

    return data.data.map(item => {
      const titles = item.attributes.title || {};
      const title = titles.en || Object.values(titles)[0] || '';
      const releaseYear = item.attributes.year ? String(item.attributes.year) : '';
      const desc = item.attributes.description?.en || Object.values(item.attributes.description || {})[0] || '';

      // Find cover_art relationship
      const coverRel = item.relationships?.find(r => r.type === 'cover_art');
      const fileName = coverRel?.attributes?.fileName;
      const coverUrl = fileName
        ? `https://uploads.mangadex.org/covers/${item.id}/${fileName}.512.jpg`
        : '';

      return {
        title,
        aliases: [...Object.values(titles), ...(item.attributes.altTitles || []).flatMap(t => Object.values(t))],
        sourceRefs: [{ source: 'mangadex', id: item.id, url: `https://mangadex.org/title/${item.id}` },
          ...(/^\d+$/.test(item.attributes.links?.mal || '') ? [{ source: 'myanimelist', id: `manga:${item.attributes.links!.mal}`, url: `https://myanimelist.net/manga/${item.attributes.links!.mal}` }] : []),
          ...(/^\d+$/.test(item.attributes.links?.kt || '') ? [{ source: 'kitsu', id: `manga:${item.attributes.links!.kt}`, url: `https://kitsu.io/manga/${item.attributes.links!.kt}` }] : []),
        ],
        creators: (item.relationships || []).filter(r => ['author', 'artist'].includes(r.type) && r.attributes?.name)
          .map(r => ({ name: r.attributes!.name!, role: r.type })),
        genres: (item.attributes.tags || []).filter(t => t.attributes?.group === 'genre').map(t => t.attributes?.name?.en || '').filter(Boolean),
        themes: (item.attributes.tags || []).filter(t => t.attributes?.group === 'theme').map(t => t.attributes?.name?.en || '').filter(Boolean),
        demographics: item.attributes.publicationDemographic ? [item.attributes.publicationDemographic] : [],
        language: item.attributes.originalLanguage || '', status: item.attributes.status || '',
        totalChapters: Number(item.attributes.lastChapter) || 0,
        totalVolumes: Number(item.attributes.lastVolume) || 0,
        creator: (item.relationships || []).find(r => r.type === 'author')?.attributes?.name || '',
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

