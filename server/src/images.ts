// A bounded, cached fallback for devices that cannot reach a provider's image CDN.
// Never accept arbitrary destinations or forward client credentials/cookies.
const imageHosts = new Set([
  'images.igdb.com', 'image.tmdb.org', 'covers.openlibrary.org',
  'books.google.com', 'books.googleusercontent.com', 'm.media-amazon.com',
  'cdn.myanimelist.net', 'media.kitsu.app', 'media.kitsu.io',
  'uploads.mangadex.org', 'static.tvmaze.com', 'images-na.ssl-images-amazon.com',
  'images.gr-assets.com', 'i.gr-assets.com', 'cdn.hardcover.app', 'assets.hardcover.app',
  'upload.wikimedia.org', 'thumb.wikimedia.org',
]);
export function allowedImageUrl(value: string): URL | null {
  try {
    const url = new URL(value);
    const host = url.hostname;
    // Open Library moves cover archives to Internet Archive. Restrict these
    // destinations to cover files, rather than allowing arbitrary IA downloads.
    const archiveHost = host === 'archive.org' || /^ia\d+\.(?:us\.)?archive\.org$/.test(host);
    const archiveCover = archiveHost && (
      /^\/(?:download|\d+\/items)\/[sml]_covers_\d+\/[sml]_covers_[\d_]+\.zip\/\d+-[SML]\.jpg$/.test(url.pathname) ||
      (url.pathname === '/view_archive.php' && url.searchParams.size === 2 &&
        /^\/\d+\/items\/[sml]_covers_\d+\/[sml]_covers_[\d_]+\.zip$/.test(url.searchParams.get('archive') || '') &&
        /^\d+-[SML]\.jpg$/.test(url.searchParams.get('file') || '')));
    const allowed = imageHosts.has(host) || archiveCover || /^(?:[a-z0-9-]+\.)*(?:mzstatic\.com|steamstatic\.com|steamcdn-a\.akamaihd\.net)$/.test(host);
    const inventaireCover = host === 'inventaire.io' && /^\/img\/entities\/[a-f0-9]{40}$/.test(url.pathname) && !url.search;
    if ((!allowed && !inventaireCover) || url.protocol !== 'https:' || url.port || url.username || url.password || url.hash) return null;
    if (host === 'books.google.com' && url.pathname !== '/books/content') return null;
    return url;
  } catch { return null; }
}

const MAX_BYTES = 3 * 1024 * 1024;
const imageTypes = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'image/avif']);
export async function fetchCover(value: string, fetcher: typeof fetch = fetch): Promise<Response> {
  let target = allowedImageUrl(value);
  if (!target) return new Response('Unsupported image URL', { status: 400 });
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 8_000);
  try {
    for (let redirects = 0; redirects <= 3; redirects++) {
      const response: Response = await fetcher(target.href, { redirect: 'manual', signal: controller.signal,
        headers: { Accept: 'image/avif,image/webp,image/*', 'User-Agent': 'ListsCover/1.0' } });
      if (response.status >= 300 && response.status < 400) {
        const location: string | null = response.headers.get('location');
        await response.body?.cancel();
        target = location ? allowedImageUrl(new URL(location, target).href) : null;
        if (!target) return new Response('Unsupported image redirect', { status: 502 });
        continue;
      }
      const type = (response.headers.get('content-type') || '').split(';')[0].toLowerCase();
      if (!response.ok || !imageTypes.has(type) || !response.body) {
        await response.body?.cancel();
        return new Response('Cover unavailable', { status: 502 });
      }
      if (Number(response.headers.get('content-length')) > MAX_BYTES) {
        await response.body.cancel();
        return new Response('Cover too large', { status: 502 });
      }
      const reader = response.body.getReader();
      const chunks: Uint8Array[] = [];
      let size = 0;
      for (;;) {
        const { done, value: chunk } = await reader.read();
        if (done) break;
        size += chunk.byteLength;
        if (size > MAX_BYTES) {
          await reader.cancel();
          return new Response('Cover too large', { status: 502 });
        }
        chunks.push(chunk);
      }
      if (!size) return new Response('Empty cover', { status: 502 });
      const bytes = new Uint8Array(size);
      let offset = 0;
      for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
      return new Response(bytes, { headers: {
        'Content-Type': type, 'Cache-Control': 'public, max-age=86400',
        'X-Content-Type-Options': 'nosniff', 'Access-Control-Allow-Origin': '*',
      } });
    }
    return new Response('Too many image redirects', { status: 502 });
  } catch {
    return new Response('Cover temporarily unavailable', { status: 502 });
  } finally { clearTimeout(timer); }
}
