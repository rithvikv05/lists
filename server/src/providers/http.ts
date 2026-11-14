import { AsyncLocalStorage } from 'node:async_hooks';
import { SearchResult } from '../types';
import { ProviderCooldown, providerQuotaKey } from './cooldown';

export type ProviderStatus = 'ok' | 'empty' | 'timeout' | 'rate_limited' | 'unavailable' | 'invalid_response';
export interface ProviderOutcome { source: string; status: ProviderStatus; results: SearchResult[] }
const scopes = new AsyncLocalStorage<{ signal: AbortSignal; failures: ProviderStatus[] }>();
const cooldown = new ProviderCooldown();

/** Observe errors even in legacy adapters that catch failures and return an empty array. */
export async function providerFetch(input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> {
  const scope = scopes.getStore();
  const signals = [scope?.signal, init.signal].filter((s): s is AbortSignal => !!s);
  try {
    const quotaKey = await providerQuotaKey(input, init);
    if (signals.some(signal => signal.aborted)) throw new Error('Provider request cancelled');
    const wait = cooldown.remaining(quotaKey);
    if (wait) {
      scope?.failures.push('rate_limited');
      return new Response(null, { status: 429, headers: { 'Retry-After': String(wait) } });
    }
    const response = await fetch(input, { ...init, signal: signals.length ? AbortSignal.any(signals) : undefined });
    if (response.status === 429) cooldown.record(quotaKey, response.headers.get('Retry-After'));
    if (!response.ok) scope?.failures.push(response.status === 429 ? 'rate_limited' : 'unavailable');
    const readJson = response.json.bind(response);
    response.json = async () => {
      try {
        const body: any = await readJson();
        if (body?.errors?.length) scope?.failures.push('invalid_response');
        if (body?.Response === 'False' && /key|limit|unauthorized/i.test(String(body.Error || ''))) scope?.failures.push('unavailable');
        const url = new URL(typeof input === 'string' || input instanceof URL ? input.toString() : input.url);
        const expected = url.hostname === 'openlibrary.org' && url.pathname === '/search.json' ? body?.docs :
          url.hostname === 'www.googleapis.com' ? (body?.items ?? (body?.totalItems === 0 ? [] : null)) :
          url.hostname === 'itunes.apple.com' ? body?.results :
          url.hostname === 'api.themoviedb.org' && url.pathname.includes('/search/') ? body?.results :
          ['kitsu.io', 'api.jikan.moe'].includes(url.hostname) ? body?.data :
          url.hostname === 'api.igdb.com' ? body :
          url.hostname === 'api.hardcover.app' ? (body?.data?.search ? undefined : body?.data?.books) : undefined;
        if (expected !== undefined && !Array.isArray(expected)) throw new Error('Invalid provider response shape');
        if (expected === undefined && !(url.hostname === 'api.hardcover.app' && body?.data?.search) && (url.hostname !== 'openlibrary.org' || url.pathname === '/search.json') && ['openlibrary.org', 'www.googleapis.com', 'itunes.apple.com', 'kitsu.io', 'api.jikan.moe', 'api.hardcover.app'].includes(url.hostname)) {
          throw new Error('Missing provider result collection');
        }
        return body;
      } catch (error) {
        scope?.failures.push('invalid_response');
        throw error;
      }
    };
    return response;
  } catch (error) {
    scope?.failures.push(signals.some(s => s.aborted) ? 'timeout' : 'unavailable');
    throw error;
  }
}

export async function runProvider(source: string, run: () => Promise<SearchResult[]>, signal: AbortSignal): Promise<ProviderOutcome> {
  const failures: ProviderStatus[] = [];
  return scopes.run({ signal, failures }, async () => {
    let abort: () => void = () => {};
    try {
      // Transport cancellation plus a bound for non-cooperative adapters.
      const deadline = new Promise<SearchResult[]>((_, reject) => {
        abort = () => reject(new Error('deadline'));
        if (signal.aborted) abort();
        else signal.addEventListener('abort', abort, { once: true });
      });
      const results = await Promise.race([run(), deadline]);
      return { source, status: failures[0] || (results.length ? 'ok' : 'empty'), results };
    } catch {
      return { source, status: signal.aborted ? 'timeout' : failures[0] || 'unavailable', results: [] };
    } finally {
      signal.removeEventListener('abort', abort);
    }
  });
}
