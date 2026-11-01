package com.lists.app.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class Category(
    val id: String,
    val displayName: String,
    val searchHint: String
) {
    MOVIE(
        id = "MOVIE",
        displayName = "Movies",
        searchHint = "Type a movie title..."
    ),
    TV_SHOW(
        id = "TV_SHOW",
        displayName = "TV Shows",
        searchHint = "Type a TV show or anime..."
    ),
    BOOK(
        id = "BOOK",
        displayName = "Books",
        searchHint = "Type a book or manga..."
    ),
    GAME(
        id = "GAME",
        displayName = "Games",
        searchHint = "Type a game title..."
    );

    companion object {
        fun fromId(id: String): Category {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: MOVIE
        }
    }
}

@Serializable
enum class ItemStatus(val displayName: String) {
    BACKLOG("Backlog"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Done")
}

@Serializable
enum class SortOption(val displayName: String) {
    DATE_ADDED_DESC("Date Added (Newest)"),
    DATE_ADDED_ASC("Date Added (Oldest)"),
    TITLE_ASC("Title (A-Z)"),
    TITLE_DESC("Title (Z-A)"),
    RATING_DESC("Highest Rating"),
    RELEASE_YEAR_DESC("Release Year")
}

@Serializable
enum class FilterOption(val displayName: String) {
    ALL("All"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Done"),
    BACKLOG("Backlog"),
    PINNED("Pinned")
}

@Serializable
enum class ViewMode {
    GRID_2,
    GRID_3,
    LIST
}

@Serializable
data class SearchResult(
    val title: String,
    val originalTitle: String = "",
    val releaseYear: String = "",
    val releaseDate: String = "",
    val creator: String = "",
    val overview: String = "",
    val coverUrl: String = "",
    val backdropUrl: String = "",
    val rating: Float = 0f,
    val category: Category,
    val externalUrl: String = "",
    val totalEpisodes: Int = 0,
    val totalPages: Int = 0
)

@Serializable
enum class RatingScaleOption(
    val id: String,
    val displayName: String,
    val shortName: String
) {
    SKIP_PERFECTION("skip_perfection", "Skip to Perfection", "Skip → Perfect"),
    SCALE_5("scale_5", "5-Star Scale (0 – 5)", "5-Star"),
    SCALE_10("scale_10", "10-Point Scale (0 – 10)", "10-Point");

    companion object {
        fun fromId(id: String?, default: RatingScaleOption): RatingScaleOption {
            return entries.firstOrNull { it.id == id } ?: default
        }

        fun getDefault(category: Category): RatingScaleOption {
            return when (category) {
                Category.BOOK -> SCALE_5
                else -> SKIP_PERFECTION
            }
        }
    }
}
