package books.engine.aggregator

import books.engine.normalizer.IsbnNormalizer

/**
 * Intelligent Cover Resolver that determines the highest fidelity cover art
 * across Open Library and Google Books.
 */
object CoverResolver {

    /**
     * Compute the best cover image URL for a given book record.
     */
    fun resolveBestCover(
        primaryIsbn: String?,
        openLibraryCoverId: Int? = null,
        googleCoverUrl: String? = null
    ): Pair<String?, String?> {
        val validIsbn = primaryIsbn?.let { IsbnNormalizer.clean(it) }?.takeIf { IsbnNormalizer.isValidIsbn(it) }

        // Candidate 1: Open Library Cover via ISBN (High resolution)
        val olIsbnCover = validIsbn?.let { "https://covers.openlibrary.org/b/isbn/$it-L.jpg?default=false" }

        // Candidate 2: Open Library Cover via internal ID
        val olIdCover = if (openLibraryCoverId != null && openLibraryCoverId > 0) {
            "https://covers.openlibrary.org/b/id/$openLibraryCoverId-L.jpg?default=false"
        } else null

        // Candidate 3: Google Books Cover cleaned for highest resolution
        val cleanedGoogleCover = googleCoverUrl?.let { cleanGoogleCoverUrl(it) }

        val bestHighRes = olIsbnCover ?: olIdCover ?: cleanedGoogleCover
        val bestThumbnail = cleanedGoogleCover ?: (validIsbn?.let { "https://covers.openlibrary.org/b/isbn/$it-M.jpg" })

        return Pair(bestHighRes, bestThumbnail)
    }

    /**
     * Strip degrading query parameters from Google Books image links.
     */
    fun cleanGoogleCoverUrl(url: String): String {
        if (url.isBlank()) return ""
        return url.replace("http://", "https://")
            .replace("&edge=curl", "")
            .replace("&zoom=5", "&zoom=1")
    }
}

