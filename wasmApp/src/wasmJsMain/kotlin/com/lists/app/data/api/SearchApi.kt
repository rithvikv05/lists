package com.lists.app.data.api

import com.lists.app.data.model.Category
import com.lists.app.data.model.SearchResult
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.*

object SearchApi {
    private val client = HttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(query: String, category: Category): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val q = query.trim()

        return try {
            when (category) {
                Category.MOVIE -> searchMovies(q)
                Category.TV_SHOW -> searchTvShows(q)
                Category.BOOK -> searchBooks(q)
                Category.GAME -> searchGames(q)
            }
        } catch (e: Exception) {
            println("Search error: ${e.message}")
            emptyList()
        }
    }

    private suspend fun searchMovies(query: String): List<SearchResult> {
        val url = "https://itunes.apple.com/search?term=${encodeURIComponent(query)}&entity=movie&limit=15"
        val response = client.get(url).bodyAsText()
        val root = json.parseToJsonElement(response).jsonObject
        val results = root["results"]?.jsonArray ?: return emptyList()

        return results.mapNotNull { elem ->
            val obj = elem.jsonObject
            val title = obj["trackName"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val coverUrl = obj["artworkUrl100"]?.jsonPrimitive?.content?.replace("100x100bb", "600x600bb") ?: ""
            val releaseDate = obj["releaseDate"]?.jsonPrimitive?.content ?: ""
            val releaseYear = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else ""
            val creator = obj["artistName"]?.jsonPrimitive?.content ?: ""
            val overview = obj["longDescription"]?.jsonPrimitive?.content ?: ""

            SearchResult(
                title = title,
                releaseYear = releaseYear,
                releaseDate = releaseDate,
                creator = creator,
                overview = overview,
                coverUrl = coverUrl,
                category = Category.MOVIE
            )
        }
    }

    private suspend fun searchTvShows(query: String): List<SearchResult> {
        val url = "https://api.tvmaze.com/search/shows?q=${encodeURIComponent(query)}"
        val response = client.get(url).bodyAsText()
        val array = json.parseToJsonElement(response).jsonArray

        return array.mapNotNull { elem ->
            val show = elem.jsonObject["show"]?.jsonObject ?: return@mapNotNull null
            val title = show["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val image = show["image"]?.jsonObject
            val coverUrl = image?.get("original")?.jsonPrimitive?.content
                ?: image?.get("medium")?.jsonPrimitive?.content ?: ""
            val premiered = show["premiered"]?.jsonPrimitive?.content ?: ""
            val releaseYear = if (premiered.length >= 4) premiered.substring(0, 4) else ""
            val network = show["network"]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: ""
            val rawSummary = show["summary"]?.jsonPrimitive?.content ?: ""
            val overview = rawSummary.replace(Regex("<[^>]*>"), "")

            SearchResult(
                title = title,
                releaseYear = releaseYear,
                releaseDate = premiered,
                creator = network,
                overview = overview,
                coverUrl = coverUrl,
                category = Category.TV_SHOW
            )
        }
    }

    private suspend fun searchBooks(query: String): List<SearchResult> {
        val url = "https://www.googleapis.com/books/v1/volumes?q=${encodeURIComponent(query)}&maxResults=15"
        val response = client.get(url).bodyAsText()
        val root = json.parseToJsonElement(response).jsonObject
        val items = root["items"]?.jsonArray ?: return emptyList()

        return items.mapNotNull { elem ->
            val info = elem.jsonObject["volumeInfo"]?.jsonObject ?: return@mapNotNull null
            val title = info["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val authors = info["authors"]?.jsonArray?.map { it.jsonPrimitive.content }?.joinToString(", ") ?: ""
            val publishedDate = info["publishedDate"]?.jsonPrimitive?.content ?: ""
            val releaseYear = if (publishedDate.length >= 4) publishedDate.substring(0, 4) else ""
            val imageLinks = info["imageLinks"]?.jsonObject
            val coverUrl = (imageLinks?.get("thumbnail")?.jsonPrimitive?.content ?: "")
                .replace("http://", "https://")
            val overview = info["description"]?.jsonPrimitive?.content ?: ""
            val pageCount = info["pageCount"]?.jsonPrimitive?.intOrNull ?: 0

            SearchResult(
                title = title,
                releaseYear = releaseYear,
                releaseDate = publishedDate,
                creator = authors,
                overview = overview,
                coverUrl = coverUrl,
                category = Category.BOOK,
                totalPages = pageCount
            )
        }
    }

    private suspend fun searchGames(query: String): List<SearchResult> {
        val url = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=${encodeURIComponent(query + " video game")}&format=json&origin=*"
        val response = client.get(url).bodyAsText()
        val root = json.parseToJsonElement(response).jsonObject
        val search = root["query"]?.jsonObject?.get("search")?.jsonArray ?: return emptyList()

        return search.mapNotNull { elem ->
            val obj = elem.jsonObject
            val rawTitle = obj["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val cleanTitle = rawTitle.replace(" (video game)", "").replace(" (game)", "")
            val snippet = obj["snippet"]?.jsonPrimitive?.content?.replace(Regex("<[^>]*>"), "") ?: ""

            SearchResult(
                title = cleanTitle,
                releaseYear = "",
                creator = "Game",
                overview = snippet,
                coverUrl = "",
                category = Category.GAME
            )
        }
    }
}

external fun encodeURIComponent(uri: String): String
