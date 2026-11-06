package books.engine.provider

import books.engine.model.BookMetadata
import books.engine.model.BookSearchFilter
import books.engine.model.BookSearchSource

/**
 * Pluggable provider interface for book metadata sources.
 */
interface BookSearchProvider {
    val source: BookSearchSource
    val isEnabled: Boolean get() = true

    /** Search books using a filter criteria (query, author, title, etc.) */
    suspend fun search(filter: BookSearchFilter): List<BookMetadata>

    /** Direct exact-match lookup by ISBN-10 or ISBN-13 */
    suspend fun lookupByIsbn(isbn: String): BookMetadata?
}

