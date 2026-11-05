# Lists

> **The Universal Media Tracker & Aggregator**  
> One minimal, distraction-free app for everything you watch, read, and play.

---

## Overview

If you love movies, binge TV series, read books and comics, follow seasonal anime, or play video games, you probably have watchlists, reading queues, and backlogs scattered across a dozen different services (Letterboxd, Goodreads, Trakt, Backloggd, MyAnimeList, notes apps). 

**Lists** was built to solve this exact problem. It is a universal, content-first media tracker designed for people who want unified, personalized lists for every kind of media in one unified, beautifully crafted ledger—completely free of social feed noise, ads, or bloated interfaces.

---

## Key Features

### 🔍 Universal Media Aggregator & Search Engine
Fetch accurate metadata, release years, cover artwork, and progress breakdowns across virtually any medium:
- **Intelligent Query Normalization**: Automatic 4-digit release year extraction (`"Dune 1984"`), bi-directional Roman numeral mapping (`"Final Fantasy VII"` $\leftrightarrow$ `"Final Fantasy 7"`), diacritic folding (`Pokémon` $\rightarrow$ `Pokemon`), and leading article filtering.
- **Typo Tolerance & Fuzzy Scoring**: Sub-string Levenshtein edit distance and word-order-invariant token Jaccard similarity to rank relevant results instantly.
- **Parallel Provider Pipeline**:
  - **Movies**: Primary: iTunes Movies (`1200x1200bb`) & IMDb Suggestions; Fallback: OMDb API & Wikipedia.
  - **TV Shows & Anime**: TVMaze, AniList GraphQL (English + Romaji + Native Japanese titles), MangaDex, JustWatch.
  - **Books**: Primary: Apple Books (`1200x1200bb`); Backup: Open Library (`-L.jpg`); Fallback: Hardcover GraphQL & Google Books.
  - **Comics**: Cataloggd / Grand Comics Database (GCD), GetComics, Apple Books, and Google Books.
  - **Video Games**: IMDb Suggestions (video games), Steam Store, and Wikipedia.
  - **Board Games**: Dedicated Wikipedia Board Game search with 600px box art, Wikidata tabletop entities, and BoardGameGeek.
- **Request Coalescing & Negative Caching**: In-flight concurrency coalescing and 10-minute negative caching for empty queries to eliminate redundant network load.
- **Custom / Manual Entries**: Manually track any item with custom titles, notes, external links, and your own cover images.

---

### 🎨 Material Design 3 (M3) Minimalism
- **AMOLED Pitch Black & Paper Light**: True `#000000` dark mode that maximizes battery life and contrast on OLED displays, paired with a warm `#FAFAFA` paper light mode.
- **Dynamic Accent Theming**: Personalize the look with curated tonal palettes: **Lavender**, **Peach**, **Mint**, **Sky**, **Rose**, or **Monochrome (B&W)**.
- **Adaptive Bento Grid & Compact List**: Seamlessly toggle between visual Bento poster cards and dense, quick-scan list rows with an interactive slider.
- **Segmented Filter Controls**: Material 3 segmented button rows (`All`, `Done`, `Remaining`) featuring active accent color highlights for instantaneous filtering.
- **Discrete Decimal Rating Sliders**: Precise rating input across multiple scales:
  - **10-Scale**: Decimal precision with visual stop indicators at every integer milestone.
  - **5-Star**: Half-star and decimal adjustments.
  - **Skip to Perfection**: Minimal 4-tier status ratings.
- **Granular Progress Tracking**: Detailed progress rings reflecting the exact percentage of episodes, chapters, or pages completed.

---

### 🔒 Offline-First, Private & Lightweight
- **Zero Tracking**: No analytics, no third-party trackers, and no mandatory accounts.
- **Local Storage Engine**: Powered by Room Database on Android and optimized persistent WebStore on Web.
- **Ultra-Efficient Storage**: Media covers are compressed using lossy WebP at balanced resolution to prevent storage bloat, with memory-only cache policies to eliminate duplicate disk caching.
- **Local Backups**: Automatic rolling snapshots with one-click export/import of `.listspkg` and `.json` archive packages.

---

### ☁️ Private Google Drive Cloud Sync
- **Private AppData Folder**: Syncs data directly to your personal Google Drive hidden app data folder—your data is visible only to you.
- **Tombstone Sync Architecture**: Tracks deletions and updates across devices with deterministic conflict resolution, preventing resurrection of deleted items.

---

### 📱 Platform Parity: Android & Web
- **Native Android**: Built with 100% Jetpack Compose. Includes Material 3 Adaptive Icon support (with dark background and centered foreground artwork) that looks native on any Android launcher. Shrunk and minified with R8 (~2.8 MB release APK).
- **Modern Web**: Powered by Kotlin Wasm (WebAssembly) and Compose Multiplatform for fluid 60fps desktop and mobile browser performance.

---

## Project Structure

```
Lists/
├── app/                  # Native Android Application (Jetpack Compose, Room, Google Drive Sync)
│   ├── src/main/java/com/minimal/lists/
│   │   ├── data/         # Room Database, DAOs, Storage & Drive Sync
│   │   └── ui/           # Screens, Components, and Material 3 Themes
│   └── src/main/res/     # Adaptive Icons, Strings, and App Resources
│
├── wasmApp/              # Kotlin Wasm Web Application (Compose Multiplatform)
│   ├── src/wasmJsMain/   # Web Store, REST API Aggregator, and UI Screens
│   └── resources/        # Web App Manifest, Favicons, and HTML Entry
│
├── build.gradle.kts      # Unified Root Gradle configuration
└── settings.gradle.kts   # Project definitions (:app, :wasmApp)
```

---

## Getting Started

### Prerequisites
- **JDK 21** or later
- **Android SDK** (API 35, Build Tools 35.0.0)
- **Node.js** (managed automatically by Kotlin Multiplatform Gradle plugin)

### Build Android APK
```bash
# Debug APK
./gradlew :app:assembleDebug

# Release APK (R8 Minified)
./gradlew :app:assembleRelease
```

### Build Web Distribution
```bash
./gradlew :wasmApp:wasmJsBrowserDistribution
```
The compiled WebAssembly distribution will be output to:
`wasmApp/build/dist/wasmJs/productionExecutable/`

---

## 🎁 Data Sources & API Credits

**Lists** aggregates metadata and artwork from community-driven and open platforms. We are deeply grateful to the following projects, organizations, and APIs that make open media tracking possible:

| Provider | Category | Description & Attribution |
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

---

## License

This project is licensed under the [MIT License](LICENSE).

