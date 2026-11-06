package com.minimal.lists.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.minimal.lists.data.model.ItemStatus
import java.util.UUID

@Entity(tableName = "lists")
data class UserList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val category: String, // MOVIE, TV_SHOW, BOOK, GAME
    val iconName: String = "",
    val colorHex: String = "",
    val customOrderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sortingOption: String = "DATE_ADDED_DESC",
    val filterOption: String = "ALL",
    val viewMode: String = "GRID_2",
    val pinned: Boolean = false,
    val isArchived: Boolean = false
)

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = UserList::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("listId")]
)
data class ListItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val listId: Long,
    val title: String,
    val originalTitle: String = "",
    val overview: String = "",
    val creator: String = "",
    val coverUrl: String = "",
    val backdropUrl: String = "",
    val releaseDate: String = "",
    val releaseYear: String = "",
    val rating: Float = 0f,
    val userRating: Float = 0f,
    val status: ItemStatus = ItemStatus.BACKLOG,
    val marked: Boolean = false,
    val markedAt: Long? = null,
    val pinned: Boolean = false,
    val pinnedAt: Long? = null,
    val userNotes: String = "",
    val externalUrl: String = "",
    val category: String,
    val currentSeason: Int = 1,
    val currentEpisode: Int = 0,
    val totalEpisodes: Int = 0,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val gamePlatform: String = "",
    val customOrder: Int = 0,
    val reviewRating: Int = 0, // 0: None, 1: Skip, 2: Timepass, 3: Go for It, 4: Perfection
    val checkedEpisodesJson: String = "", // JSON array of checked episode keys e.g. ["S1E1", "S1E2"]
    val episodesDataJson: String = "", // cached JSON of episodes list
    val loggedMinutes: Int = 0, // Time logged in minutes (e.g., 525 min = 8h 45m)
    val isbn: String = "", // ISBN-10 or ISBN-13
    val bookLanguage: String = "", // Language code (e.g. en, fr, de, ja)
    val publisher: String = "", // Book publisher
    val readingSessionsJson: String = "", // Serialized JSON array of ReadingSession
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
