# Books Cloud Engine (Cloudflare Worker)

High-performance, edge-deployed Book Search, ISBN/Barcode Lookup, and Recommendation Engine built with TypeScript, Hono, and Cloudflare Workers.

---

## Capabilities & Architecture

1. **Multi-Provider Book Search**:
   * Queries **Open Library** and **Google Books** in parallel with automatic deduplication, cross-provider merging, and intelligent relevance ranking.
   * Extracts ISBN-10, ISBN-13, exact page count, publisher, publication date, language, and high-resolution cover art.
2. **Fast-Path Barcode & ISBN Lookup**:
   * Accepts any ISBN-10 or ISBN-13 (EAN-13 barcode).
   * Validates modulo 10 and modulo 11 checksums.
   * Fetches exact-edition metadata in ~200ms.
3. **Recommendation Engine (StoryGraph-inspired Content Attributes)**:
   * Classifies books across 14 **Moods** (*Dark, Atmospheric, Reflective, Tense, Hopeful, Emotional, etc.*) and **Pacing** (*Slow, Medium, Fast*).
   * Computes **Cosine Similarity** between reader taste profiles and candidate book vectors.
4. **Dynamic EWMA Reading Tracker & ETA**:
   * Uses Exponentially Weighted Moving Average (EWMA) over reading sessions to compute reading speed (`pages/hr`, `min/page`) and dynamic estimated time to finish.

---

## API Endpoints

### 1. Search Books
```http
GET /api/v1/books/search?q={query}&type={all|title|author|isbn}&lang={en|es|fr}&limit=20
```
**Example Response:**
```json
{
  "query": "dune",
  "searchType": "all",
  "source": "live",
  "count": 15,
  "results": [
    {
      "id": "9780441172719",
      "title": "Dune",
      "authors": ["Frank Herbert"],
      "primaryAuthor": "Frank Herbert",
      "isbn10": "0441172717",
      "isbn13": "9780441172719",
      "publisher": "Ace Books",
      "publishedYear": "1965",
      "totalPages": 896,
      "language": "en",
      "coverUrl": "https://covers.openlibrary.org/b/isbn/9780441172719-L.jpg?default=false",
      "normalizedGenres": ["SCI_FI", "FICTION"],
      "suggestedMoods": ["ATMOSPHERIC", "TENSE", "DARK", "REFLECTIVE"],
      "suggestedPacing": "MEDIUM"
    }
  ]
}
```

### 2. Barcode & ISBN Lookup
```http
GET /api/v1/books/isbn/9780141187761
```
**Example Response:**
```json
{
  "isbn": "9780141187761",
  "success": true,
  "book": {
    "id": "9780141187761",
    "title": "1984",
    "authors": ["George Orwell"],
    "isbn13": "9780141187761",
    "totalPages": 328,
    "publisher": "Penguin Books",
    "normalizedGenres": ["SCI_FI", "FICTION", "PHILOSOPHY"],
    "suggestedMoods": ["DARK", "TENSE", "REFLECTIVE"]
  }
}
```

### 3. Recommendation Matcher
```http
POST /api/v1/books/recommend
Content-Type: application/json

{
  "favoriteGenres": ["SCI_FI"],
  "preferredMoods": ["DARK", "ATMOSPHERIC"],
  "preferredPacing": "FAST",
  "limit": 10
}
```

### 4. Reading Speed & Finish ETA
```http
POST /api/v1/books/reading-speed
Content-Type: application/json

{
  "totalPages": 412,
  "currentPage": 150,
  "sessions": [
    { "durationMinutes": 30, "pagesRead": 22 },
    { "durationMinutes": 45, "pagesRead": 35 }
  ]
}
```
**Example Response:**
```json
{
  "totalPages": 412,
  "currentPage": 150,
  "remainingPages": 262,
  "averagePagesPerHour": 45.3,
  "averageMinutesPerPage": 1.3,
  "estimatedMinutesToFinish": 347,
  "estimatedFinishFormatted": "5h 47m",
  "confidenceLevel": "HIGH"
}
```

---

## Deployment to Cloudflare Workers

1. **Install Dependencies**:
   ```bash
   cd books/cloudflare-engine
   npm install
   ```

2. **Test Locally**:
   ```bash
   npm run dev
   ```

3. **Deploy to Cloudflare**:
   ```bash
   npx wrangler deploy
   ```

4. **Optional Secrets (for higher rate limits)**:
   ```bash
   npx wrangler secret put GOOGLE_BOOKS_API_KEY
   npx wrangler secret put ISBNDB_API_KEY
   ```

