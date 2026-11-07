package com.lists.app.data.api

import com.lists.app.currentTimeMillis
import com.lists.app.data.model.Category
import com.lists.app.data.model.SearchResult
import com.lists.app.encodeURIComponent
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

enum class BookSubFilter(val label: String) {
    ALL("All"),
    BOOKS("Books"),
    MANGA("Manga"),
    COMICS("Comics")
}

enum class BookSearchType(val label: String) {
    ALL("All"),
    TITLE("Title"),
    AUTHOR("Author")
}

enum class TvSubFilter(val label: String) {
    ALL("All"),
    TV_SHOWS("TV Shows"),
    ANIME("Anime")
}

data class OttPlatform(
    val name: String,
    val url: String
)

@Serializable
data class EpisodeItem(
    val season: Int,
    val number: Int,
    val name: String,
    val airdate: String = "",
    val airstamp: String = ""
) {
    val key: String get() = "S${season}E${number}"
}

object QueryNormalizer {
    data class NormalizedQuery(
        val original: String,
        val cleanQuery: String,
        val extractedYear: String?,
        val variants: List<String>
    )

    private val YEAR_REGEX = Regex("""\b(19\d{2}|20\d{2})\b""")

    private val ROMAN_TO_ARABIC = listOf(
        Regex("""\bVIII\b""", RegexOption.IGNORE_CASE) to "8",
        Regex("""\bVII\b""", RegexOption.IGNORE_CASE) to "7",
        Regex("""\bIII\b""", RegexOption.IGNORE_CASE) to "3",
        Regex("""\bIV\b""", RegexOption.IGNORE_CASE) to "4",
        Regex("""\bIX\b""", RegexOption.IGNORE_CASE) to "9",
        Regex("""\bVI\b""", RegexOption.IGNORE_CASE) to "6",
        Regex("""\bII\b""", RegexOption.IGNORE_CASE) to "2",
        Regex("""\bV\b""", RegexOption.IGNORE_CASE) to "5",
        Regex("""\bX\b""", RegexOption.IGNORE_CASE) to "10",
        Regex("""\bI\b""", RegexOption.IGNORE_CASE) to "1"
    )

    private val ARABIC_TO_ROMAN = listOf(
        Regex("""\b10\b""") to "X",
        Regex("""\b9\b""") to "IX",
        Regex("""\b8\b""") to "VIII",
        Regex("""\b7\b""") to "VII",
        Regex("""\b6\b""") to "VI",
        Regex("""\b5\b""") to "V",
        Regex("""\b4\b""") to "IV",
        Regex("""\b3\b""") to "III",
        Regex("""\b2\b""") to "II",
        Regex("""\b1\b""") to "I"
    )

    fun foldDiacritics(str: String): String {
        val sb = StringBuilder(str.length)
        for (ch in str) {
            val replacement = when (ch) {
                'á', 'à', 'â', 'ä', 'ã', 'å', 'ā' -> 'a'
                'Á', 'À', 'Â', 'Ä', 'Ã', 'Å', 'Ā' -> 'A'
                'é', 'è', 'ê', 'ë', 'ē', 'ė', 'ę' -> 'e'
                'É', 'È', 'Ê', 'Ë', 'Ē', 'Ė', 'Ę' -> 'E'
                'í', 'ì', 'î', 'ï', 'ī', 'į' -> 'i'
                'Í', 'Ì', 'Î', 'Ï', 'Ī', 'Į' -> 'I'
                'ó', 'ò', 'ô', 'ö', 'õ', 'ō', 'ø' -> 'o'
                'Ó', 'Ò', 'Ô', 'Ö', 'Õ', 'Ō', 'Ø' -> 'O'
                'ú', 'ù', 'û', 'ü', 'ū' -> 'u'
                'Ú', 'Ù', 'Û', 'Ü', 'Ū' -> 'U'
                'ñ' -> 'n'; 'Ñ' -> 'N'
                'ç' -> 'c'; 'Ç' -> 'C'
                'ý', 'ÿ' -> 'y'; 'Ý' -> 'Y'
                'š' -> 's'; 'Š' -> 'S'
                'ž' -> 'z'; 'Ž' -> 'Z'
                else -> ch
            }
            sb.append(replacement)
        }
        return sb.toString()
    }

    fun stripLeadingArticles(str: String): String {
        val lower = str.trim()
        val articles = listOf("the ", "a ", "an ", "der ", "die ", "das ", "le ", "la ", "les ", "el ", "los ")
        for (art in articles) {
            if (lower.startsWith(art, ignoreCase = true)) {
                return lower.substring(art.length).trim()
            }
        }
        return lower
    }

    fun normalize(input: String): NormalizedQuery {
        val trimmed = input.trim()
        val folded = foldDiacritics(trimmed)
        val yearMatch = YEAR_REGEX.find(folded)
        val extractedYear = yearMatch?.value
        val cleanQuery = if (extractedYear != null) {
            folded.replace(extractedYear, "").replace(Regex("""\s+"""), " ").trim()
        } else {
            folded
        }

        val variants = mutableListOf<String>()
        variants.add(cleanQuery)

        var toArabic = cleanQuery
        for ((roman, arabic) in ROMAN_TO_ARABIC) {
            toArabic = toArabic.replace(roman, arabic)
        }
        if (toArabic != cleanQuery && !variants.contains(toArabic)) {
            variants.add(toArabic)
        }

        var toRoman = cleanQuery
        for ((arabic, roman) in ARABIC_TO_ROMAN) {
            toRoman = toRoman.replace(arabic, roman)
        }
        if (toRoman != cleanQuery && !variants.contains(toRoman)) {
            variants.add(toRoman)
        }

        return NormalizedQuery(
            original = trimmed,
            cleanQuery = cleanQuery.ifBlank { trimmed },
            extractedYear = extractedYear,
            variants = variants
        )
    }
}

object FuzzyMatcher {
    fun levenshteinDistance(s1: String, s2: String): Int {
        val a = s1.lowercase()
        val b = s2.lowercase()
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                dp[j] = if (a[i - 1] == b[j - 1]) prev else minOf(prev, dp[j], dp[j - 1]) + 1
                prev = temp
            }
        }
        return dp[b.length]
    }

    fun tokenJaccardSimilarity(s1: String, s2: String): Double {
        val t1 = s1.lowercase().split(Regex("""[^a-z0-9]+""")).filter { it.isNotBlank() }.toSet()
        val t2 = s2.lowercase().split(Regex("""[^a-z0-9]+""")).filter { it.isNotBlank() }.toSet()
        if (t1.isEmpty() || t2.isEmpty()) return 0.0
        val intersection = t1.intersect(t2).size
        val union = t1.union(t2).size
        return intersection.toDouble() / union.toDouble()
    }

    fun calculateRelevance(
        query: String,
        cleanQuery: String,
        extractedYear: String?,
        result: SearchResult
    ): Double {
        val titleLower = result.title.lowercase().trim()
        val cleanQLower = cleanQuery.lowercase().trim()
        val origQLower = query.lowercase().trim()

        var score = 0.0

        if (titleLower == cleanQLower || titleLower == origQLower) {
            score += 100.0
        } else if (titleLower.startsWith(cleanQLower) || titleLower.startsWith(origQLower)) {
            score += 65.0
        } else if (titleLower.contains(cleanQLower) || titleLower.contains(origQLower)) {
            score += 40.0
        }

        val strippedTitle = QueryNormalizer.stripLeadingArticles(titleLower)
        val strippedCleanQ = QueryNormalizer.stripLeadingArticles(cleanQLower)
        if (strippedTitle == strippedCleanQ) {
            score += 85.0
        } else if (strippedTitle.startsWith(strippedCleanQ)) {
            score += 55.0
        }

        val jaccard = tokenJaccardSimilarity(titleLower, cleanQLower)
        score += jaccard * 45.0

        val maxLen = maxOf(titleLower.length, cleanQLower.length)
        if (maxLen > 3 && kotlin.math.abs(titleLower.length - cleanQLower.length) <= 3) {
            val dist = levenshteinDistance(titleLower.take(cleanQLower.length), cleanQLower)
            if (dist == 1) score += 28.0
            else if (dist == 2) score += 14.0
        }

        if (result.creator.isNotBlank()) {
            val creatorLower = result.creator.lowercase()
            if (creatorLower.contains(cleanQLower) || creatorLower.contains(origQLower)) {
                score += 35.0
            }
        }

        if (!extractedYear.isNullOrBlank()) {
            if (result.releaseYear.contains(extractedYear) || result.releaseDate.contains(extractedYear)) {
                score += 40.0
            }
        }

        if (result.coverUrl.isNotBlank()) score += 12.0
        if (result.overview.isNotBlank()) score += 8.0
        if (result.rating > 0f) score += (result.rating / 10f) * 6.0

        return score
    }
}

class SearchRepository {
    companion object {
        var remoteEngineUrl: String = "https://lists-search-engine.minimal-lists.workers.dev/v1"
        private val searchCache = mutableMapOf<String, List<SearchResult>>()
        private val inFlightRequests = mutableMapOf<String, Deferred<List<SearchResult>>>()
        private val negativeCache = mutableMapOf<String, Long>()
        private const val NEGATIVE_CACHE_TTL_MS = 10 * 60 * 1000L
    }

    private val client = HttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun fetchFromRemoteEngine(
        query: String,
        category: Category,
        subFilter: String = "ALL",
        year: String? = null,
        lang: String? = null
    ): List<SearchResult>? {
        if (remoteEngineUrl.isBlank()) return null
        return try {
            val qEncoded = encodeURIComponent(query)
            val yearParam = if (!year.isNullOrBlank()) "&year=${encodeURIComponent(year)}" else ""
            val langParam = if (!lang.isNullOrBlank()) "&lang=${encodeURIComponent(lang)}" else ""
            val url = "$remoteEngineUrl/search?q=$qEncoded&category=${category.name}&subFilter=$subFilter$yearParam$langParam"

            val response = client.get(url).bodyAsText()
            val root = json.parseToJsonElement(response).jsonObject
            val arr = root["results"]?.jsonArray ?: return null
            val list = mutableListOf<SearchResult>()
            for (elem in arr) {
                val obj = elem.jsonObject
                val title = obj["title"]?.jsonPrimitive?.content ?: continue
                val catStr = obj["category"]?.jsonPrimitive?.content ?: category.name
                val parsedCat = try { Category.valueOf(catStr) } catch (_: Exception) { category }

                list.add(
                    SearchResult(
                        title = title,
                        creator = obj["creator"]?.jsonPrimitive?.content ?: "",
                        releaseYear = obj["releaseYear"]?.jsonPrimitive?.content ?: "",
                        releaseDate = obj["releaseDate"]?.jsonPrimitive?.content ?: "",
                        coverUrl = obj["coverUrl"]?.jsonPrimitive?.content ?: "",
                        overview = obj["overview"]?.jsonPrimitive?.content ?: "",
                        rating = obj["rating"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: 0f,
                        totalEpisodes = obj["totalEpisodes"]?.jsonPrimitive?.intOrNull ?: 0,
                        totalPages = obj["totalPages"]?.jsonPrimitive?.intOrNull ?: 0,
                        category = parsedCat,
                        externalUrl = obj["externalUrl"]?.jsonPrimitive?.content ?: ""
                    )
                )
            }
            if (list.isNotEmpty()) list else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun isValidSearchResult(item: SearchResult): Boolean {
        val title = item.title.trim()
        if (title.isBlank()) return false
        val lower = title.lowercase()
        if (lower == "not found" || lower == "error" || lower == "404" ||
            lower.startsWith("list of ") || lower.startsWith("lists of ") ||
            lower.contains("(disambiguation)")) {
            return false
        }
        if (item.category == Category.GAME) {
            // Filter out store price duplicates that have no real game overview
            val c = item.creator.trim()
            if (c.startsWith("$") || c.startsWith("€") || c.startsWith("£") || c.startsWith("¥") || c.startsWith("₹") ||
                c.matches(Regex("""^[\$€£¥₹\d].*"""))) {
                return false
            }
            if (item.overview.isBlank() && (item.externalUrl.contains("steampowered.com") || item.coverUrl.contains("steamstatic.com"))) {
                return false
            }
            if (lower.contains("soundtrack") || lower.contains("remastered soundtrack") || lower.contains("songs of the past")) {
                return false
            }
        }
        return item.coverUrl.isNotBlank() || item.overview.isNotBlank() ||
               item.creator.isNotBlank() || item.releaseYear.isNotBlank()
    }

    private fun enhanceCoverUrl(url: String): String {
        if (url.isBlank()) return ""
        var enhanced = url
        if (enhanced.contains("apple.com") || enhanced.contains("mzstatic.com")) {
            enhanced = enhanced.replace("100x100bb.", "1200x1200bb.")
                .replace("600x600bb.", "1200x1200bb.")
                .replace("800x800bb.", "1200x1200bb.")
        } else if (enhanced.contains("media-amazon.com")) {
            enhanced = enhanced.replace(Regex("""\._V1_.*\.jpg"""), "._V1_SX1200.jpg")
        } else if (enhanced.contains("books.google.com")) {
            enhanced = enhanced.replace("http://", "https://")
                .replace("&edge=curl", "")
                .replace("zoom=1", "zoom=3")
                .replace("zoom=2", "zoom=3")
        } else if (enhanced.contains("covers.openlibrary.org")) {
            enhanced = enhanced.replace("-M.jpg", "-L.jpg").replace("-S.jpg", "-L.jpg")
        }
        return enhanced
    }

    private fun cleanCanonicalTitle(rawTitle: String): String {
        return rawTitle
            .replace(Regex("""(?i)(?:,\s*)?(?:season|series|part|vol\.?|volume)\s*\d+.*$"""), "")
            .replace(Regex("""(?i)(?:the final season|complete series|box set).*$"""), "")
            .replace(Regex("""(?i)\s*\((?:tv series|tv show|series|film|movie|anime|manga|novel|comic|video game|board game|game|\d{4})\)"""), "")
            .trim()
    }

    private fun deduplicate(results: List<SearchResult>): List<SearchResult> {
        val map = linkedMapOf<String, SearchResult>()
        for (item in results) {
            val canonicalTitle = cleanCanonicalTitle(item.title)
            val cleanKey = canonicalTitle
                .lowercase()
                .replace(Regex("""[^a-z0-9]"""), "")

            if (cleanKey.isBlank()) continue
            val yearDigits = item.releaseYear.filter { it.isDigit() }.take(4)
            val isBookType = item.category == Category.BOOK || item.category == Category.MANGA || item.category == Category.COMIC
            val key = if (isBookType) cleanKey else (if (yearDigits.isNotBlank()) "$cleanKey#$yearDigits" else cleanKey)

            val enhancedCover = enhanceCoverUrl(item.coverUrl)
            val cleanedItem = item.copy(title = canonicalTitle, coverUrl = enhancedCover)

            val existing = map[key]
            if (existing == null) {
                map[key] = cleanedItem
            } else {
                val betterCover = if (existing.coverUrl.isBlank() && cleanedItem.coverUrl.isNotBlank()) cleanedItem.coverUrl else existing.coverUrl
                val betterOverview = if (existing.overview.length < cleanedItem.overview.length) cleanedItem.overview else existing.overview
                val betterYear = if (existing.releaseYear.isBlank() && cleanedItem.releaseYear.isNotBlank()) cleanedItem.releaseYear else existing.releaseYear
                val betterCreator = if (existing.creator.isBlank() && cleanedItem.creator.isNotBlank()) cleanedItem.creator else existing.creator
                val betterExtUrl = if (existing.externalUrl.isBlank() && cleanedItem.externalUrl.isNotBlank()) cleanedItem.externalUrl else existing.externalUrl
                val betterEps = if (existing.totalEpisodes <= 0 && cleanedItem.totalEpisodes > 0) cleanedItem.totalEpisodes else existing.totalEpisodes
                val betterPages = maxOf(existing.totalPages, cleanedItem.totalPages)
                val betterIsbn = if (existing.isbn.isBlank() && cleanedItem.isbn.isNotBlank()) cleanedItem.isbn else existing.isbn
                val betterPublisher = if (existing.publisher.isBlank() && cleanedItem.publisher.isNotBlank()) cleanedItem.publisher else existing.publisher
                val betterLanguage = if (existing.language.isBlank() && cleanedItem.language.isNotBlank()) cleanedItem.language else existing.language

                map[key] = existing.copy(
                    coverUrl = betterCover,
                    overview = betterOverview,
                    releaseYear = betterYear,
                    creator = betterCreator,
                    externalUrl = betterExtUrl,
                    totalEpisodes = betterEps,
                    totalPages = betterPages,
                    isbn = betterIsbn,
                    publisher = betterPublisher,
                    language = betterLanguage
                )
            }
        }
        return map.values.toList()
    }

    suspend fun search(
        query: String,
        category: Category,
        bookSubFilter: BookSubFilter = BookSubFilter.ALL,
        tvSubFilter: TvSubFilter = TvSubFilter.ALL,
        bookSearchType: BookSearchType = BookSearchType.ALL,
        selectedYear: String? = null,
        selectedLanguage: String? = null
    ): List<SearchResult> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()

        val norm = QueryNormalizer.normalize(q)
        val effectiveYear = selectedYear ?: norm.extractedYear
        val searchCleanQuery = norm.cleanQuery

        val cacheKey = "$category|$bookSubFilter|$tvSubFilter|$bookSearchType|$effectiveYear|$selectedLanguage|${searchCleanQuery.lowercase()}"

        // 1. Positive cache check
        searchCache[cacheKey]?.let { return it }

        // 2. Negative cache check (10 min TTL)
        val negTime = negativeCache[cacheKey]
        if (negTime != null) {
            if (currentTimeMillis() - negTime < NEGATIVE_CACHE_TTL_MS) {
                return emptyList()
            } else {
                negativeCache.remove(cacheKey)
            }
        }

        // 3. Request Coalescing
        val existingDeferred = inFlightRequests[cacheKey]
        if (existingDeferred != null) {
            return existingDeferred.await()
        }

        return coroutineScope {
            val deferred = async {
                // Try standalone edge search engine first (2200ms timeout)
                val subFilter = when (category) {
                    Category.BOOK -> bookSubFilter.name
                    Category.TV_SHOW -> tvSubFilter.name
                    else -> "ALL"
                }
                val remoteResults = withTimeoutOrNull(2200) {
                    fetchFromRemoteEngine(searchCleanQuery, category, subFilter, effectiveYear, selectedLanguage)
                }
                if (!remoteResults.isNullOrEmpty()) {
                    val validRemote = remoteResults.filter { isValidSearchResult(it) }
                    val deduplicatedRemote = deduplicate(validRemote)
                    if (deduplicatedRemote.isNotEmpty()) {
                        searchCache[cacheKey] = deduplicatedRemote
                        return@async deduplicatedRemote
                    }
                }

                // Seamless fallback to embedded local providers if remote is unavailable
                val raw = try {
                    when (category) {
                        Category.MOVIE -> searchMovies(searchCleanQuery, effectiveYear)
                        Category.TV_SHOW -> searchTv(searchCleanQuery, TvSubFilter.TV_SHOWS, effectiveYear)
                        Category.ANIME -> searchTv(searchCleanQuery, TvSubFilter.ANIME, effectiveYear)
                        Category.BOOK -> searchBooks(searchCleanQuery, BookSubFilter.BOOKS, bookSearchType)
                        Category.MANGA -> searchBooks(searchCleanQuery, BookSubFilter.MANGA, bookSearchType)
                        Category.COMIC -> searchBooks(searchCleanQuery, BookSubFilter.COMICS, bookSearchType)
                        Category.GAME -> searchGames(searchCleanQuery)
                        Category.BOARD_GAME -> searchBoardGames(searchCleanQuery)
                    }
                } catch (e: Exception) {
                    println("Search error: ${e.message}")
                    emptyList()
                }

                val valid = raw.filter { isValidSearchResult(it) }
                val merged = deduplicate(valid)
                val ranked = merged.sortedByDescending { item ->
                    FuzzyMatcher.calculateRelevance(q, searchCleanQuery, effectiveYear, item)
                }.take(40)

                if (ranked.isNotEmpty()) {
                    searchCache[cacheKey] = ranked
                    negativeCache.remove(cacheKey)
                } else {
                    negativeCache[cacheKey] = currentTimeMillis()
                }

                ranked
            }

            inFlightRequests[cacheKey] = deferred
            try {
                deferred.await()
            } finally {
                inFlightRequests.remove(cacheKey)
            }
        }
    }

    private suspend fun searchMovies(query: String, year: String?): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val term = encodeURIComponent(query)
        val yearParam = if (!year.isNullOrBlank()) "&y=${encodeURIComponent(year)}" else ""

        // 1. Primary: iTunes Movie Search (1200x1200bb sharp artwork)
        try {
            val itunesUrl = "https://itunes.apple.com/search?term=$term&entity=movie&limit=25"
            val response = client.get(itunesUrl).bodyAsText()
            val root = json.parseToJsonElement(response).jsonObject
            val itunesResults = root["results"]?.jsonArray
            itunesResults?.forEach { elem ->
                val obj = elem.jsonObject
                val title = obj["trackName"]?.jsonPrimitive?.content ?: return@forEach
                val coverUrl = obj["artworkUrl100"]?.jsonPrimitive?.content?.replace("100x100bb", "1200x1200bb") ?: ""
                val releaseDate = obj["releaseDate"]?.jsonPrimitive?.content ?: ""
                val releaseYear = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else ""
                if (!year.isNullOrBlank() && releaseYear != year) return@forEach

                val creator = obj["artistName"]?.jsonPrimitive?.content ?: ""
                val overview = obj["longDescription"]?.jsonPrimitive?.content ?: ""
                val previewUrl = obj["trackViewUrl"]?.jsonPrimitive?.content ?: ""

                results.add(
                    SearchResult(
                        title = title,
                        releaseYear = releaseYear,
                        releaseDate = releaseDate,
                        creator = creator,
                        overview = overview,
                        coverUrl = coverUrl,
                        category = Category.MOVIE,
                        externalUrl = previewUrl
                    )
                )
            }
        } catch (e: Throwable) {
            println("iTunes movies error: ${e.message}")
        }

        // 2. Primary: IMDb Suggestion Search (Amazon CDN posters)
        try {
            val qSanitized = query.trim().lowercase().replace(Regex("""[^a-z0-9]+"""), "_")
            if (qSanitized.isNotBlank()) {
                val imdbUrl = "https://v3.sg.media-imdb.com/suggestion/x/$qSanitized.json"
                val resp = client.get(imdbUrl).bodyAsText()
                val root = json.parseToJsonElement(resp).jsonObject
                val d = root["d"]?.jsonArray
                d?.forEach { elem ->
                    val obj = elem.jsonObject
                    val qType = obj["q"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: ""
                    if (qType == "video game" || qType == "podcast") return@forEach
                    val title = obj["l"]?.jsonPrimitive?.content ?: return@forEach
                    val yr = obj["y"]?.jsonPrimitive?.contentOrNull ?: ""
                    val stars = obj["s"]?.jsonPrimitive?.contentOrNull ?: ""
                    val imgObj = obj["i"]?.jsonObject
                    var cover = imgObj?.get("imageUrl")?.jsonPrimitive?.contentOrNull ?: ""
                    if (cover.isNotBlank()) {
                        cover = cover.replace(Regex("""\._V1_.*\.jpg"""), "._V1_SX1200.jpg")
                    }
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: ""
                    results.add(
                        SearchResult(
                            title = title,
                            releaseYear = yr,
                            releaseDate = yr,
                            creator = stars,
                            coverUrl = cover,
                            category = Category.MOVIE,
                            externalUrl = if (id.isNotBlank()) "https://www.imdb.com/title/$id" else ""
                        )
                    )
                }
            }
        } catch (_: Throwable) {}

        return results
    }


    private suspend fun searchTv(query: String, filter: TvSubFilter, year: String?): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val term = encodeURIComponent(query)

        if (filter == TvSubFilter.ALL || filter == TvSubFilter.TV_SHOWS) {
            try {
                val url = "https://api.tvmaze.com/search/shows?q=$term"
                val response = client.get(url).bodyAsText()
                val array = json.parseToJsonElement(response).jsonArray

                for (elem in array) {
                    val show = elem.jsonObject["show"]?.jsonObject ?: continue
                    val title = show["name"]?.jsonPrimitive?.content ?: continue
                    val image = show["image"]?.jsonObject
                    val coverUrl = image?.get("original")?.jsonPrimitive?.content
                        ?: image?.get("medium")?.jsonPrimitive?.content ?: ""
                    val premiered = show["premiered"]?.jsonPrimitive?.content ?: ""
                    val releaseYear = if (premiered.length >= 4) premiered.substring(0, 4) else ""
                    if (!year.isNullOrBlank() && releaseYear != year) continue

                    val network = show["network"]?.jsonObject?.get("name")?.jsonPrimitive?.content
                        ?: show["webChannel"]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: ""
                    val rawSummary = show["summary"]?.jsonPrimitive?.content ?: ""
                    val overview = rawSummary.replace(Regex("<[^>]*>"), "")
                    val urlStr = show["url"]?.jsonPrimitive?.content ?: ""

                    results.add(
                        SearchResult(
                            title = title,
                            releaseYear = releaseYear,
                            releaseDate = premiered,
                            creator = network,
                            overview = overview,
                            coverUrl = coverUrl,
                            category = Category.TV_SHOW,
                            externalUrl = urlStr
                        )
                    )
                }
            } catch (_: Exception) {}
        }

        if (filter == TvSubFilter.ALL || filter == TvSubFilter.ANIME) {
            try {
                val animeUrl = "https://api.jikan.moe/v4/anime?q=$term&limit=15"
                val response = client.get(animeUrl).bodyAsText()
                val root = json.parseToJsonElement(response).jsonObject
                val data = root["data"]?.jsonArray

                if (data != null) {
                    for (elem in data) {
                        val obj = elem.jsonObject
                        val title = obj["title"]?.jsonPrimitive?.content ?: continue
                        val images = obj["images"]?.jsonObject?.get("jpg")?.jsonObject
                        val coverUrl = images?.get("large_image_url")?.jsonPrimitive?.content
                            ?: images?.get("image_url")?.jsonPrimitive?.content ?: ""
                        val yearVal = obj["year"]?.jsonPrimitive?.contentOrNull
                        val airedYear = yearVal ?: ""
                        if (!year.isNullOrBlank() && airedYear != year) continue

                        val synopsis = obj["synopsis"]?.jsonPrimitive?.contentOrNull ?: ""
                        val studio = obj["studios"]?.jsonArray?.firstOrNull()?.jsonObject?.get("name")?.jsonPrimitive?.content ?: ""
                        val extUrl = obj["url"]?.jsonPrimitive?.contentOrNull ?: ""
                        val episodesCount = obj["episodes"]?.jsonPrimitive?.intOrNull ?: 0

                        results.add(
                            SearchResult(
                                title = title,
                                releaseYear = airedYear,
                                releaseDate = airedYear,
                                creator = studio,
                                overview = synopsis,
                                coverUrl = coverUrl,
                                category = Category.TV_SHOW,
                                externalUrl = extUrl,
                                totalEpisodes = episodesCount
                            )
                        )
                    }
                }
            } catch (_: Throwable) {}
        }

        return results
    }


    suspend fun searchBooksViaCloudEngine(query: String, searchType: BookSearchType): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        try {
            val term = encodeURIComponent(query.trim())
            val typeParam = when (searchType) {
                BookSearchType.TITLE -> "&type=title"
                BookSearchType.AUTHOR -> "&type=author"
                BookSearchType.ALL -> ""
            }
            val url = "https://books-cloud-engine.minimal-lists.workers.dev/api/v1/books/search?q=$term$typeParam"
            val response = client.get(url).bodyAsText()
            val root = json.parseToJsonElement(response).jsonObject
            val arr = root["results"]?.jsonArray ?: return results
            for (elem in arr) {
                val obj = elem.jsonObject
                val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: continue
                if (title.isBlank()) continue
                val authorsArr = obj["authors"]?.jsonArray
                val authors = authorsArr?.mapNotNull { it.jsonPrimitive.contentOrNull }?.joinToString(", ") ?: ""
                val primaryAuthor = obj["primaryAuthor"]?.jsonPrimitive?.contentOrNull ?: authors
                val isbn = obj["primaryIsbn"]?.jsonPrimitive?.contentOrNull
                    ?: obj["isbn13"]?.jsonPrimitive?.contentOrNull
                    ?: obj["isbn10"]?.jsonPrimitive?.contentOrNull
                    ?: ""
                val coverUrl = obj["coverUrl"]?.jsonPrimitive?.contentOrNull
                    ?: obj["thumbnailCoverUrl"]?.jsonPrimitive?.contentOrNull
                    ?: ""
                val year = obj["publishedYear"]?.jsonPrimitive?.contentOrNull ?: ""
                val date = obj["publishedDate"]?.jsonPrimitive?.contentOrNull ?: ""
                val pages = obj["totalPages"]?.jsonPrimitive?.intOrNull ?: 0
                val publisher = obj["publisher"]?.jsonPrimitive?.contentOrNull ?: ""
                val lang = obj["language"]?.jsonPrimitive?.contentOrNull ?: ""
                val overview = obj["overview"]?.jsonPrimitive?.contentOrNull ?: ""
                val rating = obj["rating"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: 0f
                val extUrl = obj["externalUrl"]?.jsonPrimitive?.contentOrNull ?: ""

                results.add(
                    SearchResult(
                        title = title,
                        originalTitle = obj["subtitle"]?.jsonPrimitive?.contentOrNull ?: "",
                        creator = primaryAuthor,
                        coverUrl = coverUrl,
                        releaseYear = year,
                        releaseDate = date,
                        totalPages = pages,
                        overview = overview,
                        rating = rating,
                        category = Category.BOOK,
                        externalUrl = extUrl,
                        isbn = isbn,
                        publisher = publisher,
                        language = lang
                    )
                )
            }
        } catch (_: Throwable) {}
        return results
    }

    suspend fun resolveBookPages(title: String, creator: String = "", isbn: String = ""): Int {
        val cleanIsbn = isbn.replace("-", "").trim()
        if (cleanIsbn.isNotBlank()) {
            try {
                val url = "https://books-cloud-engine.minimal-lists.workers.dev/api/v1/books/isbn/$cleanIsbn"
                val response = client.get(url).bodyAsText()
                val root = json.parseToJsonElement(response).jsonObject
                if (root["success"]?.jsonPrimitive?.booleanOrNull == true) {
                    val book = root["book"]?.jsonObject
                    val pages = book?.get("totalPages")?.jsonPrimitive?.intOrNull ?: 0
                    if (pages > 0) return pages
                }
            } catch (_: Throwable) {}
        }

        try {
            val cleanTitle = cleanCanonicalTitle(title).trim()
            val cleanAuthor = creator.split(",", "/").firstOrNull()?.trim() ?: ""
            val q = if (cleanAuthor.isNotBlank()) "intitle:\"$cleanTitle\" inauthor:\"$cleanAuthor\"" else "intitle:\"$cleanTitle\""
            val encoded = encodeURIComponent(q)
            val url = "https://www.googleapis.com/books/v1/volumes?q=$encoded&maxResults=10"
            val response = client.get(url).bodyAsText()
            val root = json.parseToJsonElement(response).jsonObject
            val items = root["items"]?.jsonArray
            if (items != null) {
                val validPages = mutableListOf<Int>()
                for (elem in items) {
                    val vol = elem.jsonObject["volumeInfo"]?.jsonObject ?: continue
                    val volTitle = vol["title"]?.jsonPrimitive?.contentOrNull ?: ""
                    if (volTitle.contains("summary", ignoreCase = true) || volTitle.contains("study guide", ignoreCase = true)) continue
                    val pages = vol["pageCount"]?.jsonPrimitive?.intOrNull?.takeIf { it in 45..2500 }
                        ?: vol["printedPageCount"]?.jsonPrimitive?.intOrNull?.takeIf { it in 45..2500 }
                    if (pages != null) validPages.add(pages)
                }
                if (validPages.isNotEmpty()) {
                    validPages.sort()
                    return validPages[validPages.size / 2]
                }
            }
        } catch (_: Throwable) {}

        try {
            val encoded = encodeURIComponent(title.trim())
            val url = "https://openlibrary.org/search.json?q=$encoded&fields=number_of_pages_median,number_of_pages&limit=8"
            val response = client.get(url).bodyAsText()
            val root = json.parseToJsonElement(response).jsonObject
            val docs = root["docs"]?.jsonArray
            if (docs != null) {
                for (elem in docs) {
                    val doc = elem.jsonObject
                    val median = doc["number_of_pages_median"]?.jsonPrimitive?.intOrNull?.takeIf { it in 45..2500 }
                    if (median != null) return median

                    val pagesArr = doc["number_of_pages"]?.jsonArray
                    if (pagesArr != null) {
                        val validPages = pagesArr.mapNotNull { it.jsonPrimitive.intOrNull?.takeIf { p -> p in 45..2500 } }.sorted()
                        if (validPages.isNotEmpty()) {
                            return validPages[validPages.size / 2]
                        }
                    }
                    val singlePage = doc["number_of_pages"]?.jsonPrimitive?.intOrNull?.takeIf { it in 45..2500 }
                    if (singlePage != null) return singlePage
                }
            }
        } catch (_: Throwable) {}

        return 0
    }

    private suspend fun searchBooks(query: String, filter: BookSubFilter, searchType: BookSearchType): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val term = encodeURIComponent(query)

        if (filter == BookSubFilter.ALL || filter == BookSubFilter.BOOKS) {
            // 1. Primary: Cloudflare Book Engine
            val cloudResults = searchBooksViaCloudEngine(query, searchType)
            results.addAll(cloudResults)

            // 2. Google Books (with API key)
            try {
                val prefix = when (searchType) {
                    BookSearchType.TITLE -> "intitle:"
                    BookSearchType.AUTHOR -> "inauthor:"
                    BookSearchType.ALL -> ""
                }
                val url = "https://www.googleapis.com/books/v1/volumes?q=${prefix}$term&maxResults=20"
                val response = client.get(url).bodyAsText()
                val root = json.parseToJsonElement(response).jsonObject
                val items = root["items"]?.jsonArray

                if (items != null) {
                    for (elem in items) {
                        val info = elem.jsonObject["volumeInfo"]?.jsonObject ?: continue
                        val title = info["title"]?.jsonPrimitive?.content ?: continue
                        val authors = info["authors"]?.jsonArray?.map { it.jsonPrimitive.content }?.joinToString(", ") ?: ""
                        val publishedDate = info["publishedDate"]?.jsonPrimitive?.content ?: ""
                        val releaseYear = if (publishedDate.length >= 4) publishedDate.substring(0, 4) else ""
                        val imageLinks = info["imageLinks"]?.jsonObject
                        val rawCover = imageLinks?.get("thumbnail")?.jsonPrimitive?.content ?: ""
                        val coverUrl = rawCover.replace("http://", "https://").replace("&edge=curl", "").replace("zoom=1", "zoom=3")
                        val overview = info["description"]?.jsonPrimitive?.content ?: ""
                        val pageCount = info["pageCount"]?.jsonPrimitive?.intOrNull ?: (info["printedPageCount"]?.jsonPrimitive?.intOrNull ?: 0)
                        val previewLink = info["previewLink"]?.jsonPrimitive?.content ?: ""

                        results.add(
                            SearchResult(
                                title = title,
                                releaseYear = releaseYear,
                                releaseDate = publishedDate,
                                creator = authors,
                                overview = overview,
                                coverUrl = coverUrl,
                                category = Category.BOOK,
                                externalUrl = previewLink,
                                totalPages = pageCount
                            )
                        )
                    }
                }
            } catch (_: Throwable) {}

            // 3. OpenLibrary Search (with fields parameter)
            try {
                val fieldsParam = "&fields=key,title,subtitle,author_name,isbn,number_of_pages_median,number_of_pages,cover_i,publisher,first_publish_year,subject,ratings_average"
                val olUrl = "https://openlibrary.org/search.json?q=$term&limit=20$fieldsParam"
                val response = client.get(olUrl).bodyAsText()
                val root = json.parseToJsonElement(response).jsonObject
                val docs = root["docs"]?.jsonArray
                docs?.forEach { elem ->
                    val obj = elem.jsonObject
                    val title = obj["title"]?.jsonPrimitive?.content ?: return@forEach
                    val authors = obj["author_name"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.joinToString(", ") ?: ""
                    val firstYear = obj["first_publish_year"]?.jsonPrimitive?.contentOrNull ?: ""
                    val coverI = obj["cover_i"]?.jsonPrimitive?.contentOrNull
                    val coverUrl = if (coverI != null) "https://covers.openlibrary.org/b/id/$coverI-L.jpg" else ""
                    val key = obj["key"]?.jsonPrimitive?.content ?: ""
                    val pagesArr = obj["number_of_pages"]?.jsonArray
                    val validPages = pagesArr?.mapNotNull { it.jsonPrimitive.intOrNull?.takeIf { p -> p in 45..2500 } }?.sorted() ?: emptyList()
                    val pages = obj["number_of_pages_median"]?.jsonPrimitive?.intOrNull?.takeIf { it in 45..2500 }
                        ?: (if (validPages.isNotEmpty()) validPages[validPages.size / 2] else null)
                        ?: obj["number_of_pages"]?.jsonPrimitive?.intOrNull?.takeIf { it in 45..2500 }
                        ?: 0
                    val publisher = obj["publisher"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull ?: ""

                    results.add(
                        SearchResult(
                            title = title,
                            releaseYear = firstYear,
                            releaseDate = firstYear,
                            creator = authors,
                            coverUrl = coverUrl,
                            category = Category.BOOK,
                            externalUrl = if (key.isNotEmpty()) "https://openlibrary.org$key" else "",
                            totalPages = pages,
                            publisher = publisher
                        )
                    )
                }
            } catch (_: Throwable) {}

            // 4. Apple Books
            try {
                val appleUrl = "https://itunes.apple.com/search?term=$term&entity=ebook&limit=15"
                val response = client.get(appleUrl).bodyAsText()
                val root = json.parseToJsonElement(response).jsonObject
                val items = root["results"]?.jsonArray
                items?.forEach { elem ->
                    val obj = elem.jsonObject
                    val title = obj["trackName"]?.jsonPrimitive?.contentOrNull
                        ?: obj["trackCensoredName"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    val author = obj["artistName"]?.jsonPrimitive?.contentOrNull ?: ""
                    val artwork = obj["artworkUrl100"]?.jsonPrimitive?.contentOrNull
                        ?.replace("100x100bb", "1200x1200bb") ?: ""
                    val releaseDate = obj["releaseDate"]?.jsonPrimitive?.contentOrNull ?: ""
                    val releaseYear = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else ""
                    val overview = obj["description"]?.jsonPrimitive?.contentOrNull?.replace(Regex("<[^>]*>"), "") ?: ""
                    val rating = (obj["averageUserRating"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: 0f) * 2f
                    val viewUrl = obj["trackViewUrl"]?.jsonPrimitive?.contentOrNull ?: ""

                    results.add(
                        SearchResult(
                            title = title,
                            creator = author,
                            releaseYear = releaseYear,
                            releaseDate = releaseDate.take(10),
                            coverUrl = artwork,
                            rating = rating,
                            overview = overview,
                            category = Category.BOOK,
                            externalUrl = viewUrl
                        )
                    )
                }
            } catch (_: Throwable) {}
        }

        if (filter == BookSubFilter.ALL || filter == BookSubFilter.COMICS) {
            try {
                val appleComicUrl = "https://itunes.apple.com/search?term=${encodeURIComponent("$query comic")}&entity=ebook&limit=15"
                val resp = client.get(appleComicUrl).bodyAsText()
                val root = json.parseToJsonElement(resp).jsonObject
                root["results"]?.jsonArray?.forEach { elem ->
                    val obj = elem.jsonObject
                    val title = obj["trackName"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    val author = obj["artistName"]?.jsonPrimitive?.contentOrNull ?: ""
                    val artwork = obj["artworkUrl100"]?.jsonPrimitive?.contentOrNull?.replace("100x100bb", "1200x1200bb") ?: ""
                    val releaseDate = obj["releaseDate"]?.jsonPrimitive?.contentOrNull ?: ""
                    val releaseYear = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else ""
                    val overview = obj["description"]?.jsonPrimitive?.contentOrNull?.replace(Regex("<[^>]*>"), "") ?: ""
                    results.add(
                        SearchResult(
                            title = title,
                            creator = author,
                            releaseYear = releaseYear,
                            releaseDate = releaseDate.take(10),
                            coverUrl = artwork,
                            overview = overview,
                            category = Category.COMIC,
                            externalUrl = obj["trackViewUrl"]?.jsonPrimitive?.contentOrNull ?: ""
                        )
                    )
                }
            } catch (_: Throwable) {}

            try {
                val gbComicUrl = "https://www.googleapis.com/books/v1/volumes?q=${encodeURIComponent("$query comic")}&maxResults=10"
                val resp = client.get(gbComicUrl).bodyAsText()
                val root = json.parseToJsonElement(resp).jsonObject
                root["items"]?.jsonArray?.forEach { elem ->
                    val info = elem.jsonObject["volumeInfo"]?.jsonObject ?: return@forEach
                    val title = info["title"]?.jsonPrimitive?.content ?: return@forEach
                    val authors = info["authors"]?.jsonArray?.map { it.jsonPrimitive.content }?.joinToString(", ") ?: ""
                    val publishedDate = info["publishedDate"]?.jsonPrimitive?.content ?: ""
                    val releaseYear = if (publishedDate.length >= 4) publishedDate.substring(0, 4) else ""
                    val rawCover = info["imageLinks"]?.jsonObject?.get("thumbnail")?.jsonPrimitive?.content ?: ""
                    val coverUrl = rawCover.replace("http://", "https://").replace("&edge=curl", "").replace("zoom=1", "zoom=3")
                    val overview = info["description"]?.jsonPrimitive?.content ?: ""
                    results.add(
                        SearchResult(
                            title = title,
                            releaseYear = releaseYear,
                            releaseDate = publishedDate,
                            creator = authors,
                            overview = overview,
                            coverUrl = coverUrl,
                            category = Category.COMIC,
                            externalUrl = info["previewLink"]?.jsonPrimitive?.content ?: ""
                        )
                    )
                }
            } catch (_: Throwable) {}
        }

        if (filter == BookSubFilter.ALL || filter == BookSubFilter.MANGA) {
            try {
                val mangaUrl = "https://api.mangadex.org/manga?title=$term&limit=15&includes[]=cover_art"
                val response = client.get(mangaUrl).bodyAsText()
                val root = json.parseToJsonElement(response).jsonObject
                val data = root["data"]?.jsonArray

                if (data != null) {
                    for (elem in data) {
                        val obj = elem.jsonObject
                        val id = obj["id"]?.jsonPrimitive?.content ?: continue
                        val attr = obj["attributes"]?.jsonObject ?: continue
                        val title = attr["title"]?.jsonObject?.values?.firstOrNull()?.jsonPrimitive?.content ?: continue
                        val yearVal = attr["year"]?.jsonPrimitive?.contentOrNull ?: ""
                        val desc = attr["description"]?.jsonObject?.get("en")?.jsonPrimitive?.content ?: ""

                        val relationships = obj["relationships"]?.jsonArray ?: emptyList()
                        val coverRel = relationships.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.content == "cover_art" }
                        val fileName = coverRel?.jsonObject?.get("attributes")?.jsonObject?.get("fileName")?.jsonPrimitive?.content
                        val coverUrl = if (fileName != null) "https://uploads.mangadex.org/covers/$id/$fileName" else ""

                        results.add(
                            SearchResult(
                                title = title,
                                releaseYear = yearVal,
                                releaseDate = yearVal,
                                creator = "Manga",
                                overview = desc,
                                coverUrl = coverUrl,
                                category = Category.MANGA,
                                externalUrl = "https://mangadex.org/title/$id"
                            )
                        )
                    }
                }
            } catch (_: Throwable) {}
        }

        return results
    }

    private suspend fun searchGames(query: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val term = encodeURIComponent(query)

        // 1. Primary: IMDb Suggestion API for video games (instant + high-res Amazon posters)
        try {
            val qSanitized = query.trim().lowercase().replace(Regex("""[^a-z0-9]+"""), "_")
            if (qSanitized.isNotBlank()) {
                val imdbUrl = "https://v3.sg.media-imdb.com/suggestion/x/$qSanitized.json"
                val resp = client.get(imdbUrl).bodyAsText()
                val root = json.parseToJsonElement(resp).jsonObject
                val d = root["d"]?.jsonArray
                d?.forEach { elem ->
                    val obj = elem.jsonObject
                    val qType = obj["q"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: ""
                    if (qType == "video game" || qType == "game") {
                        val title = obj["l"]?.jsonPrimitive?.content ?: return@forEach
                        val yr = obj["y"]?.jsonPrimitive?.contentOrNull ?: ""
                        val stars = obj["s"]?.jsonPrimitive?.contentOrNull ?: ""
                        val imgObj = obj["i"]?.jsonObject
                        var cover = imgObj?.get("imageUrl")?.jsonPrimitive?.contentOrNull ?: ""
                        if (cover.isNotBlank()) {
                            cover = cover.replace(Regex("""\._V1_.*\.jpg"""), "._V1_SX1200.jpg")
                        }
                        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: ""
                        results.add(
                            SearchResult(
                                title = title,
                                releaseYear = yr,
                                releaseDate = yr,
                                creator = stars,
                                coverUrl = cover,
                                category = Category.GAME,
                                externalUrl = if (id.isNotBlank()) "https://www.imdb.com/title/$id" else ""
                            )
                        )
                    }
                }
            }
        } catch (_: Throwable) {}

        // 2. Wikipedia fallback for video games
        if (results.isEmpty()) {
            val wiki = searchWikipedia(query, Category.GAME)
            results.addAll(wiki)
        }

        return results
    }

    private suspend fun searchBoardGames(query: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val term = encodeURIComponent(query)

        // 1. Wikipedia Board Games (with thumbnail + extract)
        try {
            val wikiUrl = "https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=${encodeURIComponent("$query board game")}&gsrlimit=15&prop=pageimages|extracts|info&inprop=url&piprop=thumbnail&pithumbsize=600&exintro=1&explaintext=1&format=json"
            val resp = client.get(wikiUrl).bodyAsText()
            val root = json.parseToJsonElement(resp).jsonObject
            val queryObj = root["query"]?.jsonObject
            val pages = queryObj?.get("pages")?.jsonObject
            pages?.forEach { (_, elem) ->
                val page = elem.jsonObject
                val rawTitle = page["title"]?.jsonPrimitive?.content ?: return@forEach
                if (rawTitle.startsWith("List of", ignoreCase = true) || rawTitle.contains("(disambiguation)", ignoreCase = true)) return@forEach
                val cleanTitle = rawTitle.replace(Regex("""\s*\([^)]*(board game|game|card game)[^)]*\)""", RegexOption.IGNORE_CASE), "").trim()
                val extract = page["extract"]?.jsonPrimitive?.content ?: ""
                val thumb = page["thumbnail"]?.jsonObject?.get("source")?.jsonPrimitive?.content ?: ""
                val fullUrl = page["fullurl"]?.jsonPrimitive?.content ?: ""
                val yearMatch = Regex("""\b(19\d{2}|20\d{2})\b""").find(extract)?.value ?: ""

                results.add(
                    SearchResult(
                        title = cleanTitle,
                        releaseYear = yearMatch,
                        releaseDate = yearMatch,
                        overview = extract,
                        coverUrl = thumb,
                        category = Category.BOARD_GAME,
                        externalUrl = fullUrl
                    )
                )
            }
        } catch (_: Throwable) {}

        // 2. Wikidata Board Games (filtered by board game descriptions)
        try {
            val wdUrl = "https://www.wikidata.org/w/api.php?action=wbsearchentities&search=$term&language=en&type=item&limit=15&format=json"
            val resp = client.get(wdUrl).bodyAsText()
            val root = json.parseToJsonElement(resp).jsonObject
            val searchArr = root["search"]?.jsonArray
            searchArr?.forEach { elem ->
                val obj = elem.jsonObject
                val label = obj["label"]?.jsonPrimitive?.content ?: return@forEach
                val desc = obj["description"]?.jsonPrimitive?.contentOrNull ?: ""
                val lowerDesc = desc.lowercase()
                if (lowerDesc.contains("board game") || lowerDesc.contains("tabletop") ||
                    lowerDesc.contains("card game") || lowerDesc.contains("roll-and-move") ||
                    lowerDesc.contains("family game")) {
                    val yearMatch = Regex("""\b(19\d{2}|20\d{2})\b""").find(desc)?.value ?: ""
                    val conceptUri = obj["concepturi"]?.jsonPrimitive?.contentOrNull ?: ""
                    results.add(
                        SearchResult(
                            title = label,
                            releaseYear = yearMatch,
                            releaseDate = yearMatch,
                            overview = desc,
                            category = Category.BOARD_GAME,
                            externalUrl = conceptUri
                        )
                    )
                }
            }
        } catch (_: Throwable) {}

        return results
    }

    private suspend fun searchWikipedia(query: String, category: Category): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        try {
            val suffix = when (category) {
                Category.GAME -> " video game"
                Category.MOVIE -> " film"
                Category.TV_SHOW -> " series"
                Category.ANIME -> " anime"
                Category.BOOK -> " novel"
                Category.MANGA -> " manga"
                Category.COMIC -> " comic"
                Category.BOARD_GAME -> " board game"
            }
            val searchQuery = if (query.endsWith(suffix, ignoreCase = true)) query else "$query$suffix"
            val encoded = encodeURIComponent(searchQuery)
            val url = "https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=$encoded&gsrlimit=15&prop=pageimages|extracts|info&inprop=url&piprop=thumbnail&pithumbsize=600&exintro=1&explaintext=1&format=json"
            val resp = client.get(url).bodyAsText()
            val root = json.parseToJsonElement(resp).jsonObject
            val pages = root["query"]?.jsonObject?.get("pages")?.jsonObject
            pages?.forEach { (_, elem) ->
                val page = elem.jsonObject
                val rawTitle = page["title"]?.jsonPrimitive?.content ?: return@forEach
                if (rawTitle.startsWith("List of", ignoreCase = true) || rawTitle.contains("(disambiguation)", ignoreCase = true)) return@forEach
                val cleanTitle = rawTitle.replace(Regex("""\s*\([^)]*\)"""), "").trim()
                val extract = page["extract"]?.jsonPrimitive?.content ?: ""
                val thumb = page["thumbnail"]?.jsonObject?.get("source")?.jsonPrimitive?.content ?: ""
                val fullUrl = page["fullurl"]?.jsonPrimitive?.content ?: ""
                val yearMatch = Regex("""\b(19\d{2}|20\d{2})\b""").find(extract)?.value ?: ""

                results.add(
                    SearchResult(
                        title = cleanTitle,
                        releaseYear = yearMatch,
                        releaseDate = yearMatch,
                        overview = extract,
                        coverUrl = thumb,
                        category = category,
                        externalUrl = fullUrl
                    )
                )
            }
        } catch (_: Throwable) {}
        return results
    }


    suspend fun fetchOttPlatforms(title: String, category: Category): List<OttPlatform> {
        val results = mutableListOf<OttPlatform>()
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) return emptyList()

        val encoded = encodeURIComponent(trimmedTitle)
        var justWatchPath = ""

        // 1. Query JustWatch GraphQL for real streaming offers & direct links
        try {
            val queryStr = """
                query GetSuggestedTitles(${'$'}country: Country!, ${'$'}language: Language!, ${'$'}first: Int!, ${'$'}filter: TitleFilter) {
                  popularTitles(country: ${'$'}country, first: ${'$'}first, filter: ${'$'}filter) {
                    edges {
                      node {
                        content(country: ${'$'}country, language: ${'$'}language) {
                          title
                          fullPath
                        }
                        offers(country: ${'$'}country, platform: WEB) {
                          monetizationType
                          standardWebURL
                          package {
                            clearName
                            technicalName
                          }
                        }
                      }
                    }
                  }
                }
            """.trimIndent()

            val reqPayload = buildJsonObject {
                put("query", queryStr)
                put("variables", buildJsonObject {
                    put("country", "US")
                    put("language", "en")
                    put("first", 1)
                    put("filter", buildJsonObject {
                        put("searchQuery", trimmedTitle)
                    })
                })
            }

            val response = client.post("https://apis.justwatch.com/graphql") {
                header("Content-Type", "application/json")
                setBody(reqPayload.toString())
            }.bodyAsText()

            val root = json.parseToJsonElement(response).jsonObject
            val data = root["data"]?.jsonObject
            val popularTitles = data?.get("popularTitles")?.jsonObject
            val edges = popularTitles?.get("edges")?.jsonArray
            if (edges != null && edges.isNotEmpty()) {
                val firstEdge = edges.first().jsonObject
                val node = firstEdge["node"]?.jsonObject
                val content = node?.get("content")?.jsonObject
                justWatchPath = content?.get("fullPath")?.jsonPrimitive?.contentOrNull ?: ""

                val offers = node?.get("offers")?.jsonArray
                if (offers != null) {
                    val parsedOffers = mutableListOf<Triple<String, String, String>>()

                    for (elem in offers) {
                        val offer = elem.jsonObject
                        val mType = offer["monetizationType"]?.jsonPrimitive?.contentOrNull ?: ""
                        val webUrl = offer["standardWebURL"]?.jsonPrimitive?.contentOrNull ?: ""
                        val pkg = offer["package"]?.jsonObject
                        val clearName = pkg?.get("clearName")?.jsonPrimitive?.contentOrNull ?: ""
                        val techName = (pkg?.get("technicalName")?.jsonPrimitive?.contentOrNull ?: "").lowercase()

                        if (techName.contains("dvd") || techName.contains("bluray") ||
                            techName.contains("gruv") || techName.contains("fandango") ||
                            techName.contains("cinema") || techName.contains("arrowvideo")) {
                            continue
                        }

                        if (clearName.isNotBlank() && webUrl.isNotBlank()) {
                            parsedOffers.add(Triple(clearName, webUrl, mType))
                        }
                    }

                    val sorted = parsedOffers.sortedWith(
                        compareBy { item ->
                            when (item.third.uppercase()) {
                                "FLATRATE" -> 0
                                "FREE", "ADS", "FAST" -> 1
                                "RENT" -> 2
                                "BUY" -> 3
                                else -> 4
                            }
                        }
                    )

                    val seen = mutableSetOf<String>()
                    for (item in sorted) {
                        if (seen.add(item.first.lowercase())) {
                            results.add(OttPlatform(item.first, item.second))
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            println("JustWatch error: ${e.message}")
        }

        // 2. TVMaze official channel fallback/supplement for TV shows
        if (category == Category.TV_SHOW) {
            try {
                val url = "https://api.tvmaze.com/singlesearch/shows?q=$encoded"
                val body = client.get(url).bodyAsText()
                val root = json.parseToJsonElement(body).jsonObject
                val officialSite = root["officialSite"]?.jsonPrimitive?.contentOrNull ?: ""
                val webChannel = root["webChannel"]?.jsonObject
                val network = root["network"]?.jsonObject

                val channelName = webChannel?.get("name")?.jsonPrimitive?.contentOrNull
                    ?: network?.get("name")?.jsonPrimitive?.contentOrNull ?: ""
                val channelSite = webChannel?.get("officialSite")?.jsonPrimitive?.contentOrNull
                    ?: network?.get("officialSite")?.jsonPrimitive?.contentOrNull ?: officialSite

                if (channelName.isNotBlank() && results.none { it.name.equals(channelName, ignoreCase = true) }) {
                    val link = if (officialSite.isNotBlank()) officialSite else if (channelSite.isNotBlank()) channelSite else "https://www.google.com/search?q=watch+$encoded+$channelName"
                    results.add(OttPlatform(channelName, link))
                } else if (officialSite.isNotBlank() && results.isEmpty()) {
                    val host = try { officialSite.replace(Regex("https?://(www\\.)?"), "").substringBefore("/").substringBefore(".") } catch (_: Exception) { "Watch" }
                    results.add(OttPlatform(host.replaceFirstChar { it.uppercase() }, officialSite))
                }
            } catch (_: Throwable) {}
        }

        // 3. Fallback for Movies if no platforms found
        if (category == Category.MOVIE && results.isEmpty()) {
            results.add(OttPlatform("Google Play", "https://play.google.com/store/search?q=$encoded&c=movies"))
        }

        // 4. Official JustWatch attribution link
        val jwUrl = if (justWatchPath.isNotBlank()) {
            "https://www.justwatch.com$justWatchPath"
        } else {
            "https://www.justwatch.com/us/search?q=$encoded"
        }
        if (results.none { it.name.equals("JustWatch", ignoreCase = true) }) {
            results.add(OttPlatform("JustWatch", jwUrl))
        }

        return results
    }


    suspend fun fetchEpisodes(title: String, category: Category = Category.TV_SHOW): List<EpisodeItem> {
        if (title.isBlank()) return emptyList()
        val term = encodeURIComponent(title)

        try {
            val url = "https://api.tvmaze.com/singlesearch/shows?q=$term&embed=episodes"
            val response = client.get(url).bodyAsText()
            val root = json.parseToJsonElement(response).jsonObject
            val embedded = root["_embedded"]?.jsonObject
            val episodes = embedded?.get("episodes")?.jsonArray ?: return emptyList()

            return episodes.mapNotNull { elem ->
                val obj = elem.jsonObject
                val season = obj["season"]?.jsonPrimitive?.intOrNull ?: 1
                val number = obj["number"]?.jsonPrimitive?.intOrNull ?: 1
                val name = obj["name"]?.jsonPrimitive?.content ?: "Episode $number"
                val airdate = obj["airdate"]?.jsonPrimitive?.content ?: ""
                val airstamp = obj["airstamp"]?.jsonPrimitive?.content ?: ""

                EpisodeItem(
                    season = season,
                    number = number,
                    name = name,
                    airdate = airdate,
                    airstamp = airstamp
                )
            }
        } catch (_: Exception) {
            return emptyList()
        }
    }

    suspend fun fetchBookOverview(title: String): String {
        if (title.isBlank()) return ""
        val term = encodeURIComponent(title)
        return try {
            val url = "https://www.googleapis.com/books/v1/volumes?q=$term&maxResults=1"
            val response = client.get(url).bodyAsText()
            val root = json.parseToJsonElement(response).jsonObject
            val items = root["items"]?.jsonArray
            val first = items?.firstOrNull()?.jsonObject
            val info = first?.get("volumeInfo")?.jsonObject
            val desc = info?.get("description")?.jsonPrimitive?.content ?: ""
            desc.replace(Regex("<[^>]*>"), "")
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun fetchMangaChapterCount(title: String): Int {
        if (title.isBlank()) return 0
        val term = encodeURIComponent(title)
        return try {
            val url = "https://api.mangadex.org/manga?title=$term&limit=1"
            val response = client.get(url).bodyAsText()
            val root = json.parseToJsonElement(response).jsonObject
            val data = root["data"]?.jsonArray
            val first = data?.firstOrNull()?.jsonObject
            val attr = first?.get("attributes")?.jsonObject
            val lastChapter = attr?.get("lastChapter")?.jsonPrimitive?.contentOrNull
            lastChapter?.toDoubleOrNull()?.toInt() ?: 0
        } catch (_: Exception) {
            0
        }
    }

    suspend fun fetchCover(title: String, category: Category): String? {
        val results = search(title, category)
        return results.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl
    }
}
