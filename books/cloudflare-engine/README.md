# Book engine compatibility entry

The book engine has been combined with the universal metadata service. Maintained domain code is in `server/src/books`; the TypeScript files here re-export that implementation. `src/index.ts` uses the universal Worker, so the old hostname can continue serving `/api/v1/books/search`, `/isbn/:isbn` and `/reading-speed` after redeployment.

Use `server/` as the primary development/deployment project. Both apps now use its base URL for media and book calls. If you keep deploying this compatibility Worker, configure the same required provider secrets there; it is a separate deployment of the same code.

See [the unified service documentation](../../server/README.md) for contracts, merged metadata, configuration, tests, and current limitations. The old permissive book merger no longer runs.

Recommendation routes are disabled; normal search does not compute recommendation vectors. Optional D1 persistence is shared with the universal service when configured.
