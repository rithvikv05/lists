package books.engine.model

enum class BookSearchSource(val displayName: String) {
    GOOGLE_BOOKS("Google Books"),
    OPEN_LIBRARY("Open Library"),
    ISBN_DIRECT("Direct ISBN Lookup"),
    ISBNDB("ISBNdb"),
    MANUAL("Manual Entry"),
    COMBINED("Multi-Provider Aggregated")
}

enum class BookSearchType {
    ALL,
    TITLE,
    AUTHOR,
    ISBN
}

data class BookSearchFilter(
    val query: String,
    val searchType: BookSearchType = BookSearchType.ALL,
    val targetLanguage: String? = null,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val maxResults: Int = 20,
    val requireIsbn: Boolean = false
)

