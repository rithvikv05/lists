# Lists Universal Search & Aggregation Engine

> **Standalone, Edge-Deployed Universal Metadata Search Engine**  
> Blazing fast (<50ms), multi-provider media discovery engine designed for **Lists** (Android & Web).

---

## ⚡ Features

- **Decoupled Architecture**: Scrapers, APIs, regexes, and upstream fixes are updated in 5 seconds with zero app updates or Google Play review delays.
- **Intelligent Query Normalization**:
  - Automatic 4-digit release year extraction (`"Dune 1984"`).
  - Bi-directional Roman numeral mapping (`"Final Fantasy VII"` $\leftrightarrow$ `"Final Fantasy 7"`).
  - Diacritic folding (`"Pokémon"` $\rightarrow$ `"Pokemon"`).
  - Leading article filtering (`"The"`, `"A"`, `"An"`, `"Der"`, `"Le"`).
- **Composite Fuzzy Relevance Scoring**:
  - Damerau-Levenshtein edit distance for typo tolerance.
  - Word-order invariant Token Jaccard similarity.
  - Multi-factor scoring prioritizing exact matches, year alignment, creator matches, and artwork resolution.
- **Comprehensive Parallel Provider Coverage**:
  - **Movies**: iTunes Movies (`1200x1200bb.jpg`), IMDb Suggestions (Amazon SX1200 posters), OMDb API.
  - **TV Shows & Anime**: TVMaze, AniList GraphQL (English, Romaji, and Native Japanese titles).
  - **Books**: Apple Books (`1200x1200bb.jpg`), Open Library (`-L.jpg`), Google Books (`zoom=3`), Hardcover GraphQL.
  - **Comics**: Cataloggd (GCD ComicDB), GetComics, Apple Books Comics, Google Books Comics.
  - **Video Games**: IMDb Video Games (Amazon SX1200 posters), Steam Store Search API.
  - **Board Games**: Wikipedia Board Games (with 600px box art), Wikidata Tabletop Entities, BoardGameGeek.
- **Multi-Tier Caching**:
  - In-memory hot cache (24-hour TTL).
  - 10-minute negative caching for empty queries.
  - Zero-cold-start edge execution across 300+ cities worldwide.

---

## 🚀 Quickstart: Local Development

```bash
# Navigate to the server directory
cd server

# Install dependencies (if not already done)
npm install

# Start local edge development server
npm run dev
```

Your server will be running at `http://localhost:8787`.

---

## 📡 API Reference

### 1. Health Check
`GET /health`

```bash
curl http://localhost:8787/health
```

**Response:**
```json
{
  "status": "ok",
  "engine": "lists-search-engine",
  "time": "2026-11-06T00:00:00.000Z"
}
```

---

### 2. Universal Search
`GET /v1/search?q={query}&category={category}&year={year}&lang={lang}`

#### Parameters:
| Param | Required | Description | Example |
| :--- | :--- | :--- | :--- |
| `q` | **Yes** | Search term | `"interstellar"` |
| `category` | **Yes** | Media category | `"MOVIE"`, `"BOOK"`, `"GAME"`, `"BOARD_GAME"`, `"COMIC"`, `"ANIME"`, `"TV_SHOW"` |
| `year` | No | Optional 4-digit release year | `"2014"` |
| `lang` | No | ISO language code | `"en"` |

#### Example:
```bash
# Search for Movies
curl "http://localhost:8787/v1/search?q=dune+1984&category=MOVIE"

# Search for Board Games
curl "http://localhost:8787/v1/search?q=catan&category=BOARD_GAME"

# Search for Books
curl "http://localhost:8787/v1/search?q=hyperion&category=BOOK"
```

---

### 3. Metadata & Streaming Enrichment
`GET /v1/enrich?title={title}&category={category}`

#### Example:
```bash
curl "http://localhost:8787/v1/enrich?title=Succession&category=TV_SHOW"
```

---

## 🌐 1-Click Deployment (Cloudflare Workers)

Deploy globally to 300+ edge locations for **free** (100,000 requests/day included):

```bash
# Login to your free Cloudflare account
npx wrangler login

# Deploy globally in 5 seconds
npm run deploy
```

Wrangler will output your live HTTPS production URL (e.g. `https://lists-search-engine.<your-account>.workers.dev`).

### Configuring Secret Keys (Optional)

```bash
npx wrangler secret put HARDCOVER_API_TOKEN
npx wrangler secret put OMDB_API_KEY
npx wrangler secret put TWITCH_CLIENT_ID
npx wrangler secret put TWITCH_CLIENT_SECRET
```

---

## 🐳 Docker Deployment (Alternative)

If you prefer deploying to Railway, Google Cloud Run, or Fly.io:

```dockerfile
FROM node:20-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npx tsc
EXPOSE 8787
CMD ["npx", "tsx", "src/index.ts"]
```

---

## 🎁 Data Sources & API Credits

Lists Universal Search Engine aggregates data from community-driven and open platforms. We are deeply grateful to the following projects, organizations, and APIs that make open media cataloging possible:

| Provider | Category | Description & Website |
| :--- | :--- | :--- |
| **IGDB (Twitch)** | Video Games | Comprehensive video game database, cover art, and release schedules. [igdb.com](https://www.igdb.com/) |
| **OMDb API** | Movies & TV | Verified ratings, Rotten Tomatoes / Metacritic scores, and synopses by Brian Fritz. [omdbapi.com](https://www.omdbapi.com/) |
| **The Movie Database (TMDb)** | Movies & TV | Movie and television metadata, backdrops, and cast. *(This product uses the TMDB API but is not endorsed or certified by TMDB).* [themoviedb.org](https://www.themoviedb.org/) |
| **Hardcover** | Books | Modern book tracking, GraphQL API, reviews, and community ratings. [hardcover.app](https://hardcover.app/) |
| **Comic Vine** | Comics | Massive comic book database, story arcs, and superhero issues. [comicvine.gamespot.com](https://comicvine.gamespot.com/) |
| **Apple Services** | Multi-Media | High-resolution 1200px artwork for movies, TV, books, and digital graphic novels. [apple.com](https://www.apple.com/) |
| **Open Library** | Books & Comics | Non-profit open library catalog and -L cover art archives by the Internet Archive. [openlibrary.org](https://openlibrary.org/) |
| **Google Books** | Books & Manga | Bibliographic volume information, page counts, and descriptions. [books.google.com](https://books.google.com/) |
| **AniList** | Anime & Manga | Fast GraphQL API for Japanese, Korean, and Chinese animation and sequential art. [anilist.co](https://anilist.co/) |
| **Jikan (MyAnimeList)** | Anime & Manga | Open-source REST API for MyAnimeList community scores and staff credits. [jikan.moe](https://jikan.moe/) |
| **Kitsu** | Anime & Manga | Anime/manga discovery platform with canonical titles and posters. [kitsu.io](https://kitsu.io/) |
| **TVmaze** | TV Shows & Anime | Television broadcast schedules, episodic guides, and networks. [tvmaze.com](https://www.tvmaze.com/) |
| **Steam (Valve)** | Video Games | Storefront API, Steam Deck tags, developer credits, and game capsules. [store.steampowered.com](https://store.steampowered.com/) |
| **CheapShark** | Video Games | Video game pricing, retail storefronts, and Steam App ID resolution. [cheapshark.com](https://www.cheapshark.com/) |
| **Grand Comics DB (GCD)** | Comics | Volunteer-driven comic book database covering issues from the 1930s to today. [comics.org](https://www.comics.org/) |
| **MangaDex** | Manga | Manga chapter counts, high-resolution volume covers, and translations. [mangadex.org](https://mangadex.org/) |
| **BoardGameGeek (BGG)** | Board Games | The definitive tabletop board game database, rankings, and player counts. [boardgamegeek.com](https://boardgamegeek.com/) |
| **Board Game Atlas** | Board Games | Tabletop board game discovery catalog and box images. [boardgameatlas.com](https://www.boardgameatlas.com/) |
| **JustWatch** | Movies & TV | Streaming availability and OTT direct links. [justwatch.com](https://www.justwatch.com/) |
| **CrossRef** | Books & Academic | Authoritative DOI bibliographic records and publishing metadata. [crossref.org](https://www.crossref.org/) |
| **Wikipedia & Wikidata** | Multi-Media | Open encyclopedic summaries and structured linked data under CC BY-SA. [wikimedia.org](https://www.wikimedia.org/) |


