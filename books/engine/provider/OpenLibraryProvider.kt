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

class OpenLibraryProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) : BookSearchProvider {

    override val source: BookSearchSource = BookSearchSource.OPEN_LIBRARY

    override suspend fun search(filter: BookSearchFilter): List<BookMetadata> = withContext(Dispatchers.IO) {
        val results = mutableListOf<BookMetadata>()
        try {
            val qClean = filter.query.trim()
            val encoded = URLEncoder.encode(qClean, "UTF-8")
            val limit = filter.maxResults.coerceIn(1, 40)
            val langParam = filter.targetLanguage?.let { mapOlLanguage(it) } ?: ""

            val url = when (filter.searchType) {
                BookSearchType.AUTHOR -> "https://openlibrary.org/search.json?author=$encoded$langParam&limit=$limit"
                BookSearchType.TITLE -> "https://openlibrary.org/search.json?title=$encoded$langParam&limit=$limit"
                BookSearchType.ISBN -> "https://openlibrary.org/search.json?isbn=${IsbnNormalizer.clean(qClean)}&limit=$limit"
                BookSearchType.ALL -> {
                    if (IsbnNormalizer.isQueryAnIsbn(qClean)) {
                        "https://openlibrary.org/search.json?isbn=${IsbnNormalizer.clean(qClean)}&limit=$limit"
                    } else {
                        "https://openlibrary.org/search.json?q=$encoded$langParam&limit=$limit"
                    }
                }
            }

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "ListsBooksEngine/1.0 (Android; Linux)")
                .build()

            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val docs = json.optJSONArray("docs") ?: JSONArray()
                    for (i in 0 until docs.length()) {
                        val doc = docs.getJSONObject(i)
                        parseDoc(doc)?.let { results.add(it) }
                    }
                }
            }
        } catch (_: Exception) {}
        results
    }

    override suspend fun lookupByIsbn(isbn: String): BookMetadata? = withContext(Dispatchers.IO) {
        val clean = IsbnNormalizer.clean(isbn)
        if (!IsbnNormalizer.isValidIsbn(clean)) return@withContext null
        try {
            // First check the detailed Open Library Data API
            val bibUrl = "https://openlibrary.org/api/books?bibkeys=ISBN:$clean&jscmd=data&format=json"
            val req = Request.Builder()
                .url(bibUrl)
                .header("User-Agent", "ListsBooksEngine/1.0 (Android; Linux)")
                .build()

            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val root = JSONObject(body)
                    val key = "ISBN:$clean"
                    if (root.has(key)) {
                        val bookObj = root.getJSONObject(key)
                        return@withContext parseBibBook(clean, bookObj)
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback to search by isbn
        val searchResults = search(BookSearchFilter(query = clean, searchType = BookSearchType.ISBN, maxResults = 1))
        return@withContext searchResults.firstOrNull()
    }

    private fun parseDoc(doc: JSONObject): BookMetadata? {
        val title = doc.optString("title").trim()
        if (title.isBlank()) return null
        val subtitle = doc.optString("subtitle").takeIf { it.isNotBlank() }

        val authorsList = mutableListOf<String>()
        val authorArr = doc.optJSONArray("author_name")
        if (authorArr != null) {
            for (i in 0 until authorArr.length()) {
                val a = authorArr.optString(i).trim()
                if (a.isNotBlank()) authorsList.add(a)
            }
        }

        var isbn10: String? = null
        var isbn13: String? = null
        val isbnArr = doc.optJSONArray("isbn")
        if (isbnArr != null) {
            for (i in 0 until isbnArr.length()) {
                val candidate = IsbnNormalizer.clean(isbnArr.optString(i))
                if (candidate.length == 13 && isbn13 == null) {
                    isbn13 = candidate
                } else if (candidate.length == 10 && isbn10 == null) {
                    isbn10 = candidate
                }
                if (isbn10 != null && isbn13 != null) break
            }
        }
        if (isbn13 != null && isbn10 == null) isbn10 = IsbnNormalizer.isbn13ToIsbn10(isbn13)
        if (isbn10 != null && isbn13 == null) isbn13 = IsbnNormalizer.isbn10ToIsbn13(isbn10)

        val publisher = doc.optJSONArray("publisher")?.optString(0)?.takeIf { it.isNotBlank() }
        val year = doc.optString("first_publish_year").takeIf { it.isNotBlank() && it != "null" }

        val pages = doc.optInt("number_of_pages_median", 0).takeIf { it > 0 }
            ?: doc.optInt("number_of_pages", 0).takeIf { it > 0 }
            ?: doc.optJSONArray("number_of_pages")?.optInt(0, 0)?.takeIf { it > 0 }

        val coverId = doc.optInt("cover_i", 0).takeIf { it > 0 }
        val (highResCover, thumbCover) = CoverResolver.resolveBestCover(
            primaryIsbn = isbn13 ?: isbn10,
            openLibraryCoverId = coverId
        )

        val subjectsList = mutableListOf<String>()
        val subArr = doc.optJSONArray("subject")
        if (subArr != null) {
            val limit = minOf(subArr.length(), 20)
            for (i in 0 until limit) {
                val s = subArr.optString(i).trim()
                if (s.isNotBlank()) subjectsList.add(s)
            }
        }

        val firstSentence = doc.optString("first_sentence").ifEmpty {
            doc.optJSONArray("first_sentence")?.optString(0) ?: ""
        }.takeIf { it.isNotBlank() }

        val rating = doc.optDouble("ratings_average", 0.0).toFloat().takeIf { it > 0f }
        val ratingsCount = doc.optInt("ratings_count", 0).takeIf { it > 0 }
        val olKey = doc.optString("key")

        val attributeResult = AttributeMapper.mapAttributes(
            categories = emptyList(),
            subjects = subjectsList,
            totalPages = pages
        )

        val id = isbn13 ?: isbn10 ?: "openlib_$olKey"

        return BookMetadata(
            id = id,
            title = title,
            subtitle = subtitle,
            authors = authorsList,
            isbn10 = isbn10,
            isbn13 = isbn13,
            publisher = publisher,
            publishedYear = year,
            totalPages = pages,
            overview = firstSentence,
            coverUrl = highResCover,
            thumbnailCoverUrl = thumbCover,
            rating = rating,
            ratingsCount = ratingsCount,
            externalUrl = if (olKey.isNotBlank()) "https://openlibrary.org$olKey" else null,
            source = BookSearchSource.OPEN_LIBRARY,
            rawSubjects = subjectsList,
            normalizedGenres = attributeResult.genres,
            suggestedMoods = attributeResult.moods,
            suggestedPacing = attributeResult.pacing,
            recommendationVector = attributeResult.recommendationVector
        )
    }

    private fun parseBibBook(isbn: String, bookObj: JSONObject): BookMetadata? {
        val title = bookObj.optString("title").trim()
        if (title.isBlank()) return null
        val subtitle = bookObj.optString("subtitle").takeIf { it.isNotBlank() }

        val authorsList = mutableListOf<String>()
        val authorsArr = bookObj.optJSONArray("authors")
        if (authorsArr != null) {
            for (i in 0 until authorsArr.length()) {
                val a = authorsArr.optJSONObject(i)?.optString("name")?.trim() ?: ""
                if (a.isNotBlank()) authorsList.add(a)
            }
        }

        val (isbn10, isbn13) = IsbnNormalizer.resolveIsbnPair(isbn)

        val publisher = bookObj.optJSONArray("publishers")?.optJSONObject(0)?.optString("name")
        val publishDate = bookObj.optString("publish_date").takeIf { it.isNotBlank() }
        val year = if (publishDate != null && publishDate.length >= 4) {
            Regex("""\b(19\d{2}|20\d{2})\b""").find(publishDate)?.value
        } else null

        val pages = bookObj.optInt("number_of_pages", 0).takeIf { it > 0 }

        val coverObj = bookObj.optJSONObject("cover")
        val coverUrl = coverObj?.optString("large")
            ?: coverObj?.optString("medium")
            ?: "https://covers.openlibrary.org/b/isbn/$isbn-L.jpg?default=false"

        val subjectsList = mutableListOf<String>()
        val subArr = bookObj.optJSONArray("subjects")
        if (subArr != null) {
            for (i in 0 until minOf(subArr.length(), 20)) {
                val s = subArr.optJSONObject(i)?.optString("name")?.trim() ?: ""
                if (s.isNotBlank()) subjectsList.add(s)
            }
        }

        val attributeResult = AttributeMapper.mapAttributes(
            categories = emptyList(),
            subjects = subjectsList,
            totalPages = pages
        )

        return BookMetadata(
            id = isbn13 ?: isbn10 ?: "openlib_$isbn",
            title = title,
            subtitle = subtitle,
            authors = authorsList,
            isbn10 = isbn10,
            isbn13 = isbn13,
            publisher = publisher,
            publishedYear = year,
            publishedDate = publishDate,
            totalPages = pages,
            coverUrl = coverUrl,
            thumbnailCoverUrl = coverObj?.optString("small") ?: coverUrl,
            externalUrl = bookObj.optString("url").takeIf { it.isNotBlank() },
            source = BookSearchSource.OPEN_LIBRARY,
            rawSubjects = subjectsList,
            normalizedGenres = attributeResult.genres,
            suggestedMoods = attributeResult.moods,
            suggestedPacing = attributeResult.pacing,
            recommendationVector = attributeResult.recommendationVector
        )
    }

    private fun mapOlLanguage(lang: String): String {
        val code = when (lang.lowercase()) {
            "hi" -> "hin"
            "ta" -> "tam"
            "te" -> "tel"
            "ml" -> "mal"
            "kn" -> "kan"
            "bn" -> "ben"
            "mr" -> "mar"
            "en" -> "eng"
            "es" -> "spa"
            "fr" -> "fre"
            "de" -> "ger"
            "zh" -> "chi"
            "ja" -> "jpn"
            "ko" -> "kor"
            else -> lang.lowercase()
        }
        return "&language=$code"
    }
}

