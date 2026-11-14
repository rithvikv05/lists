# Search source refinements — 7 November 2026

Scope: metadata search and list tracking. No recommendation engine, user-behavior collection, paid subscription, bulk catalog import, or new D1 deployment.

## Implemented in this pass

- **Books:** Inventaire's public work search is an additional fallback. Hydrate at most four candidates in one batch, including their authors. Reject author entities and loose title matches. Preserve work identifiers and source links; do not invent edition ISBNs, pages or ratings. It is supplementary coverage, not a replacement for Hardcover/Open Library/Google Books.
- **Anime/manga depth:** retain Jikan genres, themes, demographics, status, rating counts, contributor roles and manga volume counts. These already arrive in search responses, so extracting them adds no provider requests. Kitsu now includes its genre relationships in the same search request, preserving per-result genre associations even when Jikan is unavailable.
- **Manga identity:** use MangaDex's supplied MyAnimeList/Kitsu links to join alternate titles. Request author/artist relationships in the same search response and retain genre/theme distinctions.
- **TV identity:** retain TVDB IDs supplied by both TMDB and TVmaze. No extra TVDB request or subscription is involved.
- **Book editions:** deduplication retains separate ISBNs under a provider's work ID, instead of keeping only the first edition.
- **Reliability/cost:** useful partial results cache for 30 seconds; healthy positive results for 10 minutes; healthy empty results for 30 seconds. Failed empty searches are never negative cached. An expired usable result can be returned for up to one additional hour when live recovery yields no usable results, with `freshness: stale` and `status: partial`. The 200-entry / 16 MiB isolate cache cap remains. This is best-effort across requests handled by the same Worker isolate, not persistent/distributed storage.
- **Quota handling:** respect HTTP 429 Retry-After (seconds or date, bounded to 1–900 seconds). A hashed provider/credential identity prevents retrying every keystroke against an exhausted key, while allowing a different key to work. Backoff is bounded, per-isolate and limited to rate-limit responses.
- **Evaluation:** `cd server && npm run eval:search` runs eight opt-in live smoke queries. It reports expected rank, repeated identity references, cover presence and sample latency. This small set is not a statistically useful global benchmark. It creates no scheduled jobs or app analytics.

## Source decisions and next candidates

| Category | Current direction | Next useful source/action | Constraints |
| --- | --- | --- | --- |
| Books | Hardcover + Open Library + Google Books; Apple/Wikipedia/Inventaire recovery | Configure an application-owned Google Books key; expand ISBN/edition and non-English evaluation | Shared unauthenticated Google quota currently fails. A key must be supplied separately; no quota guarantee. Goodreads remains link import, not a scraped general search backend. |
| Games | IGDB + exact Steam details; preserve base-game classification | RAWG as an optional additional discovery catalog after source attribution is implemented | Official personal/hobby plan requires a key and backlinks. Not enabled without credentials/attribution. MobyGames requires an API subscription and is outside the current budget. |
| Movies/TV | TMDB structured details; TVmaze; OMDb where available | Improve native/translated title and remake/year evaluation before adding another broad movie catalog | Multiple catalogs containing the same popular film are less valuable than correct identity and detail retention. |
| Anime/manga | Jikan + Kitsu + MangaDex | Explore verified Kitsu mappings and on-demand related-series/season detail | Additional mapping/detail calls must be bounded and cached. AniList stays disabled pending tracker eligibility. |
| Comics | Comic Vine, with common book records for collected editions | Evaluate Metron for issue/series metadata | Official API requires account tokens and rate-limit handling. Issue, collection and series identities must remain distinct. No token was provisioned in this pass. |
| Board games | Registered BGG adapter + Wikidata recovery | Obtain approved BGG application authorization and finish public attribution | Existing adapter requires `BGG_API_TOKEN`; it is not enabled by inventing an unauthenticated route. |

## Accuracy work that remains

1. Expand the judged evaluation set to ambiguous names, remakes, regional titles, typos, transliteration, recently released books, manga one-shots, comic issues and standalone games sharing titles. Record expected identities, not only strings. Track wrong merges separately from visible duplicate cards.
2. Add bounded query rewriting only after a poor/no-match response: title-author parsing, native/Latin aliases, typo alternatives and explicit year handling. Keep original intent and filters; do not silently relax author/year constraints to find something vaguely similar.
3. Add on-demand exact-ID detail enrichment for the selected item, including exact Hardcover edition/ISBN queries rather than treating work-level fields as edition facts. Keep search discovery bounded, and avoid downloading every cast, episode list, edition, screenshot or game requirement for every result card.
4. Normalize genre synonyms against a small maintained vocabulary while retaining original source labels. Keep themes and demographics separate. Resolve disputed crosswalks with corroboration. A live Inventaire/Wikidata record for Dune contained multiple Open Library work IDs; those IDs were deliberately not copied into automatic merge references. More source IDs do not automatically mean more accurate identity.
5. If the app becomes multi-user, evaluate a shared edge cache and provider-wide request scheduling from measured traffic. The current isolate cache and 429 backoff do not coordinate globally. No persistent infrastructure was added for hypothetical load.

## Primary references checked

- [Inventaire API and public specification](https://api.inventaire.io/) ([OpenAPI JSON](https://inventaire.io/public/api_specs.json))
- [Jikan API](https://docs.api.jikan.moe/)
- [TVmaze API](https://www.tvmaze.com/api)
- [RAWG API plans and attribution](https://rawg.io/apidocs)
- [MobyGames API access](https://www.mobygames.com/info/api/)
- [Metron API authentication](https://github.com/Metron-Project/metron/blob/master/api/README.md)
- [BGG application authorization](https://boardgamegeek.com/using_the_xml_api)

MangaDex fields were checked against its live structured search response. Provider policies, schemas, keys and limits can change; these integrations are not permanent guarantees of breadth or availability.
