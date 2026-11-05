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
