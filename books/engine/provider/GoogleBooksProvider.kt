package books.engine.provider

import books.engine.aggregator.CoverResolver
import books.engine.model.BookMetadata
import books.engine.model.BookSearchFilter
import books.engine.model.BookSearchSource
import books.engine.model.BookSearchType
import books.engine.normalizer.AttributeMapper
import books.engine.normalizer.IsbnNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class GoogleBooksProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) : BookSearchProvider {

    override val source: BookSearchSource = BookSearchSource.GOOGLE_BOOKS

    override suspend fun search(filter: BookSearchFilter): List<BookMetadata> = withContext(Dispatchers.IO) {
        val results = mutableListOf<BookMetadata>()
        try {
            val qClean = filter.query.trim()
            val formattedQuery = when (filter.searchType) {
                BookSearchType.AUTHOR -> "inauthor:$qClean"
                BookSearchType.TITLE -> "intitle:$qClean"
                BookSearchType.ISBN -> "isbn:${IsbnNormalizer.clean(qClean)}"
                BookSearchType.ALL -> {
                    if (IsbnNormalizer.isQueryAnIsbn(qClean)) "isbn:${IsbnNormalizer.clean(qClean)}"
                    else qClean
                }
            }
            val encoded = URLEncoder.encode(formattedQuery, "UTF-8")
            val langParam = if (!filter.targetLanguage.isNullOrBlank()) {
                "&langRestrict=${URLEncoder.encode(filter.targetLanguage.lowercase(), "UTF-8")}"
            } else ""

            val limit = filter.maxResults.coerceIn(1, 40)
            val url = "https://www.googleapis.com/books/v1/volumes?q=$encoded&maxResults=$limit$langParam"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "ListsBooksEngine/1.0 (Android; Linux)")
                .build()

            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val items = json.optJSONArray("items") ?: JSONArray()
                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        parseVolume(item)?.let { results.add(it) }
                    }
                }
            }
        } catch (_: Exception) {}
        results
    }

    override suspend fun lookupByIsbn(isbn: String): BookMetadata? = withContext(Dispatchers.IO) {
        try {
            val clean = IsbnNormalizer.clean(isbn)
            if (!IsbnNormalizer.isValidIsbn(clean)) return@withContext null
            val url = "https://www.googleapis.com/books/v1/volumes?q=isbn:$clean&maxResults=1"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "ListsBooksEngine/1.0 (Android; Linux)")
                .build()

            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val items = json.optJSONArray("items")
                    if (items != null && items.length() > 0) {
                        return@withContext parseVolume(items.getJSONObject(0))
                    }
                }
            }
        } catch (_: Exception) {}
        null
    }

    private fun parseVolume(item: JSONObject): BookMetadata? {
        val volumeId = item.optString("id")
        val vol = item.optJSONObject("volumeInfo") ?: return null
        val rawTitle = vol.optString("title").trim()
        if (rawTitle.isBlank()) return null
        val subtitle = vol.optString("subtitle").takeIf { it.isNotBlank() }

        val authorsList = mutableListOf<String>()
        val authorsArr = vol.optJSONArray("authors")
        if (authorsArr != null) {
            for (i in 0 until authorsArr.length()) {
                val a = authorsArr.optString(i).trim()
                if (a.isNotBlank()) authorsList.add(a)
            }
        }

        var isbn10: String? = null
        var isbn13: String? = null
        val identifiers = vol.optJSONArray("industryIdentifiers")
        if (identifiers != null) {
            for (i in 0 until identifiers.length()) {
                val ident = identifiers.getJSONObject(i)
                val type = ident.optString("type")
                val identifier = ident.optString("identifier")
                if (type.contains("ISBN_13", ignoreCase = true)) {
                    isbn13 = IsbnNormalizer.clean(identifier)
                } else if (type.contains("ISBN_10", ignoreCase = true)) {
                    isbn10 = IsbnNormalizer.clean(identifier)
                }
            }
        }

        // Fill in missing ISBN variant if one exists
        if (isbn13 != null && isbn10 == null) isbn10 = IsbnNormalizer.isbn13ToIsbn10(isbn13)
        if (isbn10 != null && isbn13 == null) isbn13 = IsbnNormalizer.isbn10ToIsbn13(isbn10)

        val publisher = vol.optString("publisher").takeIf { it.isNotBlank() }
        val publishedDate = vol.optString("publishedDate").takeIf { it.isNotBlank() }
        val year = if (publishedDate != null && publishedDate.length >= 4) publishedDate.substring(0, 4) else null

        val pages = vol.optInt("pageCount", 0).takeIf { it > 0 }
            ?: vol.optInt("printedPageCount", 0).takeIf { it > 0 }

        val language = vol.optString("language").takeIf { it.isNotBlank() }
        val overview = vol.optString("description").takeIf { it.isNotBlank() }

        val categoriesList = mutableListOf<String>()
        val catArr = vol.optJSONArray("categories")
        if (catArr != null) {
            for (i in 0 until catArr.length()) {
                val c = catArr.optString(i).trim()
                if (c.isNotBlank()) categoriesList.add(c)
            }
        }

        val rating = vol.optDouble("averageRating", 0.0).toFloat().takeIf { it > 0f }
        val ratingsCount = vol.optInt("ratingsCount", 0).takeIf { it > 0 }
        val infoLink = vol.optString("infoLink").takeIf { it.isNotBlank() }

        val imageLinks = vol.optJSONObject("imageLinks")
        var cover = imageLinks?.optString("extraLarge")
            ?: imageLinks?.optString("large")
            ?: imageLinks?.optString("medium")
            ?: imageLinks?.optString("small")
            ?: imageLinks?.optString("thumbnail")
            ?: imageLinks?.optString("smallThumbnail")

        if (cover.isNullOrBlank() && volumeId.isNotBlank()) {
            cover = "https://books.google.com/books/content?id=$volumeId&printsec=frontcover&img=1&zoom=1"
        }
        val (highResCover, thumbCover) = CoverResolver.resolveBestCover(
            primaryIsbn = isbn13 ?: isbn10,
            googleCoverUrl = cover
        )

        // Attribute and Recommendation Vector mapping
        val attributeResult = AttributeMapper.mapAttributes(
            categories = categoriesList,
            subjects = emptyList(),
            totalPages = pages
        )

        val id = isbn13 ?: isbn10 ?: "google_$volumeId"

        return BookMetadata(
            id = id,
            title = rawTitle,
            subtitle = subtitle,
            authors = authorsList,
            isbn10 = isbn10,
            isbn13 = isbn13,
            publisher = publisher,
            publishedYear = year,
            publishedDate = publishedDate,
            totalPages = pages,
            language = language,
            overview = overview,
            coverUrl = highResCover,
            thumbnailCoverUrl = thumbCover,
            rating = rating,
            ratingsCount = ratingsCount,
            externalUrl = infoLink,
            source = BookSearchSource.GOOGLE_BOOKS,
            rawCategories = categoriesList,
            normalizedGenres = attributeResult.genres,
            suggestedMoods = attributeResult.moods,
            suggestedPacing = attributeResult.pacing,
            recommendationVector = attributeResult.recommendationVector
        )
    }
}

