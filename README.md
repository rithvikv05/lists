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

## License

This project is licensed under the [MIT License](LICENSE).

