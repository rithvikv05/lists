package com.lists.app.data.api

import com.lists.app.data.model.Category
import com.lists.app.data.model.SearchResult
import com.lists.app.encodeURIComponent
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
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
    val airdate: String = ""
) {
    val key: String get() = "S${season}E${number}"
}

class SearchRepository {
    private val client = HttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private fun normalizeTitle(title: String): String {
        return title.lowercase()
            .replace(Regex("""[:\-_,.'"!?/()\[\]]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun deduplicate(results: List<SearchResult>): List<SearchResult> {
        val map = linkedMapOf<String, SearchResult>()
        for (item in results) {
            val titleNorm = normalizeTitle(item.title)
            if (titleNorm.isBlank()) continue
            val yearDigits = item.releaseYear.filter { it.isDigit() }.take(4)
            val key = if (yearDigits.isNotBlank()) "$titleNorm#$yearDigits" else titleNorm

            val existing = map[key]
            if (existing == null) {
                map[key] = item
            } else {
                val betterCover = if (existing.coverUrl.isBlank() && item.coverUrl.isNotBlank()) item.coverUrl else existing.coverUrl
                val betterOverview = if (existing.overview.length < item.overview.length) item.overview else existing.overview
                val betterYear = if (existing.releaseYear.isBlank() && item.releaseYear.isNotBlank()) item.releaseYear else existing.releaseYear
                val betterCreator = if (existing.creator.isBlank() && item.creator.isNotBlank()) item.creator else existing.creator
                val betterExtUrl = if (existing.externalUrl.isBlank() && item.externalUrl.isNotBlank()) item.externalUrl else existing.externalUrl
                val betterEps = if (existing.totalEpisodes <= 0 && item.totalEpisodes > 0) item.totalEpisodes else existing.totalEpisodes
                val betterPages = if (existing.totalPages <= 0 && item.totalPages > 0) item.totalPages else existing.totalPages

                map[key] = existing.copy(
                    coverUrl = betterCover,
                    overview = betterOverview,
                    releaseYear = betterYear,
                    creator = betterCreator,
                    externalUrl = betterExtUrl,
                    totalEpisodes = betterEps,
                    totalPages = betterPages
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

        val raw = try {
            when (category) {
                Category.MOVIE -> searchMovies(q, selectedYear)
                Category.TV_SHOW -> searchTv(q, tvSubFilter, selectedYear)
                Category.BOOK -> searchBooks(q, bookSubFilter, bookSearchType)
                Category.GAME -> searchGames(q)
            }
        } catch (e: Exception) {
            println("Search error: ${e.message}")
            emptyList()
        }

        return deduplicate(raw)
    }

    private suspend fun searchMovies(query: String, year: String?): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val term = encodeURIComponent(query)
        val yearParam = if (!year.isNullOrBlank()) "&y=${encodeURIComponent(year)}" else ""

        // 1. OMDB Movie Search (reliable, has IMDb IDs and posters)
        try {
            val omdbUrl = "https://www.omdbapi.com/?s=$term$yearParam&type=movie&apikey=trilogy"
            val resp = client.get(omdbUrl).bodyAsText()
            val root = json.parseToJsonElement(resp).jsonObject
            if (root["Response"]?.jsonPrimitive?.content == "True") {
                val arr = root["Search"]?.jsonArray
                arr?.forEach { elem ->
                    val obj = elem.jsonObject
                    val title = obj["Title"]?.jsonPrimitive?.content ?: return@forEach
                    val yr = obj["Year"]?.jsonPrimitive?.content ?: ""
                    var poster = obj["Poster"]?.jsonPrimitive?.content ?: ""
                    if (poster == "N/A") poster = ""
                    val imdbId = obj["imdbID"]?.jsonPrimitive?.content ?: ""
                    results.add(
                        SearchResult(
                            title = title,
                            releaseYear = yr,
                            releaseDate = yr,
                            coverUrl = poster,
                            category = Category.MOVIE,
                            externalUrl = if (imdbId.isNotEmpty()) "https://www.imdb.com/title/$imdbId" else ""
                        )
                    )
                }
            }
        } catch (e: Throwable) {
            println("OMDB movies error: ${e.message}")
        }

        // 2. iTunes Movie Search (high-res artwork and rich overviews)
        try {
            val itunesUrl = "https://itunes.apple.com/search?term=$term&entity=movie&limit=25"
            val response = client.get(itunesUrl).bodyAsText()
            val root = json.parseToJsonElement(response).jsonObject
            val itunesResults = root["results"]?.jsonArray
            itunesResults?.forEach { elem ->
                val obj = elem.jsonObject
                val title = obj["trackName"]?.jsonPrimitive?.content ?: return@forEach
                val coverUrl = obj["artworkUrl100"]?.jsonPrimitive?.content?.replace("100x100bb", "600x600bb") ?: ""
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

        // 3. OMDB Series Fallback
        if (results.isEmpty() && (filter == TvSubFilter.ALL || filter == TvSubFilter.TV_SHOWS)) {
            try {
                val omdbUrl = "https://www.omdbapi.com/?s=$term&type=series&apikey=trilogy"
                val resp = client.get(omdbUrl).bodyAsText()
                val root = json.parseToJsonElement(resp).jsonObject
                if (root["Response"]?.jsonPrimitive?.content == "True") {
                    val arr = root["Search"]?.jsonArray
                    arr?.forEach { elem ->
                        val obj = elem.jsonObject
                        val title = obj["Title"]?.jsonPrimitive?.content ?: return@forEach
                        val yr = obj["Year"]?.jsonPrimitive?.content ?: ""
                        var poster = obj["Poster"]?.jsonPrimitive?.content ?: ""
                        if (poster == "N/A") poster = ""
                        val imdbId = obj["imdbID"]?.jsonPrimitive?.content ?: ""
                        results.add(
                            SearchResult(
                                title = title,
                                releaseYear = yr,
                                releaseDate = yr,
                                coverUrl = poster,
                                category = Category.TV_SHOW,
                                externalUrl = if (imdbId.isNotEmpty()) "https://www.imdb.com/title/$imdbId" else ""
                            )
                        )
                    }
                }
            } catch (_: Throwable) {}
        }

        return results
    }


    private suspend fun searchBooks(query: String, filter: BookSubFilter, searchType: BookSearchType): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val term = encodeURIComponent(query)

        if (filter == BookSubFilter.ALL || filter == BookSubFilter.BOOKS) {
            // 1. OpenLibrary Search (fast, reliable, no quota limits)
            try {
                val olUrl = "https://openlibrary.org/search.json?q=$term&limit=15"
                val response = client.get(olUrl).bodyAsText()
                val root = json.parseToJsonElement(response).jsonObject
                val docs = root["docs"]?.jsonArray
                docs?.forEach { elem ->
                    val obj = elem.jsonObject
                    val title = obj["title"]?.jsonPrimitive?.content ?: return@forEach
                    val authors = obj["author_name"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.joinToString(", ") ?: ""
                    val firstYear = obj["first_publish_year"]?.jsonPrimitive?.contentOrNull ?: ""
                    val coverI = obj["cover_i"]?.jsonPrimitive?.contentOrNull
                    val coverUrl = if (coverI != null) "https://covers.openlibrary.org/b/id/$coverI-M.jpg" else ""
                    val key = obj["key"]?.jsonPrimitive?.content ?: ""

                    results.add(
                        SearchResult(
                            title = title,
                            releaseYear = firstYear,
                            releaseDate = firstYear,
                            creator = authors,
                            coverUrl = coverUrl,
                            category = Category.BOOK,
                            externalUrl = if (key.isNotEmpty()) "https://openlibrary.org$key" else ""
                        )
                    )
                }
            } catch (e: Throwable) {
                println("OpenLibrary error: ${e.message}")
            }

            // 2. Google Books fallback
            try {
                val prefix = when (searchType) {
                    BookSearchType.TITLE -> "intitle:"
                    BookSearchType.AUTHOR -> "inauthor:"
                    BookSearchType.ALL -> ""
                }
                val url = "https://www.googleapis.com/books/v1/volumes?q=${prefix}$term&maxResults=15"
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
                        val coverUrl = rawCover.replace("http://", "https://")
                        val overview = info["description"]?.jsonPrimitive?.content ?: ""
                        val pageCount = info["pageCount"]?.jsonPrimitive?.intOrNull ?: 0
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
                        val coverUrl = if (fileName != null) "https://uploads.mangadex.org/covers/$id/$fileName.512.jpg" else ""

                        results.add(
                            SearchResult(
                                title = title,
                                releaseYear = yearVal,
                                releaseDate = yearVal,
                                creator = "Manga",
                                overview = desc,
                                coverUrl = coverUrl,
                                category = Category.BOOK,
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

        // 1. OMDB Video Game Search (fast, covers, accurate titles & years)
        try {
            val omdbUrl = "https://www.omdbapi.com/?s=$term&type=game&apikey=trilogy"
            val resp = client.get(omdbUrl).bodyAsText()
            val root = json.parseToJsonElement(resp).jsonObject
            if (root["Response"]?.jsonPrimitive?.content == "True") {
                val arr = root["Search"]?.jsonArray
                arr?.forEach { elem ->
                    val obj = elem.jsonObject
                    val title = obj["Title"]?.jsonPrimitive?.content ?: return@forEach
                    val yr = obj["Year"]?.jsonPrimitive?.content ?: ""
                    var poster = obj["Poster"]?.jsonPrimitive?.content ?: ""
                    if (poster == "N/A") poster = ""
                    val imdbId = obj["imdbID"]?.jsonPrimitive?.content ?: ""
                    results.add(
                        SearchResult(
                            title = title,
                            releaseYear = yr,
                            releaseDate = yr,
                            coverUrl = poster,
                            category = Category.GAME,
                            externalUrl = if (imdbId.isNotEmpty()) "https://www.imdb.com/title/$imdbId" else ""
                        )
                    )
                }
            }
        } catch (e: Throwable) {
            println("OMDB games error: ${e.message}")
        }

        // 2. Wikipedia fallback for any niche video game
        if (results.isEmpty()) {
            try {
                val wikiUrl = "https://en.wikipedia.org/w/api.php?action=opensearch&search=$term&limit=10&format=json"
                val resp = client.get(wikiUrl).bodyAsText()
                val array = json.parseToJsonElement(resp).jsonArray
                val titles = array.getOrNull(1)?.jsonArray ?: JsonArray(emptyList())
                val descs = array.getOrNull(2)?.jsonArray ?: JsonArray(emptyList())
                val links = array.getOrNull(3)?.jsonArray ?: JsonArray(emptyList())

                for (i in 0 until titles.size) {
                    val title = titles[i].jsonPrimitive.content
                    val desc = descs.getOrNull(i)?.jsonPrimitive?.content ?: ""
                    val link = links.getOrNull(i)?.jsonPrimitive?.content ?: ""
                    if (title.contains("disambiguation", ignoreCase = true)) continue
                    results.add(
                        SearchResult(
                            title = title,
                            releaseYear = "",
                            overview = desc,
                            category = Category.GAME,
                            externalUrl = link
                        )
                    )
                }
            } catch (_: Throwable) {}
        }

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

                EpisodeItem(
                    season = season,
                    number = number,
                    name = name,
                    airdate = airdate
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
