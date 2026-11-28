import assert from 'node:assert/strict';
import { test } from 'node:test';
import app from '../src/index';
import { calculateRelevance } from '../src/ranker/relevanceScorer';
import { searchOmdbMovies } from '../src/providers/movies/omdbMovie';

test('movie details keep regional certifications separate and use the theatrical date', async (t) => {
  t.mock.method(globalThis, 'fetch', async (input: URL) => {
    assert.equal(input.pathname, '/3/movie/101');
    assert.equal(input.searchParams.get('append_to_response'), 'release_dates');
    return Response.json({
      id: 101, status: 'Released', genres: [{ name: 'Drama' }],
      release_dates: { results: [
        { iso_3166_1: 'US', release_dates: [{ type: 3, certification: 'PG-13', release_date: '2026-11-29T00:00:00Z' }] },
        { iso_3166_1: 'IN', release_dates: [{ type: 3, certification: 'UA', release_date: '2026-12-01T00:00:00Z' }] },
      ] },
    });
  });
  const response = await app.request('/v2/screen-info?kind=movie&id=101&country=US', {}, { TMDB_API_KEY: 'example' });
  assert.equal(response.status, 200);
  const info = await response.json() as Record<string, unknown>;
  assert.equal(info.certification, 'PG-13');
  assert.equal(info.indiaCertification, 'UA');
  assert.equal(info.regionalReleaseDate, '2026-11-29');
});

test('legacy items with ambiguous titles do not receive another film’s details', async (t) => {
  t.mock.method(globalThis, 'fetch', async (input: URL) => {
    assert.equal(input.pathname, '/3/search/movie');
    return Response.json({ total_pages: 1, results: [{ title: 'The Gift', id: 1 }, { title: 'The Gift', id: 2 }] });
  });
  const response = await app.request('/v2/screen-info?kind=movie&title=The%20Gift', {}, { TMDB_API_KEY: 'example' });
  assert.equal(response.status, 404);
});

test('punctuation variants outrank titles that only share the first words', () => {
  const score = (title: string) => calculateRelevance('Mission Impossible', 'Mission Impossible', undefined,
    { title } as Parameters<typeof calculateRelevance>[3]);
  assert(score('Mission: Impossible') > score('Mission Impossible Fallout'));
});

test('OMDb movie search excludes episodes returned despite the upstream type filter', async (t) => {
  t.mock.method(globalThis, 'fetch', async () => Response.json({ Response: 'True', Search: [
    { Title: 'Mission: Impossible', Type: 'movie', Year: '1996' },
    { Title: 'Mission Impossible (1996)', Type: 'episode', Year: '1996' },
  ] }));
  assert.deepEqual((await searchOmdbMovies('Mission Impossible', 'example', '1996')).map(result => result.title),
    ['Mission: Impossible']);
});
