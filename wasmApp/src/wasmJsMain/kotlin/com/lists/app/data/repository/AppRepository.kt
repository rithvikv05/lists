package com.lists.app.data.repository

import com.lists.app.currentTimeMillis
import com.lists.app.data.api.SearchRepository
import com.lists.app.data.db.ListItem
import com.lists.app.data.db.ListWithStats
import com.lists.app.data.db.UserList
import com.lists.app.data.model.Category
import com.lists.app.data.model.ItemStatus
import com.lists.app.data.model.SearchResult
import com.lists.app.data.store.WebStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AppRepository(val context: Any? = null) {
    private val json = Json { ignoreUnknownKeys = true }
    private val searchRepo = SearchRepository()

    val allListsWithStats: Flow<List<ListWithStats>> = combine(WebStore.lists, WebStore.items) { lists, items ->
        lists.sortedBy { it.customOrderIndex }.map { list ->
            val listItems = items.filter { it.listId == list.id || it.listUuid == list.uuid }
            val completed = listItems.count { it.marked || it.status == ItemStatus.COMPLETED }
            ListWithStats(
                list = list,
                itemCount = listItems.size,
                completedCount = completed
            )
        }
    }

    val allLists: Flow<List<UserList>> = WebStore.lists

    fun getList(id: Long): Flow<UserList?> = WebStore.lists.map { it.firstOrNull { l -> l.id == id } }
    suspend fun getListSync(id: Long): UserList? = WebStore.lists.value.firstOrNull { it.id == id }

    fun getItems(listId: Long): Flow<List<ListItem>> = combine(WebStore.lists, WebStore.items) { lists, items ->
        val parent = lists.firstOrNull { it.id == listId }
        val parentUuid = parent?.uuid ?: ""
        items.filter { item -> item.listId == listId || (parentUuid.isNotEmpty() && item.listUuid == parentUuid) }
    }

    fun getItem(id: Long): Flow<ListItem?> = WebStore.items.map { it.firstOrNull { item -> item.id == id } }
    suspend fun getItemSync(id: Long): ListItem? = WebStore.items.value.firstOrNull { it.id == id }

    fun getPreviewCovers(listId: Long): Flow<List<String>> = combine(WebStore.lists, WebStore.items) { lists, items ->
        val parent = lists.firstOrNull { it.id == listId }
        val parentUuid = parent?.uuid ?: ""
        items.filter { item ->
            (item.listId == listId || (parentUuid.isNotEmpty() && item.listUuid == parentUuid)) && item.coverUrl.isNotBlank()
        }.map { it.coverUrl }.take(4)
    }

    suspend fun createList(
        name: String,
        description: String = "",
        category: String
    ): Long {
        val cat = Category.fromId(category)
        val created = WebStore.addList(name, cat)
        return created.id
    }

    suspend fun reorderLists(reorderedIds: List<Long>) {
        WebStore.reorderLists(reorderedIds)
    }

    suspend fun updateList(list: UserList) {
        WebStore.updateList(list)
    }

    suspend fun deleteList(list: UserList) {
        WebStore.deleteList(list)
    }

    suspend fun addItemFromSearch(listId: Long, result: SearchResult): Long {
        val parentList = WebStore.lists.value.firstOrNull { it.id == listId }
        val maxId = WebStore.items.value.maxOfOrNull { it.id } ?: 0L
        val newId = maxId + 1
        val item = ListItem(
            id = newId,
            listId = listId,
            listUuid = parentList?.uuid ?: "",
            title = result.title,
            originalTitle = result.originalTitle,
            overview = result.overview,
            creator = result.creator,
            coverUrl = result.coverUrl,
            backdropUrl = result.backdropUrl,
            releaseDate = result.releaseDate,
            releaseYear = result.releaseYear,
            rating = result.rating,
            category = result.category.id,
            externalUrl = result.externalUrl,
            totalEpisodes = result.totalEpisodes,
            totalPages = result.totalPages
        )
        WebStore.addItem(item)
        return newId
    }

    suspend fun addCustomItem(
        listId: Long,
        title: String,
        category: String,
        releaseYear: String = "",
        creator: String = "",
        coverUrl: String = "",
        overview: String = "",
        totalEpisodes: Int = 0,
        totalPages: Int = 0
    ): Long {
        val parentList = WebStore.lists.value.firstOrNull { it.id == listId }
        val maxId = WebStore.items.value.maxOfOrNull { it.id } ?: 0L
        val newId = maxId + 1
        val item = ListItem(
            id = newId,
            listId = listId,
            listUuid = parentList?.uuid ?: "",
            title = title,
            category = category,
            releaseYear = releaseYear,
            creator = creator,
            coverUrl = coverUrl,
            overview = overview,
            totalEpisodes = totalEpisodes,
            totalPages = totalPages
        )
        WebStore.addItem(item)
        return newId
    }

    suspend fun updateItem(item: ListItem) {
        WebStore.updateItem(item)
    }

    suspend fun deleteItem(item: ListItem) {
        WebStore.deleteItem(item)
    }

    suspend fun toggleItemMarked(item: ListItem) {
        val newMarked = !item.marked
        val newStatus = if (newMarked) ItemStatus.COMPLETED else ItemStatus.BACKLOG
        val now = currentTimeMillis()
        WebStore.updateItem(
            item.copy(
                marked = newMarked,
                status = newStatus,
                markedAt = if (newMarked) now else null,
                updatedAt = now
            )
        )
    }

    suspend fun toggleItemPinned(item: ListItem) {
        val newPinned = !item.pinned
        val now = currentTimeMillis()
        WebStore.updateItem(
            item.copy(
                pinned = newPinned,
                pinnedAt = if (newPinned) now else null,
                updatedAt = now
            )
        )
    }

    suspend fun updateItemProgress(
        item: ListItem,
        currentEpisode: Int,
        totalEpisodes: Int,
        currentPage: Int,
        totalPages: Int
    ) {
        val isCompleted = (totalEpisodes > 0 && currentEpisode >= totalEpisodes) ||
                (totalPages > 0 && currentPage >= totalPages)
        val isInProgress = currentEpisode > 0 || currentPage > 0
        val newStatus = when {
            isCompleted -> ItemStatus.COMPLETED
            isInProgress -> ItemStatus.IN_PROGRESS
            else -> item.status
        }
        val now = currentTimeMillis()
        WebStore.updateItem(
            item.copy(
                currentEpisode = currentEpisode,
                totalEpisodes = if (totalEpisodes > 0) totalEpisodes else item.totalEpisodes,
                currentPage = currentPage,
                totalPages = if (totalPages > 0) totalPages else item.totalPages,
                status = newStatus,
                marked = isCompleted,
                markedAt = if (isCompleted) now else item.markedAt,
                updatedAt = now
            )
        )
    }

    suspend fun updateItemReviewRating(item: ListItem, reviewRating: Int) {
        WebStore.updateItem(item.copy(reviewRating = reviewRating, updatedAt = currentTimeMillis()))
    }

    suspend fun saveCachedEpisodes(item: ListItem, episodesJson: String) {
        WebStore.updateItem(item.copy(episodesDataJson = episodesJson, updatedAt = currentTimeMillis()))
    }

    fun parseCheckedKeys(jsonStr: String): Set<String> {
        if (jsonStr.isBlank()) return emptySet()
        return try {
            json.decodeFromString<List<String>>(jsonStr).toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    suspend fun toggleEpisodeChecked(item: ListItem, key: String, allKeys: List<String>) {
        val currentSet = parseCheckedKeys(item.checkedEpisodesJson).toMutableSet()
        if (currentSet.contains(key)) {
            currentSet.remove(key)
        } else {
            currentSet.add(key)
        }
        val allCompleted = allKeys.isNotEmpty() && allKeys.all { currentSet.contains(it) }
        val newStatus = when {
            allCompleted -> ItemStatus.COMPLETED
            currentSet.isNotEmpty() -> ItemStatus.IN_PROGRESS
            else -> ItemStatus.BACKLOG
        }
        val now = currentTimeMillis()
        val jsonArray = json.encodeToString(currentSet.toList())
        WebStore.updateItem(
            item.copy(
                checkedEpisodesJson = jsonArray,
                marked = allCompleted,
                status = newStatus,
                markedAt = if (allCompleted) now else null,
                updatedAt = now
            )
        )
    }

    suspend fun setSeasonEpisodesChecked(
        item: ListItem,
        seasonKeys: List<String>,
        shouldCheck: Boolean,
        allKeys: List<String>
    ) {
        val currentSet = parseCheckedKeys(item.checkedEpisodesJson).toMutableSet()
        if (shouldCheck) {
            currentSet.addAll(seasonKeys)
        } else {
            currentSet.removeAll(seasonKeys.toSet())
        }
        val allCompleted = allKeys.isNotEmpty() && allKeys.all { currentSet.contains(it) }
        val newStatus = when {
            allCompleted -> ItemStatus.COMPLETED
            currentSet.isNotEmpty() -> ItemStatus.IN_PROGRESS
            else -> ItemStatus.BACKLOG
        }
        val now = currentTimeMillis()
        val jsonArray = json.encodeToString(currentSet.toList())
        WebStore.updateItem(
            item.copy(
                checkedEpisodesJson = jsonArray,
                marked = allCompleted,
                status = newStatus,
                markedAt = if (allCompleted) now else null,
                updatedAt = now
            )
        )
    }

    suspend fun clearCompleted(listId: Long) {
        WebStore.clearCompletedItems(listId)
    }

    suspend fun ensureItemCover(item: ListItem) {
        if (item.coverUrl.isBlank()) {
            val cat = Category.fromId(item.category)
            val cover = searchRepo.fetchCover(item.title, cat)
            if (!cover.isNullOrBlank()) {
                WebStore.updateItem(item.copy(coverUrl = cover, updatedAt = currentTimeMillis()))
            }
        }
    }

    fun exportDataToJson(): String {
        return WebStore.exportBackupJson()
    }

    fun importDataFromJson(jsonStr: String): Boolean {
        return WebStore.restoreBackupJson(jsonStr)
    }
}
