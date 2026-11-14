import { providerFetch } from '../http';
import { SearchResult } from '../../types';

export async function searchComicVine(query: string, year?: string, apiKey?: string): Promise<SearchResult[]> {
  const key = apiKey;
  if (!key) return [];

  try {
    const url = `https://comicvine.gamespot.com/api/search/?api_key=${key}&format=json&resources=volume,issue&query=${encodeURIComponent(query)}&limit=6`;
    const res = await providerFetch(url, {
      signal: AbortSignal.timeout(1800),
      headers: { 'User-Agent': 'ListsEngine/2.0 (media-aggregator)' },
    });
    if (!res.ok) return [];

    const data = await res.json() as {
      results?: Array<{
        id?: number;
        resource_type?: string;
        issue_number?: string;
        cover_date?: string;
        count_of_issues?: number;
        description?: string;
        name?: string;
        volume?: { id?: number; name?: string };
        start_year?: string;
        image?: { super_url?: string; medium_url?: string };
        deck?: string;
        publisher?: { name?: string };
        site_detail_url?: string;
      }>;
    };

    if (!data.results || !Array.isArray(data.results)) return [];

    return data.results.map(item => {
      const isIssue = item.resource_type === 'issue' || item.issue_number !== undefined;
      const title = isIssue ? `${item.volume?.name || item.name || 'Comic'} #${item.issue_number || item.id}` : item.name || '';
      const kind = isIssue ? 'comic_issue' : 'comic_series';
      const releaseYear = item.start_year || item.cover_date?.slice(0, 4) || '';
      const coverUrl = item.image?.super_url || item.image?.medium_url || '';
      const publisher = item.publisher?.name || '';

      return {
        title,
        entityKind: kind,
        publisher,
        totalIssues: item.count_of_issues || 0,
        sourceRefs: item.id ? [{ source: 'comicvine', id: `${kind}:${item.id}`, url: item.site_detail_url || '' }] : [],
        creator: publisher,
        releaseYear,
        releaseDate: item.cover_date || releaseYear,
        coverUrl,
        overview: (item.deck || item.description || '').replace(/<[^>]*>/g, ''),
        rating: 0,
        totalEpisodes: 0,
        totalPages: 0,
        category: 'COMIC' as const,
        externalUrl: item.site_detail_url || '',
      };
    });
  } catch {
    return [];
  }
}
