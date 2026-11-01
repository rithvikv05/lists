package com.lists.app.data.model

import kotlinx.serialization.Serializable
import kotlin.random.Random

fun generateUuid(): String {
    val chars = "0123456789abcdef"
    return (1..32).map { chars[Random.nextInt(chars.length)] }.chunked(4).joinToString("-") { it.joinToString("") }
}

@Serializable
data class UserList(
    val id: Long = 0,
    val uuid: String = generateUuid(),
    val name: String,
    val description: String = "",
    val category: String, // MOVIE, TV_SHOW, BOOK, GAME
    val iconName: String = "",
    val colorHex: String = "",
    val customOrderIndex: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val sortingOption: String = "DATE_ADDED_DESC",
    val filterOption: String = "ALL",
    val viewMode: String = "GRID_2"
)

@Serializable
data class ListItem(
    val id: Long = 0,
    val uuid: String = generateUuid(),
    val listId: Long = 0,
    val listUuid: String = "",
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
    val checkedEpisodesJson: String = "",
    val episodesDataJson: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class Tombstone(
    val entityUuid: String,
    val entityType: String, // "LIST" or "ITEM"
    val deletedAt: Long
)

@Serializable
data class BackupData(
    val version: Int = 1,
    val exportedAt: Long = 0L,
    val lists: List<UserList> = emptyList(),
    val items: List<ListItem> = emptyList(),
    val tombstones: List<Tombstone> = emptyList()
)
