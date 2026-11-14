# Lists unified metadata engine

One Cloudflare Worker serves media search, rich book search, ISBN lookup, link resolution, reading-speed calculations. The former `books/cloudflare-engine` entry imports this same implementation; it is a compatibility deployment, not a second engine.

## Development and validation

```sh
cd server
npm ci
npm run dev
npm run build
npm test
npx wrangler deploy --dry-run
```

Use `.dev.vars.example` for local configuration. Store production credentials with `wrangler secret put NAME`. `npm run deploy` publishes the Worker; build/test commands do not deploy it. This is a Worker entry; `tsx src/index.ts` is not a standalone Node HTTP server.

## Search contract

`GET /v2/search?q=Dune&category=BOOK&mode=TITLE&lang=en&country=IN`

The test APK targets `/v2`; `/v1` routes remain compatibility aliases. With an engine URL configured, search failures are shown to the user instead of silently invoking the old embedded providers.

Categories: `MOVIE`, `TV_SHOW`, `ANIME`, `BOOK`, `MANGA`, `COMIC`, `GAME`, `BOARD_GAME`, `PODCAST`, `MUSIC`. `mode` is `ALL`, `TITLE`, or `AUTHOR`. Optional explicit `year` filters release/edition evidence. Only a trailing parenthesized year is automatically parsed: `Dune (1984)`; literal titles such as `1984` or `Blade Runner 2049` are preserved.

The response keeps existing app fields and adds `schemaVersion: 2`, `status`, `incompleteSources`, and enriched fields on each result:

- `id`: a stable `catalog:...` ID for persisted entities when optional D1 storage is enabled; otherwise a provider-derived ID. All verified `sourceRefs` remain available. `catalogRevision` tracks persisted metadata changes.
- `originalTitle`, `aliases`, `backdropUrl`, `creators` with roles, `genres`, `platforms`, `runtimeMinutes`, `status`, `isbn`, `publisher`, and `language` when supplied.
- Separate `totalPages`, `totalEpisodes`, `totalChapters`, `totalVolumes` and `totalIssues`.
- `ratings`: attributed source values/scales/counts rather than a fabricated average.
- `editions`: source-scoped book edition fields. ISBN, publisher, language, pages and edition date stay together. Work-level median pages are labeled `bookMetadata.estimatedPages`, not silently used as exact pages.
- `metadata`: source-attributed supplied fields, including alternative/conflicting values.
- `bookMetadata`: richer book fields, raw subjects/categories and bibliographic fields. Recommendation features are not computed during search.

Matching uses verified provider IDs/crosswalks first, then conservative title/alias, creator, kind and date evidence. It preserves native scripts and volume/part numbers. Incompatible records stay separate. Scalar fields use deterministic provider preference, arrays are united, and alternative values retain provenance. No metadata is invented when a source lacks it.

Discovery providers run in parallel with a shared deadline. The top three eligible movie/TV/Steam results receive ID-based detail enrichment within the same three-second budget. Other cards contain the combined discovery metadata available for their entities. Requests for the same search are coalesced within an isolate; the bounded LRU cache holds complete results for ten minutes and healthy no-matches for thirty seconds. Partial results/outages are not negative-cached.

Healthy no-match: HTTP 200 with empty results. Some sources unavailable: HTTP 200 with `status: partial` if usable results exist. No trustworthy results during failure: HTTP 503 with retry guidance. Invalid filters: HTTP 400. `complete` describes the configured search plan, not exhaustive global coverage.

## Unified book endpoints

These existing paths now use the universal merger, providers, transport and cache:

| Endpoint | Behavior |
| --- | --- |
| `GET /api/v1/books/search?q=...&type=all&lang=en&limit=20` | Rich book DTOs; `type` supports `all`, `title`, `author`, `isbn` |
| `GET /api/v1/books/isbn/9780441172719` | Checksum-validated exact lookup combining Google Books, Open Library and optional ISBNdb |
| `POST /api/v1/books/reading-speed` | Existing reading-session EWMA/ETA calculator |

ISBN-shaped queries also work through universal search. Exact ISBN lookup checks returned identifiers; it does not choose an unverified first search hit. The old book merger, which could combine missing-author matches and sum overlapping rating counts, has been replaced.

The maintained book domain lives in `server/src/books`. Old TypeScript module locations under `books/cloudflare-engine/src` re-export it for compatibility. The standalone Kotlin files under `books/engine` remain legacy reference code; they are not another server runtime.

## Link imports and enrichment

`POST /v1/resolve` with `{"url":"https://www.goodreads.com/book/show/123","country":"IN"}`. Supported Goodreads and Steam links can also be pasted as search queries.

Goodreads: extract available structured `Book` metadata, validate ISBNs, and enrich verified matches using book catalogs. Sparse or blocked pages return `partial`/`needs_input`; ambiguous candidates remain explicit. A Goodreads link does not turn another catalog's rating into a Goodreads rating.

Steam: resolve the exact App ID and fetch available storefront details. Game search also preserves IGDB-to-Steam crosswalks. Pricing is never placed in creator fields. Packages/bundles and arbitrary URLs are not accepted by this resolver.

`GET /v1/enrich?source=tvmaze&id=123&category=TV_SHOW` fetches episodes by ID. `source=tmdb` with a movie/TV ID and `country=IN` returns real regional watch-provider data, with JustWatch attribution. URLs point to the supplied TMDB availability page; generic streaming search links are not represented as verified offers. Title-only enrichment is rejected to prevent wrong-show matches.

## Providers and credentials

`GET /health` reports enabled discovery providers. Optional credentials:

- `TMDB_API_KEY` or `TMDB_READ_ACCESS_TOKEN`
- `OMDB_API_KEY`
- `TWITCH_CLIENT_ID` + `TWITCH_CLIENT_SECRET` (or `IGDB_CLIENT_ID` + `IGDB_CLIENT_SECRET`)
- `HARDCOVER_API_TOKEN`
- `GOOGLE_BOOKS_API_KEY`
- `ISBNDB_API_KEY`
- `COMIC_VINE_API_KEY`
- `BGG_API_TOKEN`

AniList is disabled unless `ENABLE_ANILIST=true`; confirm the service's eligibility under its list/tracker restrictions before enabling it. BGG requires registered bearer authorization. Unverified Cataloggd/GCD mappings, GetComics posts, book catalogs mislabeled as board games, and fixed six-provider fan-outs are removed from default discovery. Wikipedia fallback filters media type and preserves identity-bearing disambiguators.

Movie/TV data uses [TMDB](https://www.themoviedb.org/) and [TVmaze](https://www.tvmaze.com/). Watch-provider data is supplied by [JustWatch via TMDB](https://developer.themoviedb.org/reference/movie-watch-providers). Book catalogs include [Open Library](https://openlibrary.org/), [Google Books](https://books.google.com/), [Hardcover](https://hardcover.app/) and optional [ISBNdb](https://isbndb.com/). Games use [IGDB](https://www.igdb.com/) and Steam. This product uses the TMDB API but is not endorsed or certified by TMDB. Respect each provider's usage, caching and attribution requirements.

## Clients and remaining architecture work

Android and Web keep typed display/tracking fields plus an allowlisted `metadataJson` snapshot capped at 2 KB on new saves/imports. The snapshot retains source references, catalog ID/revision, genres, platforms and typed counts. Descriptions/artwork references already have typed columns; raw provider evidence is not duplicated in every saved item. Existing notes, progress and personal ratings remain separate. Older stored snapshots are not automatically rewritten in bulk.

Android database migration 6 → 7 adds the metadata columns without dropping items. Both clients preserve server identity/order and defensively reject price-only storefront records and default game extras, including responses from an older deployed engine.

## Game result policy

Normal game search shows base games (including distinct remakes/remasters), not store product listings. IGDB game types and verified Steam App IDs classify DLC, expansions, ports/expanded editions, demos, collections, bundles, mods and software. Clear storefront title suffixes catch supporter packs/soundtracks that lack a reliable subtype. Unknown sparse Steam records are enrichment candidates, not extra result cards. Prices never populate creators. Steam artwork uses supplied image URLs rather than guessed path replacements.

Use `includeExtras=true` on `/v1/search?category=GAME` to inspect non-base catalog products explicitly. The Lists UI defaults to base games. Direct link resolution remains a separate explicit import operation.

## Optional bounded D1 catalog (₹0-first)

The maintained implementation is `src/catalog/store.ts`; schema is `migrations/0001_catalog.sql`. No remote database is created and no deployment is performed by the local commands below.

```sh
npm run catalog:migrate:local
npm run dev:catalog
```

For production, create a D1 database on the Free plan, add its real UUID to the commented `CATALOG_DB` binding in `wrangler.toml`, then apply the migration to that database before deploying. Never deploy the all-zero database ID from the local config. The old book compatibility deployment needs a binding to the same database if you use both hostnames.

Initial application limits:

- At most 10,000 catalog entities and 200 MiB of serialized metadata payload. Payload bytes exclude SQLite tables/index overhead; check actual database size in D1 metrics.
- At most 32 KiB per entity; images and complete raw HTTP responses are not stored.
- At most three top results persisted per search. Other results remain available with provider-derived IDs. Local list entries always retain their own stable UUID, independent of catalog admission.
- At most 500 changed-entity writes per UTC day. This is a conservative application budget, **not** D1's billed row-write count; index/ref/title/stats operations count separately.
- At most 24 provider refs and eight indexed titles per stored entity. Aliases are replaced on refresh so old variants cannot grow indefinitely.
- Unchanged results do not acquire a write lease or consume database writes. There is no bulk ingestion, per-search activity logging, or background recommendation processing.

A short database lease serializes writes across Worker isolates; an atomic D1 batch commits entities, references and indexed aliases together. Lost leases, capacity limits, missing migrations or unavailable storage preserve live search responses. Known IDs and metadata can still be reused. Existing canonical entities are never silently merged with another canonical ID; ambiguous conflicts are left unresolved.

`GET /v1/entities/{catalogId}` retrieves stored shared metadata. Optional `?isbn=...` selects that book edition while preserving work identity. `GET /v1/catalog/coverage` returns aggregate category completeness and application-budget counters without scanning all entity records. This measures the stored sample, not global recall or search quality. The schema contains no user lists, notes, personal ratings or activity history.

Search responses include `catalogState`: `stored`, `read_only`, `busy`, `capacity`, `unavailable`, or `disabled`. The catalog currently reuses exact IDs/aliases and preserves cross-source knowledge; it is not yet a worldwide full-text index. Source freshness, refresh scheduling, manual merge/split review and production CPU/quota/latency profiling remain necessary before wider rollout.

## Current scope

The [source refinement notes](../SEARCH_SOURCE_REFINEMENTS.md) record the current provider choices, optional sources, constraints and remaining accuracy work. `npm run eval:search` runs an opt-in eight-query live smoke set; it is separate from deterministic unit tests.

Useful partial responses now cache for 30 seconds. Expired positive results may be reused for one additional hour only when live recovery produces no usable results, with `freshness: stale`. Cache memory remains capped at 200 entries / 16 MiB per Worker isolate. HTTP 429 responses create bounded credential-scoped backoff honoring Retry-After; this does not provide global cross-isolate quota scheduling. Failed empty searches are never negative cached.

Cover recovery: `/v2/cover?url=...` serves images from allowlisted provider CDNs, follows validated cover redirects (including Open Library's Internet Archive cover files), bounds upstream time/size, and caches successful images at the edge for one day. It uses no D1 image storage. Android retries failed CDN requests through this relay for both display and offline cover downloads; Web retries failed image loads through the same route.

Search recovery: discovery has up to 2.5 seconds inside a 5.5-second total budget, leaving time for backup providers. Clients allow this phase to complete and make one `strategy=backup` request after a failed engine call. This is provider recovery through the same Worker, not an independently hosted second engine; a complete network/Worker outage can still prevent search. Healthy no-match responses remain distinct from upstream outages. Books use Hardcover's supported indexed `search` operation, and explicit `title by author` queries retain the author constraint. Wikipedia fallbacks classify the subject's definition to exclude author pages, adaptations and characters from the wrong media category.

Public source ratings remain metadata. The app's personal rating controls, badges and sorting require an explicit `userRating` or `reviewRating`; a fetched public score alone never marks an item as personally rated. Existing explicit ratings are retained.

Search, metadata correctness, efficiency and multi-list tracking only. `/api/v1/books/recommend` is not exposed. Mood/pacing/vector calculations have been removed from normal book providers and projections. Legacy recommendation modules remain unwired reference code; they are not part of the active search bundle. No model training, embeddings, recommendation jobs or behavioral telemetry have been added.
