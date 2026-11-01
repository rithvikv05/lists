package com.lists.app.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import com.lists.app.data.model.Category
import com.lists.app.data.model.SearchResult
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

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

data class EpisodeItem(
    val season: Int,
    val number: Int,
    val name: String,
    val airdate: String = ""
) {
    val key: String get() = "S${season}E${number}"
}

class SearchRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetchOttPlatforms(title: String, category: Category): List<OttPlatform> = withContext(Dispatchers.IO) {
        val results = mutableListOf<OttPlatform>()
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) return@withContext emptyList()

        val encoded = URLEncoder.encode(trimmedTitle, "UTF-8")
        val country = try {
            val c = Locale.getDefault().country
            if (c.length == 2) c.uppercase() else "US"
        } catch (_: Exception) {
            "US"
        }
        val language = try {
            val l = Locale.getDefault().language
            if (l.length == 2) l.lowercase() else "en"
        } catch (_: Exception) {
            "en"
        }

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

            val filterObj = JSONObject().apply {
                put("searchQuery", trimmedTitle)
            }
            val variablesObj = JSONObject().apply {
                put("country", country)
                put("language", language)
                put("first", 1)
                put("filter", filterObj)
            }
            val requestJson = JSONObject().apply {
                put("query", queryStr)
                put("variables", variablesObj)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url("https://apis.justwatch.com/graphql")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10)")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val root = JSONObject(body)
                    val data = root.optJSONObject("data")
                    val popularTitles = data?.optJSONObject("popularTitles")
                    val edges = popularTitles?.optJSONArray("edges")
                    if (edges != null && edges.length() > 0) {
                        val firstEdge = edges.getJSONObject(0)
                        val node = firstEdge.optJSONObject("node")
                        val content = node?.optJSONObject("content")
                        justWatchPath = content?.optString("fullPath", "") ?: ""

                        val offers = node?.optJSONArray("offers")
                        if (offers != null) {
                            val parsedOffers = mutableListOf<Triple<String, String, String>>()
                            for (i in 0 until offers.length()) {
                                val offer = offers.getJSONObject(i)
                                val mType = offer.optString("monetizationType", "")
                                val webUrl = offer.optString("standardWebURL", "")
                                val pkg = offer.optJSONObject("package")
                                val clearName = pkg?.optString("clearName", "") ?: ""
                                val techName = pkg?.optString("technicalName", "") ?: ""

                                // Filter out physical media / cinema ticketing
                                val lowerTech = techName.lowercase()
                                if (lowerTech.contains("dvd") || lowerTech.contains("bluray") ||
                                    lowerTech.contains("gruv") || lowerTech.contains("fandango") ||
                                    lowerTech.contains("cinema") || lowerTech.contains("arrowvideo")) {
                                    continue
                                }

                                if (clearName.isNotBlank() && webUrl.isNotBlank()) {
                                    parsedOffers.add(Triple(clearName, webUrl, mType))
                                }
                            }

                            // Prioritize subscription streaming (FLATRATE, FREE, ADS) over rent/buy
                            val sorted = parsedOffers.sortedWith(
                                compareBy { triple ->
                                    when (triple.third.uppercase()) {
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
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. TVMaze official channel fallback/supplement for TV shows
        if (category == Category.TV_SHOW) {
            try {
                val url = "https://api.tvmaze.com/singlesearch/shows?q=$encoded"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val root = JSONObject(body)
                        val officialSite = root.optString("officialSite", "")
                        val webChannel = root.optJSONObject("webChannel")
                        val network = root.optJSONObject("network")

                        val channelName = webChannel?.optString("name") ?: network?.optString("name") ?: ""
                        val channelSite = webChannel?.optString("officialSite") ?: network?.optString("officialSite") ?: officialSite

                        if (channelName.isNotBlank() && results.none { it.name.equals(channelName, ignoreCase = true) }) {
                            val link = if (officialSite.isNotBlank()) officialSite else if (channelSite.isNotBlank()) channelSite else "https://www.google.com/search?q=watch+$encoded+$channelName"
                            results.add(OttPlatform(channelName, link))
                        } else if (officialSite.isNotBlank() && results.isEmpty()) {
                            val host = try { java.net.URI(officialSite).host.replace("www.", "").substringBefore(".") } catch (_: Exception) { "Watch" }
                            results.add(OttPlatform(host.replaceFirstChar { it.uppercase() }, officialSite))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Fallback for Movies if no platforms found
        if (category == Category.MOVIE && results.isEmpty()) {
            results.add(OttPlatform("Google Play", "https://play.google.com/store/search?q=$encoded&c=movies"))
        }

        // 4. Official JustWatch attribution link (with direct country path if available)
        val jwUrl = if (justWatchPath.isNotBlank()) {
            "https://www.justwatch.com$justWatchPath"
        } else {
            "https://www.justwatch.com/${country.lowercase()}/search?q=$encoded"
        }
        results.add(OttPlatform("JustWatch", jwUrl))

        results.distinctBy { it.name }
    }

    suspend fun fetchEpisodes(title: String): List<EpisodeItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<EpisodeItem>()
        try {
            val encoded = URLEncoder.encode(title.trim(), "UTF-8")
            val url = "https://api.tvmaze.com/singlesearch/shows?q=$encoded&embed=episodes"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "ListyClone/1.0")
                .build()

            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val root = JSONObject(body)
                    val embedded = root.optJSONObject("_embedded")
                    val epsArr = embedded?.optJSONArray("episodes") ?: JSONArray()
                    for (i in 0 until epsArr.length()) {
                        val ep = epsArr.getJSONObject(i)
                        val season = ep.optInt("season", 1)
                        val number = ep.optInt("number", i + 1)
                        val name = ep.optString("name", "Episode $number")
                        val airdate = ep.optString("airdate", "")
                        results.add(EpisodeItem(season, number, name, airdate))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        results
    }

    private fun matchesYear(itemYear: String, filterYear: String): Boolean {
        if (itemYear.isBlank()) return false
        val cleanFilter = filterYear.trim()
        if (cleanFilter.endsWith("s", ignoreCase = true) && cleanFilter.length == 5) {
            val decadeStart = cleanFilter.dropLast(1).toIntOrNull()
            val parsedYear = itemYear.take(4).toIntOrNull()
            if (decadeStart != null && parsedYear != null) {
                return parsedYear in decadeStart..(decadeStart + 9)
            }
        }
        return itemYear.contains(cleanFilter)
    }

    suspend fun search(
        query: String,
        category: Category,
        bookSubFilter: BookSubFilter = BookSubFilter.ALL,
        tvSubFilter: TvSubFilter = TvSubFilter.ALL,
        bookSearchType: BookSearchType = BookSearchType.ALL,
        yearFilter: String? = null,
        langFilter: String? = null
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            val scraped = scrapeUrl(trimmed, category)
            if (scraped != null) {
                return@withContext listOf(scraped)
            }
        }

        val rawResults = try {
            when (category) {
                Category.MOVIE -> searchMovies(trimmed, yearFilter, langFilter)
                Category.TV_SHOW -> searchTvAndAnime(trimmed, tvSubFilter, yearFilter, langFilter)
                Category.BOOK -> searchBooksAndManga(trimmed, bookSubFilter, bookSearchType, yearFilter, langFilter)
                Category.GAME -> searchGames(trimmed, yearFilter, langFilter)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                searchWikipedia(trimmed, category)
            } catch (ex: Exception) {
                emptyList()
            }
        }

        val yearFiltered = if (!yearFilter.isNullOrBlank()) {
            val (matched, unmatched) = rawResults.partition {
                matchesYear(it.releaseYear, yearFilter) || matchesYear(it.releaseDate, yearFilter)
            }
            if (matched.isNotEmpty()) matched + unmatched else rawResults
        } else {
            rawResults
        }

        // Relevance scoring & smart sorting so best matches always appear first
        val lowerQ = trimmed.lowercase()
        val sorted = yearFiltered.sortedWith(
            compareByDescending<SearchResult> { item ->
                val titleLower = item.title.lowercase()
                var score = 0
                if (titleLower == lowerQ) score += 100
                else if (titleLower.startsWith(lowerQ)) score += 50
                else if (titleLower.contains(lowerQ)) score += 25
                if (item.creator.lowercase().contains(lowerQ)) score += 30
                if (item.coverUrl.isNotBlank()) score += 10
                if (item.rating > 0f) score += 5
                score
            }
        )
        sorted.take(40)
    }

    // -------------------------------------------------------------
    // UNIVERSAL LINK SCRAPER & INGESTION
    // -------------------------------------------------------------
    suspend fun scrapeUrl(rawUrl: String, defaultCategory: Category): SearchResult? = withContext(Dispatchers.IO) {
        val trimmed = rawUrl.trim()
        try {
            // 0. Goodreads URL: https://www.goodreads.com/book/show/2767052...
            if (trimmed.contains("goodreads.com/book/show/", ignoreCase = true)) {
                val goodreadsResult = scrapeGoodreads(trimmed)
                if (goodreadsResult != null) return@withContext goodreadsResult
            }

            // 1. IMDb URL: https://www.imdb.com/title/tt0111161/...
            val imdbMatch = Regex("imdb\\.com/title/(tt\\d+)", RegexOption.IGNORE_CASE).find(trimmed)
            if (imdbMatch != null) {
                val imdbId = imdbMatch.groupValues[1]
                val url = "https://www.omdbapi.com/?i=$imdbId&apikey=trilogy"
                val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = JSONObject(response.body?.string() ?: "")
                        if (json.optString("Response") == "True") {
                            val title = json.optString("Title")
                            val year = json.optString("Year")
                            var poster = json.optString("Poster")
                            if (poster == "N/A") poster = ""
                            val plot = json.optString("Plot")
                            val rating = json.optDouble("imdbRating", 0.0).toFloat()
                            val type = json.optString("Type")
                            val cat = if (type.equals("series", ignoreCase = true)) Category.TV_SHOW else Category.MOVIE
                            return@withContext SearchResult(
                                title = title,
                                releaseYear = year,
                                coverUrl = poster,
                                overview = plot,
                                rating = rating,
                                category = cat,
                                externalUrl = "https://www.imdb.com/title/$imdbId"
                            )
                        }
                    }
                }
            }

            // 2. MangaDex URL: https://mangadex.org/title/a1c7c817-4e59-43b7-9365-09675a149a6f/...
            val mangadexMatch = Regex("mangadex\\.org/title/([a-f0-9\\-]+)", RegexOption.IGNORE_CASE).find(trimmed)
            if (mangadexMatch != null) {
                val mangaId = mangadexMatch.groupValues[1]
                val url = "https://api.mangadex.org/manga/$mangaId?includes[]=cover_art"
                val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val root = JSONObject(response.body?.string() ?: "")
                        val data = root.optJSONObject("data")
                        if (data != null) {
                            val attrs = data.optJSONObject("attributes") ?: JSONObject()
                            val titleObj = attrs.optJSONObject("title")
                            val title = titleObj?.optString("en")?.takeIf { it.isNotBlank() }
                                ?: titleObj?.optString("ja-ro")?.takeIf { it.isNotBlank() }
                                ?: titleObj?.keys()?.asSequence()?.firstOrNull()?.let { titleObj.optString(it) }
                                ?: "Manga"
                            val desc = cleanHtml(attrs.optJSONObject("description")?.optString("en", "") ?: "")
                            val year = attrs.optInt("year", 0).let { if (it > 0) it.toString() else "" }
                            val lastChapterStr = attrs.optString("lastChapter", "")
                            var chapters = lastChapterStr.toDoubleOrNull()?.toInt() ?: 0
                            if (chapters <= 0) {
                                chapters = fetchMangaDexLatestChapter(mangaId)
                            }

                            val relationships = data.optJSONArray("relationships") ?: JSONArray()
                            var coverFileName = ""
                            for (rIdx in 0 until relationships.length()) {
                                val rel = relationships.getJSONObject(rIdx)
                                if (rel.optString("type") == "cover_art") {
                                    coverFileName = rel.optJSONObject("attributes")?.optString("fileName", "") ?: ""
                                    if (coverFileName.isNotBlank()) break
                                }
                            }
                            val coverUrl = if (coverFileName.isNotBlank()) {
                                "https://uploads.mangadex.org/covers/$mangaId/$coverFileName.512.jpg"
                            } else ""

                            return@withContext SearchResult(
                                title = title,
                                releaseYear = year,
                                coverUrl = coverUrl,
                                overview = desc,
                                category = Category.BOOK,
                                externalUrl = "https://mangadex.org/title/$mangaId",
                                totalPages = chapters
                            )
                        }
                    }
                }
            }

            // 3. AniList URL: https://anilist.co/anime/21/... or /manga/30013/...
            val anilistMatch = Regex("anilist\\.co/(anime|manga)/(\\d+)", RegexOption.IGNORE_CASE).find(trimmed)
            if (anilistMatch != null) {
                val typeStr = anilistMatch.groupValues[1].uppercase()
                val id = anilistMatch.groupValues[2].toIntOrNull() ?: 0
                if (id > 0) {
                    val graphql = """
                        {"query": "query (${'$'}id: Int) { Media(id: ${'$'}id) { id title { romaji english } coverImage { extraLarge } episodes chapters description seasonYear averageScore } }", "variables": {"id": $id}}
                    """.trimIndent()
                    val req = Request.Builder()
                        .url("https://graphql.anilist.co")
                        .header("Content-Type", "application/json")
                        .header("User-Agent", "Mozilla/5.0")
                        .post(graphql.toRequestBody("application/json".toMediaType()))
                        .build()
                    client.newCall(req).execute().use { response ->
                        if (response.isSuccessful) {
                            val root = JSONObject(response.body?.string() ?: "")
                            val m = root.optJSONObject("data")?.optJSONObject("Media")
                            if (m != null) {
                                val titleObj = m.optJSONObject("title")
                                val title = titleObj?.optString("english")?.takeIf { it.isNotBlank() && it != "null" }
                                    ?: titleObj?.optString("romaji")?.takeIf { it.isNotBlank() && it != "null" }
                                    ?: "Anime/Manga"
                                val cover = m.optJSONObject("coverImage")?.optString("extraLarge") ?: ""
                                val eps = m.optInt("episodes", 0)
                                val chs = m.optInt("chapters", 0)
                                val score = m.optDouble("averageScore", 0.0).toFloat() / 10f
                                val year = m.optString("seasonYear", "")
                                val desc = cleanHtml(m.optString("description", ""))
                                val cat = if (typeStr == "MANGA") Category.BOOK else Category.TV_SHOW
                                return@withContext SearchResult(
                                    title = title,
                                    releaseYear = if (year != "null") year else "",
                                    coverUrl = cover,
                                    rating = score,
                                    overview = desc,
                                    category = cat,
                                    externalUrl = "https://anilist.co/${typeStr.lowercase()}/$id",
                                    totalEpisodes = eps,
                                    totalPages = chs
                                )
                            }
                        }
                    }
                }
            }

            // 4. Wikipedia URL: https://en.wikipedia.org/wiki/Elden_Ring
            val wikiMatch = Regex("wikipedia\\.org/wiki/([^?#]+)", RegexOption.IGNORE_CASE).find(trimmed)
            if (wikiMatch != null) {
                val wikiTitle = wikiMatch.groupValues[1]
                val decodedTitle = java.net.URLDecoder.decode(wikiTitle, "UTF-8").replace("_", " ")
                val results = searchWikipedia(decodedTitle, defaultCategory)
                if (results.isNotEmpty()) {
                    return@withContext results.first()
                }
            }

            // 5. Generic Website Scraper: HTML parsing (JSON-LD, OpenGraph, Twitter, Title)
            val req = Request.Builder()
                .url(trimmed)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()
            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""
                    var title = ""
                    var coverUrl = ""
                    var overview = ""
                    var releaseYear = ""

                    // Try JSON-LD first
                    val jsonLdRegex = Regex("<script[^>]*type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
                    for (match in jsonLdRegex.findAll(html)) {
                        try {
                            val jsonStr = match.groupValues[1].trim()
                            val jsonObj = if (jsonStr.startsWith("[")) {
                                JSONArray(jsonStr).optJSONObject(0)
                            } else {
                                JSONObject(jsonStr)
                            }
                            if (jsonObj != null) {
                                if (title.isBlank()) {
                                    title = jsonObj.optString("name").ifEmpty { jsonObj.optString("headline") }
                                }
                                if (coverUrl.isBlank()) {
                                    val img = jsonObj.opt("image")
                                    if (img is String) coverUrl = img
                                    else if (img is JSONObject) coverUrl = img.optString("url")
                                    else if (img is JSONArray && img.length() > 0) coverUrl = img.getString(0)
                                }
                                if (overview.isBlank()) {
                                    overview = jsonObj.optString("description")
                                }
                                if (releaseYear.isBlank()) {
                                    val date = jsonObj.optString("datePublished").ifEmpty { jsonObj.optString("copyrightYear") }
                                    val yMatch = Regex("\\b(19\\d{2}|20\\d{2})\\b").find(date)
                                    if (yMatch != null) releaseYear = yMatch.value
                                }
                            }
                        } catch (_: Exception) {}
                        if (title.isNotBlank()) break
                    }

                    // OpenGraph fallback
                    if (title.isBlank()) {
                        val ogTitleMatch = Regex("<meta[^>]+property=[\"']og:title[\"'][^>]+content=[\"'](.*?)[\"']", RegexOption.IGNORE_CASE).find(html)
                            ?: Regex("<meta[^>]+content=[\"'](.*?)[\"'][^>]+property=[\"']og:title[\"']", RegexOption.IGNORE_CASE).find(html)
                        title = ogTitleMatch?.groupValues?.get(1) ?: ""
                    }
                    if (title.isBlank()) {
                        val htmlTitleMatch = Regex("<title[^>]*>(.*?)</title>", RegexOption.IGNORE_CASE).find(html)
                        title = htmlTitleMatch?.groupValues?.get(1)?.substringBefore(" - ")?.substringBefore(" | ")?.trim() ?: ""
                    }

                    if (coverUrl.isBlank()) {
                        val ogImgMatch = Regex("<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"'](.*?)[\"']", RegexOption.IGNORE_CASE).find(html)
                            ?: Regex("<meta[^>]+content=[\"'](.*?)[\"'][^>]+property=[\"']og:image[\"']", RegexOption.IGNORE_CASE).find(html)
                            ?: Regex("<meta[^>]+name=[\"']twitter:image[\"'][^>]+content=[\"'](.*?)[\"']", RegexOption.IGNORE_CASE).find(html)
                        coverUrl = ogImgMatch?.groupValues?.get(1) ?: ""
                    }

                    if (overview.isBlank()) {
                        val ogDescMatch = Regex("<meta[^>]+property=[\"']og:description[\"'][^>]+content=[\"'](.*?)[\"']", RegexOption.IGNORE_CASE).find(html)
                            ?: Regex("<meta[^>]+content=[\"'](.*?)[\"'][^>]+property=[\"']og:description[\"']", RegexOption.IGNORE_CASE).find(html)
                            ?: Regex("<meta[^>]+name=[\"']description[\"'][^>]+content=[\"'](.*?)[\"']", RegexOption.IGNORE_CASE).find(html)
                        overview = ogDescMatch?.groupValues?.get(1) ?: ""
                    }

                    if (title.isNotBlank()) {
                        if (coverUrl.isNotBlank() && !coverUrl.startsWith("http://") && !coverUrl.startsWith("https://")) {
                            try {
                                val baseUri = java.net.URI(trimmed)
                                coverUrl = baseUri.resolve(coverUrl).toString()
                            } catch (_: Exception) {}
                        }

                        return@withContext SearchResult(
                            title = cleanHtml(title),
                            releaseYear = releaseYear,
                            coverUrl = coverUrl,
                            overview = cleanHtml(overview),
                            category = defaultCategory,
                            externalUrl = trimmed
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    private fun scrapeGoodreads(url: String): SearchResult? {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""

                    // 1. Try JSON-LD schema
                    val jsonLdMatch = Regex("<script[^>]*type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL).findAll(html)
                    for (match in jsonLdMatch) {
                        try {
                            val jsonStr = match.groupValues[1].trim()
                            val obj = if (jsonStr.startsWith("[")) JSONArray(jsonStr).optJSONObject(0) else JSONObject(jsonStr)
                            if (obj != null && (obj.optString("@type") == "Book" || obj.has("name"))) {
                                val title = obj.optString("name")
                                val image = obj.optString("image")
                                val pages = obj.optInt("numberOfPages", 0)
                                val ratingObj = obj.optJSONObject("aggregateRating")
                                val rating = (ratingObj?.optDouble("ratingValue", 0.0)?.toFloat() ?: 0f) * 2f
                                var authorName = ""
                                val authorArr = obj.optJSONArray("author")
                                if (authorArr != null && authorArr.length() > 0) {
                                    authorName = authorArr.getJSONObject(0).optString("name")
                                } else {
                                    val authorObj = obj.optJSONObject("author")
                                    authorName = authorObj?.optString("name") ?: ""
                                }

                                val ogDescMatch = Regex("<meta[^>]+property=[\"']og:description[\"'][^>]+content=[\"'](.*?)[\"']", RegexOption.IGNORE_CASE).find(html)
                                val desc = ogDescMatch?.groupValues?.get(1) ?: obj.optString("description")

                                if (title.isNotBlank()) {
                                    return SearchResult(
                                        title = cleanHtml(title).substringBefore(" (").trim(),
                                        creator = authorName,
                                        coverUrl = image,
                                        rating = rating,
                                        overview = cleanHtml(desc),
                                        category = Category.BOOK,
                                        externalUrl = url,
                                        totalPages = pages
                                    )
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    // 2. OpenGraph fallback
                    val ogTitleMatch = Regex("<meta[^>]+property=[\"']og:title[\"'][^>]+content=[\"'](.*?)[\"']", RegexOption.IGNORE_CASE).find(html)
                    val ogImageMatch = Regex("<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"'](.*?)[\"']", RegexOption.IGNORE_CASE).find(html)
                    val ogDescMatch = Regex("<meta[^>]+property=[\"']og:description[\"'][^>]+content=[\"'](.*?)[\"']", RegexOption.IGNORE_CASE).find(html)

                    val rawTitle = ogTitleMatch?.groupValues?.get(1) ?: ""
                    val image = ogImageMatch?.groupValues?.get(1) ?: ""
                    val desc = ogDescMatch?.groupValues?.get(1) ?: ""

                    if (rawTitle.isNotBlank()) {
                        val title = rawTitle.substringBefore(" (").trim()
                        return SearchResult(
                            title = title,
                            coverUrl = image,
                            overview = cleanHtml(desc),
                            category = Category.BOOK,
                            externalUrl = url
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Fallback: Parse title slug from URL (e.g. goodreads.com/book/show/2767052-the-hunger-games) and search Google Books
        val slugMatch = Regex("goodreads\\.com/book/show/\\d+[-_]([^?#/]+)", RegexOption.IGNORE_CASE).find(url)
        if (slugMatch != null) {
            val slug = slugMatch.groupValues[1].replace("-", " ").replace("_", " ").trim()
            val books = searchGoogleBooks(slug)
            if (books.isNotEmpty()) {
                return books.first().copy(externalUrl = url)
            }
        }
        return null
    }

    // -------------------------------------------------------------
    // MOVIES: Multi-page OMDb (30-40 results) with Wikipedia fallback
    // -------------------------------------------------------------
    private suspend fun searchMovies(query: String, yearFilter: String? = null, langFilter: String? = null): List<SearchResult> = coroutineScope {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val yearParam = if (!yearFilter.isNullOrBlank()) "&y=${URLEncoder.encode(yearFilter.trim(), "UTF-8")}" else ""

        val deferredPages = (1..3).map { page ->
            async {
                val results = mutableListOf<SearchResult>()
                try {
                    val url = "https://www.omdbapi.com/?s=$encoded$yearParam&type=movie&page=$page&apikey=trilogy"
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", "ListyClone/1.0")
                        .build()

                    client.newCall(req).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: ""
                            val json = JSONObject(body)
                            if (json.optString("Response") == "True") {
                                val searchArr = json.optJSONArray("Search") ?: JSONArray()
                                for (i in 0 until searchArr.length()) {
                                    val item = searchArr.getJSONObject(i)
                                    val title = item.optString("Title")
                                    val year = item.optString("Year")
                                    var poster = item.optString("Poster")
                                    if (poster == "N/A") poster = ""
                                    val imdbId = item.optString("imdbID")

                                    results.add(
                                        SearchResult(
                                            title = title,
                                            releaseYear = year,
                                            coverUrl = poster,
                                            category = Category.MOVIE,
                                            externalUrl = if (imdbId.isNotEmpty()) "https://www.imdb.com/title/$imdbId" else ""
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
                results
            }
        }

        val allOmdb = deferredPages.awaitAll().flatten().distinctBy { it.externalUrl.ifEmpty { it.title } }
        if (allOmdb.size < 15) {
            val wiki = searchWikipedia(query, Category.MOVIE)
            (allOmdb + wiki).distinctBy { it.title.lowercase().trim() }
        } else {
            allOmdb
        }
    }

    // -------------------------------------------------------------
    // TV SHOWS & ANIME: TVMaze + AniList + OMDb Series (Parallel, 30-40 items)
    // -------------------------------------------------------------
    private suspend fun searchTvAndAnime(
        query: String,
        tvSubFilter: TvSubFilter,
        yearFilter: String? = null,
        langFilter: String? = null
    ): List<SearchResult> = coroutineScope {
        val deferredList = when (tvSubFilter) {
            TvSubFilter.TV_SHOWS -> listOf(
                async { searchTvMaze(query, yearFilter) },
                async { searchOmdbSeries(query, yearFilter) }
            )
            TvSubFilter.ANIME -> listOf(
                async { searchAniListAnime(query, yearFilter) }
            )
            TvSubFilter.ALL -> listOf(
                async { searchAniListAnime(query, yearFilter) },
                async { searchTvMaze(query, yearFilter) },
                async { searchOmdbSeries(query, yearFilter) }
            )
        }

        val allResults = deferredList.awaitAll().flatten()
        val seen = mutableSetOf<String>()
        val deduped = mutableListOf<SearchResult>()
        for (item in allResults) {
            val key = item.title.lowercase().replace(Regex("[^a-z0-9]"), "")
            if (key.isNotBlank() && seen.add(key)) {
                deduped.add(item)
            }
        }
        deduped
    }

    private fun searchAniListAnime(query: String, yearFilter: String? = null): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        try {
            val graphql = """
                {"query": "query (${'$'}search: String) { Page(perPage: 30) { media(search: ${'$'}search, type: ANIME) { id title { romaji english native } coverImage { extraLarge large } episodes averageScore seasonYear description genres } } }", "variables": {"search": "$query"}}
            """.trimIndent()

            val req = Request.Builder()
                .url("https://graphql.anilist.co")
                .header("Content-Type", "application/json")
                .header("User-Agent", "ListyClone/1.0")
                .post(graphql.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val root = JSONObject(body)
                    val data = root.optJSONObject("data")
                    val page = data?.optJSONObject("Page")
                    val media = page?.optJSONArray("media") ?: JSONArray()
                    for (i in 0 until media.length()) {
                        val m = media.getJSONObject(i)
                        val titleObj = m.optJSONObject("title")
                        val eng = titleObj?.optString("english")?.takeIf { it.isNotEmpty() && it != "null" }
                        val romaji = titleObj?.optString("romaji")?.takeIf { it.isNotEmpty() && it != "null" }
                        val title = eng ?: romaji ?: "Anime"
                        val orig = if (eng != null && romaji != null && eng != romaji) romaji else ""
                        val coverObj = m.optJSONObject("coverImage")
                        val cover = coverObj?.optString("extraLarge") ?: coverObj?.optString("large") ?: ""
                        val eps = m.optInt("episodes", 0)
                        val score = m.optDouble("averageScore", 0.0).toFloat() / 10f
                        val year = m.optString("seasonYear")
                        val desc = cleanHtml(m.optString("description"))
                        val id = m.optInt("id")

                        results.add(
                            SearchResult(
                                title = title,
                                originalTitle = orig,
                                releaseYear = if (year != "null") year else "",
                                coverUrl = cover,
                                rating = score,
                                overview = desc,
                                category = Category.TV_SHOW,
                                externalUrl = "https://anilist.co/anime/$id",
                                totalEpisodes = eps
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    private fun searchTvMaze(query: String, yearFilter: String? = null): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.tvmaze.com/search/shows?q=$encoded"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "ListyClone/1.0")
                .build()

            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val arr = JSONArray(body)
                    for (i in 0 until minOf(arr.length(), 30)) {
                        val item = arr.getJSONObject(i)
                        val show = item.optJSONObject("show") ?: continue
                        val name = show.optString("name")
                        val premiered = show.optString("premiered")
                        val year = if (premiered.length >= 4) premiered.substring(0, 4) else ""
                        val ratingObj = show.optJSONObject("rating")
                        val rating = ratingObj?.optDouble("average", 0.0)?.toFloat() ?: 0f
                        val imgObj = show.optJSONObject("image")
                        val cover = imgObj?.optString("original") ?: imgObj?.optString("medium") ?: ""
                        val summary = cleanHtml(show.optString("summary"))
                        val showUrl = show.optString("url")

                        results.add(
                            SearchResult(
                                title = name,
                                releaseYear = year,
                                releaseDate = premiered,
                                coverUrl = cover,
                                rating = rating,
                                overview = summary,
                                category = Category.TV_SHOW,
                                externalUrl = showUrl
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    private suspend fun searchOmdbSeries(query: String, yearFilter: String? = null): List<SearchResult> = coroutineScope {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val yearParam = if (!yearFilter.isNullOrBlank()) "&y=${URLEncoder.encode(yearFilter.trim(), "UTF-8")}" else ""

        val deferredPages = (1..2).map { page ->
            async {
                val results = mutableListOf<SearchResult>()
                try {
                    val url = "https://www.omdbapi.com/?s=$encoded$yearParam&type=series&page=$page&apikey=trilogy"
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", "ListyClone/1.0")
                        .build()

                    client.newCall(req).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: ""
                            val json = JSONObject(body)
                            if (json.optString("Response") == "True") {
                                val searchArr = json.optJSONArray("Search") ?: JSONArray()
                                for (i in 0 until searchArr.length()) {
                                    val item = searchArr.getJSONObject(i)
                                    val title = item.optString("Title")
                                    val year = item.optString("Year")
                                    var poster = item.optString("Poster")
                                    if (poster == "N/A") poster = ""
                                    val imdbId = item.optString("imdbID")

                                    results.add(
                                        SearchResult(
                                            title = title,
                                            releaseYear = year,
                                            coverUrl = poster,
                                            category = Category.TV_SHOW,
                                            externalUrl = if (imdbId.isNotEmpty()) "https://www.imdb.com/title/$imdbId" else ""
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
                results
            }
        }
        deferredPages.awaitAll().flatten()
    }

    // -------------------------------------------------------------
    // BOOKS & MANGA: MangaDex + AniList Manga + Google Books + Open Library (30-40 items)
    // -------------------------------------------------------------
    fun searchMangaDex(query: String, langFilter: String? = null): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val langParam = if (!langFilter.isNullOrBlank()) "&availableTranslatedLanguage[]=${URLEncoder.encode(langFilter.lowercase(), "UTF-8")}" else ""
            val url = "https://api.mangadex.org/manga?title=$encoded&limit=30&includes[]=cover_art$langParam"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "ListyClone/1.0 (contact@example.com)")
                .build()

            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val root = JSONObject(body)
                    val data = root.optJSONArray("data") ?: JSONArray()
                    for (i in 0 until data.length()) {
                        val m = data.getJSONObject(i)
                        val mangaId = m.optString("id")
                        val attrs = m.optJSONObject("attributes") ?: JSONObject()
                        val titleObj = attrs.optJSONObject("title")
                        val title = titleObj?.optString("en")?.takeIf { it.isNotBlank() }
                            ?: titleObj?.optString("ja-ro")?.takeIf { it.isNotBlank() }
                            ?: titleObj?.keys()?.asSequence()?.firstOrNull()?.let { titleObj.optString(it) }
                            ?: "Manga"
                        val descObj = attrs.optJSONObject("description")
                        val desc = cleanHtml(descObj?.optString("en", "") ?: "")
                        val year = attrs.optInt("year", 0).let { if (it > 0) it.toString() else "" }
                        val lastChapterStr = attrs.optString("lastChapter", "")
                        val chapters = lastChapterStr.toDoubleOrNull()?.toInt() ?: 0

                        val relationships = m.optJSONArray("relationships") ?: JSONArray()
                        var coverFileName = ""
                        for (rIdx in 0 until relationships.length()) {
                            val rel = relationships.getJSONObject(rIdx)
                            if (rel.optString("type") == "cover_art") {
                                coverFileName = rel.optJSONObject("attributes")?.optString("fileName", "") ?: ""
                                if (coverFileName.isNotBlank()) break
                            }
                        }
                        val coverUrl = if (coverFileName.isNotBlank()) {
                            "https://uploads.mangadex.org/covers/$mangaId/$coverFileName.512.jpg"
                        } else ""

                        results.add(
                            SearchResult(
                                title = title,
                                releaseYear = year,
                                overview = desc,
                                coverUrl = coverUrl,
                                category = Category.BOOK,
                                externalUrl = "https://mangadex.org/title/$mangaId",
                                totalPages = chapters
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    fun fetchMangaDexLatestChapter(mangaId: String): Int {
        try {
            val url = "https://api.mangadex.org/manga/$mangaId/feed?limit=1&order[chapter]=desc"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "ListyClone/1.0")
                .build()
            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val root = JSONObject(body)
                    val data = root.optJSONArray("data")
                    if (data != null && data.length() > 0) {
                        val chStr = data.getJSONObject(0).optJSONObject("attributes")?.optString("chapter", "") ?: ""
                        val count = chStr.toDoubleOrNull()?.toInt() ?: 0
                        if (count > 0) return count
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 0
    }

    suspend fun fetchMangaChapterCount(title: String): Int = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(title.trim(), "UTF-8")
            val url = "https://api.mangadex.org/manga?title=$encoded&limit=1"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "ListyClone/1.0")
                .build()
            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val root = JSONObject(body)
                    val data = root.optJSONArray("data")
                    if (data != null && data.length() > 0) {
                        val m = data.getJSONObject(0)
                        val mangaId = m.optString("id")
                        val attrs = m.optJSONObject("attributes")
                        val lastChapterStr = attrs?.optString("lastChapter", "") ?: ""
                        val parsed = lastChapterStr.toDoubleOrNull()?.toInt() ?: 0
                        if (parsed > 0) return@withContext parsed
                        val feedCount = fetchMangaDexLatestChapter(mangaId)
                        if (feedCount > 0) return@withContext feedCount
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        0
    }

    private suspend fun searchBooksAndManga(
        query: String,
        subFilter: BookSubFilter,
        bookSearchType: BookSearchType = BookSearchType.ALL,
        yearFilter: String? = null,
        langFilter: String? = null
    ): List<SearchResult> = coroutineScope {
        val deferredList = when (subFilter) {
            BookSubFilter.BOOKS -> listOf(
                async { searchGoogleBooks(query, langFilter, bookSearchType) },
                async { searchOpenLibrary(query, bookSearchType) }
            )
            BookSubFilter.MANGA -> listOf(
                async { searchMangaDex(query, langFilter) },
                async { searchAniListManga(query) },
                async { searchGoogleBooks(query, langFilter, bookSearchType) }
            )
            BookSubFilter.COMICS -> listOf(
                async { searchGoogleBooks("$query comic", langFilter, bookSearchType) },
                async { searchWikipedia("$query comic", Category.BOOK) },
                async { searchMangaDex(query, langFilter) }
            )
            BookSubFilter.ALL -> listOf(
                async { searchGoogleBooks(query, langFilter, bookSearchType) },
                async { searchOpenLibrary(query, bookSearchType) },
                async { searchMangaDex(query, langFilter) },
                async { searchAniListManga(query) }
            )
        }

        val allResults = deferredList.awaitAll().flatten()
        val seen = mutableSetOf<String>()
        val deduped = mutableListOf<SearchResult>()
        for (item in allResults) {
            val key = item.title.lowercase().replace(Regex("[^a-z0-9]"), "")
            if (key.isNotBlank() && seen.add(key)) {
                deduped.add(item)
            }
        }
        if (!yearFilter.isNullOrBlank()) {
            val (matching, rest) = deduped.partition { matchesYear(it.releaseYear, yearFilter) }
            matching + rest
        } else {
            deduped
        }
    }

    private fun searchAniListManga(query: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        try {
            val graphql = """
                {"query": "query (${'$'}search: String) { Page(perPage: 30) { media(search: ${'$'}search, type: MANGA) { id title { romaji english } coverImage { extraLarge large } chapters volumes averageScore startDate { year } description } } }", "variables": {"search": "$query"}}
            """.trimIndent()

            val req = Request.Builder()
                .url("https://graphql.anilist.co")
                .header("Content-Type", "application/json")
                .header("User-Agent", "ListyClone/1.0")
                .post(graphql.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val root = JSONObject(body)
                    val media = root.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("media") ?: JSONArray()
                    for (i in 0 until media.length()) {
                        val m = media.getJSONObject(i)
                        val titleObj = m.optJSONObject("title")
                        val eng = titleObj?.optString("english")?.takeIf { it.isNotEmpty() && it != "null" }
                        val romaji = titleObj?.optString("romaji")?.takeIf { it.isNotEmpty() && it != "null" }
                        val title = eng ?: romaji ?: "Manga"
                        val orig = if (eng != null && romaji != null && eng != romaji) romaji else ""
                        val coverObj = m.optJSONObject("coverImage")
                        val cover = coverObj?.optString("extraLarge") ?: coverObj?.optString("large") ?: ""
                        val chapters = m.optInt("chapters", 0)
                        val score = m.optDouble("averageScore", 0.0).toFloat() / 10f
                        val startYear = m.optJSONObject("startDate")?.optString("year") ?: ""
                        val desc = cleanHtml(m.optString("description"))
                        val id = m.optInt("id")

                        results.add(
                            SearchResult(
                                title = title,
                                originalTitle = orig,
                                releaseYear = if (startYear != "null") startYear else "",
                                coverUrl = cover,
                                rating = score,
                                overview = desc,
                                category = Category.BOOK,
                                externalUrl = "https://anilist.co/manga/$id",
                                totalPages = chapters
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    private fun searchGoogleBooks(
        query: String,
        langFilter: String? = null,
        bookSearchType: BookSearchType = BookSearchType.ALL
    ): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        try {
            val qClean = query.trim()
            val formattedQuery = when (bookSearchType) {
                BookSearchType.AUTHOR -> "inauthor:$qClean"
                BookSearchType.TITLE -> "intitle:$qClean"
                BookSearchType.ALL -> qClean
            }
            val encoded = URLEncoder.encode(formattedQuery, "UTF-8")
            val langParam = if (!langFilter.isNullOrBlank()) "&langRestrict=${URLEncoder.encode(langFilter.lowercase(), "UTF-8")}" else ""
            val url = "https://www.googleapis.com/books/v1/volumes?q=$encoded&maxResults=40$langParam"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val items = json.optJSONArray("items") ?: JSONArray()
                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        val vol = item.optJSONObject("volumeInfo") ?: continue
                        val title = vol.optString("title")
                        val authors = vol.optJSONArray("authors")
                        val author = if (authors != null && authors.length() > 0) authors.getString(0) else ""
                        val publishedDate = vol.optString("publishedDate")
                        val year = if (publishedDate.length >= 4) publishedDate.substring(0, 4) else ""
                        val desc = cleanHtml(vol.optString("description"))
                        val imageLinks = vol.optJSONObject("imageLinks")
                        var thumb = imageLinks?.optString("thumbnail") ?: imageLinks?.optString("smallThumbnail") ?: ""
                        if (thumb.startsWith("http://")) {
                            thumb = thumb.replace("http://", "https://")
                        }
                        val pages = vol.optInt("pageCount", 0)
                        val rating = vol.optDouble("averageRating", 0.0).toFloat() * 2f

                        results.add(
                            SearchResult(
                                title = title,
                                creator = author,
                                releaseYear = year,
                                overview = desc,
                                coverUrl = thumb,
                                rating = rating,
                                category = Category.BOOK,
                                totalPages = pages
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    private fun searchOpenLibrary(
        query: String,
        bookSearchType: BookSearchType = BookSearchType.ALL
    ): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        try {
            val qClean = query.trim()
            val encoded = URLEncoder.encode(qClean, "UTF-8")
            val url = when (bookSearchType) {
                BookSearchType.AUTHOR -> "https://openlibrary.org/search.json?author=$encoded&limit=30"
                BookSearchType.TITLE -> "https://openlibrary.org/search.json?title=$encoded&limit=30"
                BookSearchType.ALL -> "https://openlibrary.org/search.json?q=$encoded&limit=30"
            }
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "ListyClone/1.0")
                .build()

            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val docs = json.optJSONArray("docs") ?: JSONArray()
                    for (i in 0 until docs.length()) {
                        val doc = docs.getJSONObject(i)
                        val title = doc.optString("title")
                        val authorArr = doc.optJSONArray("author_name")
                        val author = if (authorArr != null && authorArr.length() > 0) authorArr.getString(0) else ""
                        val year = doc.optString("first_publish_year")
                        val coverId = doc.optInt("cover_i", 0)
                        val cover = if (coverId > 0) "https://covers.openlibrary.org/b/id/$coverId-L.jpg" else ""
                        val pages = doc.optInt("number_of_pages_median", 0)
                        val rating = doc.optDouble("ratings_average", 0.0).toFloat() * 2f
                        val key = doc.optString("key")
                        val firstSentence = doc.optString("first_sentence").ifEmpty {
                            doc.optJSONArray("first_sentence")?.optString(0) ?: ""
                        }

                        results.add(
                            SearchResult(
                                title = title,
                                creator = author,
                                releaseYear = if (year != "null") year else "",
                                overview = firstSentence,
                                coverUrl = cover,
                                rating = rating,
                                category = Category.BOOK,
                                externalUrl = if (key.isNotEmpty()) "https://openlibrary.org$key" else "",
                                totalPages = pages
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    suspend fun fetchBookOverview(title: String): String = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(title.trim(), "UTF-8")
            val url = "https://www.googleapis.com/books/v1/volumes?q=$encoded&maxResults=1"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val items = json.optJSONArray("items")
                    if (items != null && items.length() > 0) {
                        val vol = items.getJSONObject(0).optJSONObject("volumeInfo")
                        val desc = vol?.optString("description", "") ?: ""
                        if (desc.isNotBlank()) return@withContext cleanHtml(desc)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        ""
    }

    // -------------------------------------------------------------
    // AUTOMATIC COVER RESOLUTION FALLBACK
    // -------------------------------------------------------------
    suspend fun resolveCover(title: String, category: Category, creator: String = ""): String? = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return@withContext null
        try {
            when (category) {
                Category.GAME -> {
                    val games = searchImdbVideoGames(cleanTitle)
                    val gameCover = games.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl
                    if (!gameCover.isNullOrBlank()) return@withContext gameCover
                    val wiki = searchWikipedia(cleanTitle, Category.GAME)
                    val wikiCover = wiki.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl
                    if (!wikiCover.isNullOrBlank()) return@withContext wikiCover
                }
                Category.BOOK -> {
                    val books = searchGoogleBooks(if (creator.isNotBlank()) "$cleanTitle $creator" else cleanTitle)
                    val bookCover = books.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl
                    if (!bookCover.isNullOrBlank()) return@withContext bookCover
                    val ol = searchOpenLibrary(cleanTitle)
                    val olCover = ol.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl
                    if (!olCover.isNullOrBlank()) return@withContext olCover
                    val manga = searchMangaDex(cleanTitle)
                    val mCover = manga.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl
                    if (!mCover.isNullOrBlank()) return@withContext mCover
                }
                Category.MOVIE -> {
                    val movies = searchMovies(cleanTitle)
                    val mCover = movies.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl
                    if (!mCover.isNullOrBlank()) return@withContext mCover
                }
                Category.TV_SHOW -> {
                    val tv = searchTvMaze(cleanTitle)
                    val tvCover = tv.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl
                    if (!tvCover.isNullOrBlank()) return@withContext tvCover
                    val ani = searchAniListAnime(cleanTitle)
                    val aniCover = ani.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl
                    if (!aniCover.isNullOrBlank()) return@withContext aniCover
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    // -------------------------------------------------------------
    // GAMES: RecipeKit IMDb GraphQL Engine with clean covers & details (35 items)
    // -------------------------------------------------------------
    private fun searchImdbVideoGames(query: String, langFilter: String? = null): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        try {
            val acceptLang = when (langFilter?.lowercase()) {
                "ja" -> "ja-JP,ja;q=0.9,en;q=0.8"
                "es" -> "es-ES,es;q=0.9,en;q=0.8"
                "fr" -> "fr-FR,fr;q=0.9,en;q=0.8"
                "de" -> "de-DE,de;q=0.9,en;q=0.8"
                "ko" -> "ko-KR,ko;q=0.9,en;q=0.8"
                "zh" -> "zh-CN,zh;q=0.9,en;q=0.8"
                "hi" -> "hi-IN,hi;q=0.9,en;q=0.8"
                "it" -> "it-IT,it;q=0.9,en;q=0.8"
                else -> "en-US,en;q=0.9"
            }

            val queryStr = """
                query MainSearch(${'$'}searchTerm: String!) {
                  mainSearch(first: 35, options: {searchTerm: ${'$'}searchTerm, isExactMatch: false, type: TITLE, titleSearchOptions: {type: VIDEO_GAME}}) {
                    edges {
                      node {
                        entity {
                          ... on Title {
                            id
                            titleText {
                              text
                            }
                            originalTitleText {
                              text
                            }
                            releaseYear {
                              year
                            }
                            primaryImage {
                              url
                            }
                            ratingsSummary {
                              aggregateRating
                            }
                            plot {
                              plotText {
                                plainText
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
            """.trimIndent()

            val reqJson = JSONObject().apply {
                put("query", queryStr)
                put("variables", JSONObject().apply {
                    put("searchTerm", query.trim())
                })
            }

            val req = Request.Builder()
                .url("https://caching.graphql.imdb.com/")
                .header("Content-Type", "application/json")
                .header("x-imdb-client-name", "imdb-web-next-localized")
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                .header("Accept-Language", acceptLang)
                .post(reqJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val root = JSONObject(body)
                    val data = root.optJSONObject("data")
                    val mainSearch = data?.optJSONObject("mainSearch")
                    val edges = mainSearch?.optJSONArray("edges") ?: JSONArray()
                    for (i in 0 until edges.length()) {
                        val edge = edges.getJSONObject(i)
                        val entity = edge.optJSONObject("node")?.optJSONObject("entity") ?: continue
                        val id = entity.optString("id")
                        val title = entity.optJSONObject("titleText")?.optString("text") ?: ""
                        if (title.isBlank()) continue
                        val origTitle = entity.optJSONObject("originalTitleText")?.optString("text") ?: ""
                        val releaseYear = entity.optJSONObject("releaseYear")?.optString("year") ?: ""
                        val coverUrl = entity.optJSONObject("primaryImage")?.optString("url") ?: ""
                        val rating = entity.optJSONObject("ratingsSummary")?.optDouble("aggregateRating", 0.0)?.toFloat() ?: 0f
                        val plot = entity.optJSONObject("plot")?.optJSONObject("plotText")?.optString("plainText") ?: ""

                        results.add(
                            SearchResult(
                                title = title,
                                originalTitle = if (origTitle != title) origTitle else "",
                                releaseYear = releaseYear,
                                coverUrl = coverUrl,
                                rating = rating,
                                overview = plot,
                                category = Category.GAME,
                                externalUrl = if (id.isNotBlank()) "https://www.imdb.com/title/$id" else ""
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    private fun searchGames(query: String, yearFilter: String? = null, langFilter: String? = null): List<SearchResult> {
        val imdbResults = searchImdbVideoGames(query, langFilter)
        val filteredImdb = if (!yearFilter.isNullOrBlank()) {
            val (matching, rest) = imdbResults.partition { matchesYear(it.releaseYear, yearFilter) }
            matching + rest
        } else {
            imdbResults
        }

        if (filteredImdb.isNotEmpty()) {
            return filteredImdb
        }

        // Fallback to Wikipedia search, strictly filtering out "List of..." pages
        val wikiList = searchWikipedia(query, Category.GAME).filter { item ->
            val t = item.title.lowercase()
            !t.startsWith("list of") && !t.startsWith("lists of") && !t.contains("disambiguation") &&
                !item.overview.startsWith("List of", ignoreCase = true)
        }
        return if (!yearFilter.isNullOrBlank()) {
            val (matching, rest) = wikiList.partition { matchesYear(it.releaseYear, yearFilter) }
            matching + rest
        } else {
            wikiList
        }
    }

    private fun fetchWikipediaImageUrls(fileNames: List<String>): Map<String, String> {
        if (fileNames.isEmpty()) return emptyMap()
        val map = mutableMapOf<String, String>()
        try {
            val distinctFiles = fileNames.distinct()
            distinctFiles.chunked(20).forEach { chunk ->
                val titlesParam = chunk.joinToString("|") { "File:$it" }
                val encodedTitles = URLEncoder.encode(titlesParam, "UTF-8")
                val url = "https://en.wikipedia.org/w/api.php?action=query&titles=$encodedTitles&prop=imageinfo&iiprop=url&iiurlwidth=600&format=json"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "ListyClone/1.0 (contact@example.com)")
                    .build()
                client.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val root = JSONObject(body)
                        val query = root.optJSONObject("query")
                        val pages = query?.optJSONObject("pages")
                        if (pages != null) {
                            val keys = pages.keys()
                            while (keys.hasNext()) {
                                val pKey = keys.next()
                                val page = pages.getJSONObject(pKey)
                                val title = page.optString("title").removePrefix("File:").trim()
                                val imgInfo = page.optJSONArray("imageinfo")
                                if (imgInfo != null && imgInfo.length() > 0) {
                                    val first = imgInfo.getJSONObject(0)
                                    val thumbUrl = first.optString("thumburl").ifEmpty { first.optString("url") }
                                    if (thumbUrl.isNotBlank()) {
                                        map[title] = thumbUrl
                                        map[title.replace(" ", "_")] = thumbUrl
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    private fun searchWikipedia(query: String, category: Category): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        try {
            val suffix = when (category) {
                Category.GAME -> " video game"
                Category.MOVIE -> " film"
                Category.TV_SHOW -> " series"
                Category.BOOK -> " novel"
            }
            val searchQuery = if (query.endsWith(suffix, ignoreCase = true)) query else "$query$suffix"
            val encoded = URLEncoder.encode(searchQuery, "UTF-8")
            val url = "https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=$encoded&gsrlimit=30&prop=pageimages|pageprops|extracts&pithumbsize=600&exintro=1&explaintext=1&exsentences=3&format=json"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "ListyClone/1.0 (contact@example.com)")
                .build()

            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val queryObj = json.optJSONObject("query")
                    val pages = queryObj?.optJSONObject("pages")
                    if (pages != null) {
                        data class RawWikiItem(
                            val pageId: String,
                            val title: String,
                            val rawTitle: String,
                            val extract: String,
                            val thumbSource: String,
                            val pageImage: String,
                            val shortDesc: String
                        )
                        val rawList = mutableListOf<RawWikiItem>()
                        val missingImageFiles = mutableListOf<String>()

                        val keys = pages.keys()
                        while (keys.hasNext()) {
                            val pKey = keys.next()
                            val page = pages.getJSONObject(pKey)
                            val rawTitle = page.optString("title")
                            if (rawTitle.startsWith("List of ", ignoreCase = true) ||
                                rawTitle.startsWith("Lists of ", ignoreCase = true) ||
                                rawTitle.contains("(disambiguation)", ignoreCase = true)
                            ) {
                                continue
                            }
                            val cleanTitle = rawTitle.replace(Regex("\\s*\\([^)]*(film|video game|series|novel|TV series|book|game)[^)]*\\)", RegexOption.IGNORE_CASE), "").trim()
                            val extract = page.optString("extract")
                            val thumbObj = page.optJSONObject("thumbnail")
                            val thumb = thumbObj?.optString("source") ?: ""
                            val props = page.optJSONObject("pageprops")
                            val pageImage = props?.optString("page_image") ?: ""
                            val shortDesc = props?.optString("wikibase-shortdesc") ?: ""

                            if (thumb.isBlank() && pageImage.isNotBlank()) {
                                missingImageFiles.add(pageImage)
                            }

                            rawList.add(RawWikiItem(pKey, cleanTitle, rawTitle, extract, thumb, pageImage, shortDesc))
                        }

                        val resolvedImageMap = if (missingImageFiles.isNotEmpty()) {
                            fetchWikipediaImageUrls(missingImageFiles)
                        } else emptyMap()

                        for (raw in rawList) {
                            var finalCover = raw.thumbSource
                            if (finalCover.isBlank() && raw.pageImage.isNotBlank()) {
                                finalCover = resolvedImageMap[raw.pageImage]
                                    ?: resolvedImageMap[raw.pageImage.replace("_", " ")]
                                    ?: "https://en.wikipedia.org/wiki/Special:FilePath/${URLEncoder.encode(raw.pageImage, "UTF-8")}?width=600"
                            }

                            val yearMatch = Regex("\\b(19\\d{2}|20\\d{2})\\b").find(raw.shortDesc)
                                ?: Regex("\\b(19\\d{2}|20\\d{2})\\b").find(raw.extract)
                            val year = yearMatch?.value ?: ""

                            results.add(
                                SearchResult(
                                    title = raw.title,
                                    releaseYear = year,
                                    overview = raw.extract,
                                    coverUrl = finalCover,
                                    category = category,
                                    externalUrl = "https://en.wikipedia.org/wiki/" + URLEncoder.encode(raw.rawTitle.replace(" ", "_"), "UTF-8")
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    private fun cleanHtml(html: String): String {
        return html
            .replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .trim()
    }
}
