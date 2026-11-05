package com.minimal.lists.data.model

import androidx.compose.ui.graphics.Color
import com.minimal.lists.R

enum class CategoryGroup(val displayName: String) {
    WATCH("Watch"),
    READ("Read"),
    PLAY("Play"),
    LISTEN("Listen")
}

enum class Category(
    val id: String,
    val displayName: String,
    val searchHint: String,
    val group: CategoryGroup,
    val color: Color
) {
    MOVIE(
        id = "MOVIE",
        displayName = "Movies",
        searchHint = "Search movies...",
        group = CategoryGroup.WATCH,
        color = Color(0xFFE53935)
    ),
    TV_SHOW(
        id = "TV_SHOW",
        displayName = "TV Shows",
        searchHint = "Search TV shows...",
        group = CategoryGroup.WATCH,
        color = Color(0xFFFB8C00)
    ),
    ANIME(
        id = "ANIME",
        displayName = "Anime",
        searchHint = "Search anime...",
        group = CategoryGroup.WATCH,
        color = Color(0xFFE91E63)
    ),
    BOOK(
        id = "BOOK",
        displayName = "Books",
        searchHint = "Search books...",
        group = CategoryGroup.READ,
        color = Color(0xFF43A047)
    ),
    MANGA(
        id = "MANGA",
        displayName = "Manga",
        searchHint = "Search manga...",
        group = CategoryGroup.READ,
        color = Color(0xFF8E24AA)
    ),
    COMIC(
        id = "COMIC",
        displayName = "Comics",
        searchHint = "Search comics...",
        group = CategoryGroup.READ,
        color = Color(0xFF00ACC1)
    ),
    GAME(
        id = "GAME",
        displayName = "Video Games",
        searchHint = "Search games...",
        group = CategoryGroup.PLAY,
        color = Color(0xFF3949AB)
    ),
    BOARD_GAME(
        id = "BOARD_GAME",
        displayName = "Board Games",
        searchHint = "Search board games...",
        group = CategoryGroup.PLAY,
        color = Color(0xFF6D4C41)
    ),
    PODCAST(
        id = "PODCAST",
        displayName = "Podcasts",
        searchHint = "Search podcasts...",
        group = CategoryGroup.LISTEN,
        color = Color(0xFF00897B)
    ),
    MUSIC(
        id = "MUSIC",
        displayName = "Music",
        searchHint = "Search albums or artists...",
        group = CategoryGroup.LISTEN,
        color = Color(0xFFD81B60)
    );

    val iconRes: Int
        get() = when (this) {
            MOVIE -> R.drawable.ic_type_movie
            TV_SHOW -> R.drawable.ic_type_tv_show
            ANIME -> R.drawable.ic_type_anime
            BOOK -> R.drawable.ic_type_book
            MANGA -> R.drawable.ic_type_manga
            COMIC -> R.drawable.ic_type_book
            GAME -> R.drawable.ic_type_videogame
            BOARD_GAME -> R.drawable.ic_type_videogame
            PODCAST -> R.drawable.ic_type_tv_show
            MUSIC -> R.drawable.ic_type_movie
        }

    companion object {
        val activeCategories = listOf(
            MOVIE,
            TV_SHOW,
            ANIME,
            BOOK,
            MANGA,
            COMIC,
            GAME
        )

        fun fromId(id: String?): Category {
            if (id == null) return MOVIE
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
                ?: when (id.uppercase()) {
                    "VIDEO_GAME", "VIDEOGAME", "GAMES" -> GAME
                    "TV", "TVSHOW", "SERIES" -> TV_SHOW
                    "NOVEL", "MANHWA" -> MANGA
                    "BOARDGAME", "TABLETOP" -> BOARD_GAME
                    "ALBUM", "SONG" -> MUSIC
                    else -> MOVIE
                }
        }
    }
}

enum class ItemStatus(val displayName: String) {
    BACKLOG("Backlog"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Done")
}

enum class SortOption(val displayName: String) {
    DATE_ADDED_DESC("Recently Added"),
    DATE_ADDED_ASC("Date Added (Oldest)"),
    TITLE_ASC("Title (A-Z)"),
    TITLE_DESC("Title (Z-A)"),
    RATING_DESC("Highest Rated"),
    RELEASE_YEAR_DESC("Release Year")
}

enum class FilterOption(val displayName: String) {
    ALL("All"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Done"),
    BACKLOG("Backlog"),
    PINNED("Pinned")
}

enum class ViewMode {
    GRID_2,
    GRID_3,
    LIST
}

enum class HomeLayoutMode(val displayName: String, val description: String) {
    DISCRETE("Discrete List", "Pure minimal flat cards with category dot & metadata (Discrete 1)"),
    BENTO("Bento Grid", "Asymmetric dynamic 2-column bento tiles")
}

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
    val category: Category = Category.MOVIE,
    val externalUrl: String = "",
    val totalEpisodes: Int = 0,
    val totalPages: Int = 0
)

enum class RatingScaleOption(
    val id: String,
    val displayName: String,
    val shortName: String,
    val description: String = ""
) {
    SCALE_5("scale_5", "5-Star Scale (0 – 5)", "5-Star", "Classic 1 to 5 stars with half-step increments"),
    SCALE_10("scale_10", "10-Point Score (0 – 10)", "10-Point", "Standard 1 to 10 decimal grading"),
    SKIP_PERFECTION("skip_perfection", "Tier Hierarchy (Skip → Perfect)", "Tiered", "Skip, Decent, Good, or Perfect grading"),
    SCALE_100("scale_100", "100-Point Score (0 – 100%)", "100-Point", "Detailed 0 to 100 percentage metric"),
    EMOJI_3("emoji_3", "3-Tier Reaction (Dislike, Like, Love)", "3-Tier", "Simplified Dislike, Like, or Love reactions");

    companion object {
        fun fromId(id: String?, default: RatingScaleOption): RatingScaleOption {
            return entries.firstOrNull { it.id == id } ?: default
        }

        fun getDefault(category: Category): RatingScaleOption {
            return when (category) {
                Category.BOOK, Category.MANGA, Category.COMIC -> SCALE_5
                Category.GAME, Category.BOARD_GAME -> SCALE_10
                else -> SCALE_5
            }
        }
    }
}
