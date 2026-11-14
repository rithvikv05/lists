/** Per-isolate, bounded HTTP 429 backoff. No persistent quota/storage service. */
export class ProviderCooldown {
  private readonly entries = new Map<string, number>();
  remaining(key: string, now = Date.now()): number {
    const until = this.entries.get(key) || 0;
    if (until <= now) { this.entries.delete(key); return 0; }
    return Math.ceil((until - now) / 1000);
  }
  record(key: string, retryAfter: string | null, now = Date.now()): void {
    const seconds = retryAfter && /^\d+$/.test(retryAfter) ? Number(retryAfter) :
      retryAfter ? (Date.parse(retryAfter) - now) / 1000 : 60;
    const bounded = Number.isFinite(seconds) ? Math.max(1, Math.min(seconds, 900)) : 60;
    this.entries.delete(key);
    this.entries.set(key, now + bounded * 1000);
    while (this.entries.size > 128) this.entries.delete(this.entries.keys().next().value!);
  }
}

export async function providerQuotaKey(input: RequestInfo | URL, init: RequestInit): Promise<string> {
  const url = new URL(typeof input === 'string' || input instanceof URL ? input.toString() : input.url);
  const headers = new Headers(init.headers || (input instanceof Request ? input.headers : undefined));
  // Include credential identity so one key's exhausted quota cannot disable
  // another. Store only the hash, never credentials or a user's search text.
  const identity = JSON.stringify([url.hostname, headers.get('Authorization') || '', headers.get('Client-ID') || '',
    init.body instanceof URLSearchParams ? init.body.get('client_id') || '' : '',
    ...['key', 'api_key', 'apikey', 'token'].map(key => url.searchParams.get(key) || '')]);
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(identity));
  return [...new Uint8Array(digest)].map(n => n.toString(16).padStart(2, '0')).join('');
}
