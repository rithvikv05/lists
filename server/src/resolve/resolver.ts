import { Parser } from 'htmlparser2';
import { SearchResult } from '../types';
import { deduplicateAndMerge, normalizeIsbn, withSource } from '../merger/entityMerger';
import { providerFetch, runProvider } from '../providers/http';
import { steamDetails } from '../providers/details';
import { searchGoogleBooks } from '../providers/books/googleBooks';
import { searchOpenLibrary } from '../providers/books/openLibrary';
import { toCleanKey } from '../normalizer/canonicalTitle';

export interface Resolution { status: 'resolved' | 'partial' | 'needs_input'; results: SearchResult[]; candidates: SearchResult[]; incompleteSources: string[] }
export function supportedUrl(raw: string): boolean {
  try {
    const u = new URL(raw);
    return u.protocol === 'https:' && !u.port && !u.username && !u.password &&
      ((['goodreads.com', 'www.goodreads.com'].includes(u.hostname) && /^\/book\/show\/\d+/.test(u.pathname)) ||
      (u.hostname === 'store.steampowered.com' && /^\/app\/\d+(?:\/|$)/.test(u.pathname)));
  } catch { return false; }
}

async function goodreadsPage(url: URL): Promise<string> {
  for (let redirects = 0; redirects <= 3; redirects++) {
    if (!['goodreads.com', 'www.goodreads.com'].includes(url.hostname) || url.protocol !== 'https:' || url.port || url.username || url.password) throw new Error('Unsupported redirect');
    const response = await providerFetch(url, { redirect: 'manual', headers: { Accept: 'text/html', 'User-Agent': 'ListsMetadata/2.0' } });
    if (response.status >= 300 && response.status < 400) {
      const location = response.headers.get('location');
      await response.body?.cancel();
      if (!location) throw new Error('Invalid redirect');
      url = new URL(location, url);
      continue;
    }
    if (!response.ok || !(response.headers.get('content-type') || '').includes('text/html')) throw new Error('Page unavailable');
    const reader = response.body?.getReader();
    if (!reader) return '';
    let size = 0, html = '';
    const decoder = new TextDecoder();
    try {
      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        size += value.byteLength;
        if (size > 1_500_000) throw new Error('Page too large');
        html += decoder.decode(value, { stream: true });
      }
      return html + decoder.decode();
    } finally { await reader.cancel(); }
  }
  throw new Error('Too many redirects');
}

/** Structured data is parsed without executing page scripts or trusting meta-tag order. */
export function parseGoodreads(html: string, id: string): SearchResult | null {
  const scripts: string[] = [];
  const meta: Record<string, string> = {};
  let inJson = false, current = '';
  new Parser({
    onopentag(name, attrs) {
      if (name === 'script' && attrs.type?.toLowerCase() === 'application/ld+json') { inJson = true; current = ''; }
      if (name === 'meta' && (attrs.property || attrs.name) && attrs.content) meta[attrs.property || attrs.name] = attrs.content;
    },
    ontext(value) { if (inJson) current += value; },
    onclosetag(name) { if (name === 'script' && inJson) { scripts.push(current); inJson = false; } },
  }, { decodeEntities: true }).end(html);
  const nodes: any[] = [];
  const visit = (value: any, depth = 0) => {
    if (!value || depth > 8 || nodes.length > 200) return;
    if (Array.isArray(value)) { value.slice(0, 200).forEach(v => visit(v, depth + 1)); return; }
    if (typeof value !== 'object') return;
    if ([value['@type']].flat().includes('Book')) nodes.push(value);
    if (value['@graph']) visit(value['@graph'], depth + 1);
  };
  for (const script of scripts) try { visit(JSON.parse(script)); } catch { /* Bad optional structured block. */ }
  const d = nodes[0] || {};
  const title = typeof d.name === 'string' ? d.name : meta['og:title'] || '';
  if (!title || /captcha|robot|access denied/i.test(title)) return null;
  const authors = [d.author || []].flat().map((a: any) => typeof a === 'string' ? a : a?.name).filter((n: unknown): n is string => typeof n === 'string');
  const image = [d.image || []].flat()[0];
  const cover = typeof image === 'string' ? image : image?.url || meta['og:image'] || '';
  const isbn = normalizeIsbn(String(d.isbn || ''));
  const url = `https://www.goodreads.com/book/show/${id}`;
  const rating = Number(d.aggregateRating?.ratingValue) || 0;
  const scale = Number(d.aggregateRating?.bestRating) || 5;
  return {
    title, creator: authors.join(', '), creators: authors.map((name: string) => ({ name, role: 'author' })),
    releaseDate: typeof d.datePublished === 'string' ? d.datePublished : '', releaseYear: String(d.datePublished || '').slice(0, 4),
    coverUrl: cover, overview: String(d.description || meta['og:description'] || '').replace(/<[^>]*>/g, ''),
    isbn, language: typeof d.inLanguage === 'string' ? d.inLanguage : '',
    publisher: typeof d.publisher === 'string' ? d.publisher : d.publisher?.name || '',
    rating: scale > 0 ? Math.min(10, rating / scale * 10) : 0,
    ratings: rating ? [{ source: 'goodreads', value: rating, scale, count: Number(d.aggregateRating?.ratingCount) || undefined }] : [],
    totalPages: Number(d.numberOfPages) || 0, totalEpisodes: 0, category: 'BOOK', externalUrl: url,
    sourceRefs: [{ source: 'goodreads', id, url }],
  };
}

export async function resolveUrl(raw: string, country = 'US'): Promise<Resolution> {
  if (!supportedUrl(raw)) throw new Error('Unsupported URL');
  const url = new URL(raw);
  const signal = AbortSignal.timeout(3000);
  if (url.hostname === 'store.steampowered.com') {
    const outcome = await runProvider('steam', () => steamDetails(url.pathname.split('/')[2], country), signal);
    const results = deduplicateAndMerge(outcome.results.map(r => withSource(r, 'steam')));
    return { status: results.length ? 'resolved' : 'needs_input', results, candidates: [], incompleteSources: outcome.status === 'ok' ? [] : ['steam'] };
  }
  const id = url.pathname.match(/^\/book\/show\/(\d+)/)![1];
  const page = await runProvider('goodreads', async () => {
    const book = parseGoodreads(await goodreadsPage(new URL(`https://www.goodreads.com/book/show/${id}`)), id);
    return book ? [book] : [];
  }, AbortSignal.any([signal, AbortSignal.timeout(1400)]));
  const imported = page.results[0];
  if (!imported) return { status: 'needs_input', results: [], candidates: [], incompleteSources: ['goodreads'] };
  const query = imported.isbn ? `isbn:${imported.isbn}` : `${imported.title} ${imported.creator}`;
  const outcomes = await Promise.all([
    runProvider('googlebooks', () => searchGoogleBooks(query), signal),
    runProvider('openlibrary', () => searchOpenLibrary(imported.isbn || query), signal),
  ]);
  const candidates = outcomes.flatMap(o => o.results.map(r => withSource(r, o.source)));
  const verified = candidates.filter(r =>
    (imported.isbn && normalizeIsbn(r.isbn || '') === imported.isbn) ||
    (imported.creator && r.creator && toCleanKey(r.title) === toCleanKey(imported.title) && toCleanKey(r.creator) === toCleanKey(imported.creator)));
  const results = deduplicateAndMerge([withSource(imported, 'goodreads'), ...verified]);
  // Exact ISBN evidence may merge different displayed edition titles; unresolved alternatives stay explicit.
  return { status: verified.length ? 'resolved' : 'partial', results,
    candidates: verified.length ? [] : deduplicateAndMerge(candidates).slice(0, 5),
    incompleteSources: outcomes.filter(o => !['ok', 'empty'].includes(o.status)).map(o => o.source) };
}
