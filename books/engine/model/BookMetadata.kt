package books.engine.model

/**
 * Robust, fully normalized book metadata model.
 * Designed to power both high-precision book tracking and the on-device recommendation engine.
 */
data class BookMetadata(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val authors: List<String> = emptyList(),
    val isbn10: String? = null,
    val isbn13: String? = null,
    val publisher: String? = null,
    val publishedYear: String? = null,
    val publishedDate: String? = null,
    val totalPages: Int? = null,
    val language: String? = null,
    val overview: String? = null,
    val coverUrl: String? = null,
    val thumbnailCoverUrl: String? = null,
    val rating: Float? = null,
    val ratingsCount: Int? = null,
    val externalUrl: String? = null,
    val source: BookSearchSource = BookSearchSource.COMBINED,

    // Rich attributes powering the recommendation engine:
    val rawCategories: List<String> = emptyList(),
    val rawSubjects: List<String> = emptyList(),
    val normalizedGenres: Set<BookGenre> = emptySet(),
    val suggestedMoods: Set<BookMood> = emptySet(),
    val suggestedPacing: BookPacing? = null,
    val lengthBracket: BookLengthBracket = BookLengthBracket.fromPages(totalPages),
    val recommendationVector: RecommendationVector = RecommendationVector()
) {
    /** Primary author string formatted as "First Last" or "Author1, Author2" */
    val primaryAuthor: String
        get() = authors.firstOrNull() ?: "Unknown Author"

    val allAuthorsFormatted: String
        get() = authors.joinToString(", ")

    /** True if this record has a verified ISBN */
    val hasValidIsbn: Boolean
        get() = !isbn13.isNullOrBlank() || !isbn10.isNullOrBlank()

    /** Canonical primary ISBN (prefers ISBN-13 over ISBN-10) */
    val primaryIsbn: String?
        get() = isbn13?.takeIf { it.isNotBlank() } ?: isbn10?.takeIf { it.isNotBlank() }

    /** Formatted subtitle or display header */
    val fullDisplayTitle: String
        get() = if (!subtitle.isNullOrBlank() && !title.contains(subtitle, ignoreCase = true)) {
            "$title: $subtitle"
        } else {
            title
        }
}

