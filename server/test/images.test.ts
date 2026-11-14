import assert from 'node:assert/strict';
import { test } from 'node:test';
import { allowedImageUrl, fetchCover } from '../src/images';

const cover = 'https://images.igdb.com/igdb/image/upload/t_cover_big/co4jni.jpg';
test('cover relay rejects arbitrary destinations, credentials and host lookalikes', () => {
  for (const url of ['http://images.igdb.com/a.jpg', 'https://localhost/a.jpg',
    'https://127.0.0.1/a.jpg', 'https://images.igdb.com.evil.test/a.jpg',
    'https://user:pass@images.igdb.com/a.jpg', 'https://images.igdb.com:8443/a.jpg',
    'https://books.google.com/search?q=hello', 'https://archive.org/download/arbitrary/file.jpg']) {
    assert.equal(allowedImageUrl(url), null, url);
  }
  assert(allowedImageUrl(cover));
});
test('cover relay serves an image with public caching and never forwards credentials', async () => {
  const response = await fetchCover(cover, (async (_url, options) => {
    assert.equal(options?.redirect, 'manual');
    assert.equal(new Headers(options?.headers).get('cookie'), null);
    assert.equal(new Headers(options?.headers).get('authorization'), null);
    return new Response(new Uint8Array([255, 216, 255]), { headers: { 'Content-Type': 'image/jpeg' } });
  }) as typeof fetch);
  assert.equal(response.status, 200);
  assert.equal(response.headers.get('Cache-Control'), 'public, max-age=86400');
  assert.equal(response.headers.get('Access-Control-Allow-Origin'), '*');
  assert.equal((await response.arrayBuffer()).byteLength, 3);
});
test('Open Library cover archive redirects work, unsafe redirects are rejected', async () => {
  const archive = 'https://archive.org/download/l_covers_0011/l_covers_0011_48.zip/0011481354-L.jpg';
  const shard = 'https://ia600505.us.archive.org/view_archive.php?archive=/14/items/l_covers_0011/l_covers_0011_48.zip&file=0011481354-L.jpg';
  assert(allowedImageUrl(archive));
  assert(allowedImageUrl(shard));
  let calls = 0;
  const response = await fetchCover('https://covers.openlibrary.org/b/id/11481354-L.jpg?default=false', (async () => {
    calls++;
    if (calls <= 2) return new Response(null, { status: 302, headers: { location: calls === 1 ? archive : shard } });
    return new Response(new Uint8Array([255, 216, 255]), { headers: { 'Content-Type': 'image/jpeg' } });
  }) as typeof fetch);
  assert.equal(response.status, 200);
  assert.equal(calls, 3);
  const unsafe = await fetchCover(cover, (async () => new Response(null, {
    status: 302, headers: { location: 'https://127.0.0.1/private' },
  })) as typeof fetch);
  assert.equal(unsafe.status, 502);
});
test('relay rejects HTML responses and bounds downloads even without content length', async () => {
  const html = await fetchCover(cover, (async () => new Response('<html>blocked</html>', {
    headers: { 'Content-Type': 'text/html' },
  })) as typeof fetch);
  assert.equal(html.status, 502);
  const oversized = await fetchCover(cover, (async () => new Response(new Uint8Array(3 * 1024 * 1024 + 1), {
    headers: { 'Content-Type': 'image/jpeg' },
  })) as typeof fetch);
  assert.equal(oversized.status, 502);
});
