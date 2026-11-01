package com.lists.app.data.repository

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.lists.app.data.api.SearchRepository
import com.lists.app.data.db.AppDatabase
import com.lists.app.data.db.ListItem
import com.lists.app.data.db.ListWithStats
import com.lists.app.data.db.UserList
import com.lists.app.data.model.Category
import com.lists.app.data.model.ItemStatus
import com.lists.app.data.model.SearchResult

class AppRepository(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val listDao = db.listDao()
    private val itemDao = db.itemDao()
    private val tombstoneManager = com.lists.app.data.sync.TombstoneManager(context)

    private fun notifyDataChanged() {
        try {
            com.lists.app.data.sync.SyncWorker.triggerOneTimeSync(context)
        } catch (_: Exception) {}
    }

    val allListsWithStats: Flow<List<ListWithStats>> = listDao.getAllListsWithStats()

    fun getList(id: Long): Flow<UserList?> = listDao.getListById(id)
    suspend fun getListSync(id: Long): UserList? = listDao.getListByIdSync(id)

    fun getItems(listId: Long): Flow<List<ListItem>> = itemDao.getItemsForList(listId)
    fun getItem(id: Long): Flow<ListItem?> = itemDao.getItemById(id)
    suspend fun getItemSync(id: Long): ListItem? = itemDao.getItemByIdSync(id)

    fun getPreviewCovers(listId: Long): Flow<List<String>> = itemDao.getPreviewCovers(listId)

    suspend fun createList(
        name: String,
        description: String = "",
        category: String
    ): Long = withContext(Dispatchers.IO) {
        val minOrder = listDao.getMinOrderIndex() ?: 0
        val id = listDao.insertList(
            UserList(
                name = name,
                description = description,
                category = category,
                customOrderIndex = minOrder - 1
            )
        )
        notifyDataChanged()
        id
    }

    suspend fun reorderLists(reorderedIds: List<Long>) = withContext(Dispatchers.IO) {
        reorderedIds.forEachIndexed { index, id ->
            listDao.updateCustomOrder(id, index)
        }
        notifyDataChanged()
    }

    suspend fun updateList(list: UserList) = withContext(Dispatchers.IO) {
        listDao.updateList(list.copy(updatedAt = System.currentTimeMillis()))
        notifyDataChanged()
    }

    suspend fun deleteList(list: UserList) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val items = itemDao.getItemsForListSync(list.id)
        items.forEach { item ->
            tombstoneManager.recordDeletedItem(item.uuid, now)
        }
        tombstoneManager.recordDeletedList(list.uuid, now)
        listDao.deleteList(list)
        notifyDataChanged()
    }

    suspend fun updateItemReviewRating(item: ListItem, reviewRating: Int) = withContext(Dispatchers.IO) {
        itemDao.updateItem(item.copy(reviewRating = reviewRating, updatedAt = System.currentTimeMillis()))
        notifyDataChanged()
    }

    suspend fun saveCachedEpisodes(item: ListItem, episodesJson: String) = withContext(Dispatchers.IO) {
        itemDao.updateItem(item.copy(episodesDataJson = episodesJson, updatedAt = System.currentTimeMillis()))
        notifyDataChanged()
    }

    suspend fun toggleEpisodeChecked(item: ListItem, key: String, allKeys: List<String>) = withContext(Dispatchers.IO) {
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
        val jsonArray = JSONArray(currentSet.toList()).toString()
        itemDao.updateItem(
            item.copy(
                checkedEpisodesJson = jsonArray,
                marked = allCompleted,
                status = newStatus,
                markedAt = if (allCompleted) System.currentTimeMillis() else null,
                updatedAt = System.currentTimeMillis()
            )
        )
        notifyDataChanged()
    }

    suspend fun setSeasonEpisodesChecked(item: ListItem, seasonKeys: List<String>, shouldCheck: Boolean, allKeys: List<String>) = withContext(Dispatchers.IO) {
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
        val jsonArray = JSONArray(currentSet.toList()).toString()
        itemDao.updateItem(
            item.copy(
                checkedEpisodesJson = jsonArray,
                marked = allCompleted,
                status = newStatus,
                markedAt = if (allCompleted) System.currentTimeMillis() else null,
                updatedAt = System.currentTimeMillis()
            )
        )
        notifyDataChanged()
    }

    fun parseCheckedKeys(json: String): Set<String> {
        if (json.isBlank()) return emptySet()
        return try {
            val arr = JSONArray(json)
            val set = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                set.add(arr.getString(i))
            }
            set
        } catch (e: Exception) {
            emptySet()
        }
    }

    suspend fun addItemFromSearch(listId: Long, search: SearchResult): Long = withContext(Dispatchers.IO) {
        val item = ListItem(
            listId = listId,
            title = search.title,
            originalTitle = search.originalTitle,
            overview = search.overview,
            creator = search.creator,
            coverUrl = search.coverUrl,
            backdropUrl = search.backdropUrl,
            releaseDate = search.releaseDate,
            releaseYear = search.releaseYear,
            rating = search.rating,
            status = ItemStatus.BACKLOG,
            category = search.category.id,
            externalUrl = search.externalUrl,
            totalEpisodes = search.totalEpisodes,
            totalPages = search.totalPages
        )
        val insertedId = itemDao.insertItem(item)
        notifyDataChanged()
        if (search.coverUrl.isBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val searchRepo = SearchRepository()
                    val resolved = searchRepo.resolveCover(search.title, search.category, search.creator)
                    if (!resolved.isNullOrBlank()) {
                        val current = itemDao.getItemByIdSync(insertedId)
                        if (current != null && current.coverUrl.isBlank()) {
                            itemDao.updateItem(current.copy(coverUrl = resolved, updatedAt = System.currentTimeMillis()))
                            notifyDataChanged()
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        insertedId
    }

    suspend fun ensureItemCover(item: ListItem) = withContext(Dispatchers.IO) {
        if (item.coverUrl.isNotBlank()) return@withContext
        try {
            val searchRepo = SearchRepository()
            val category = Category.fromId(item.category)
            val resolved = searchRepo.resolveCover(item.title, category, item.creator)
            if (!resolved.isNullOrBlank()) {
                val current = itemDao.getItemByIdSync(item.id)
                if (current != null && current.coverUrl.isBlank()) {
                    itemDao.updateItem(current.copy(coverUrl = resolved, updatedAt = System.currentTimeMillis()))
                    notifyDataChanged()
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun addCustomItem(
        listId: Long,
        title: String,
        category: String,
        creator: String = "",
        releaseYear: String = "",
        overview: String = "",
        coverUrl: String = "",
        totalEpisodes: Int = 0,
        totalPages: Int = 0,
        gamePlatform: String = ""
    ): Long = withContext(Dispatchers.IO) {
        val item = ListItem(
            listId = listId,
            title = title,
            creator = creator,
            releaseYear = releaseYear,
            overview = overview,
            coverUrl = coverUrl,
            category = category,
            totalEpisodes = totalEpisodes,
            totalPages = totalPages,
            gamePlatform = gamePlatform
        )
        val id = itemDao.insertItem(item)
        notifyDataChanged()
        id
    }

    suspend fun updateItem(item: ListItem) = withContext(Dispatchers.IO) {
        itemDao.updateItem(item.copy(updatedAt = System.currentTimeMillis()))
        notifyDataChanged()
    }

    suspend fun toggleItemMarked(item: ListItem) = withContext(Dispatchers.IO) {
        val newMarked = !item.marked
        val newStatus = if (newMarked) ItemStatus.COMPLETED else ItemStatus.BACKLOG
        itemDao.updateItem(
            item.copy(
                marked = newMarked,
                status = newStatus,
                markedAt = if (newMarked) System.currentTimeMillis() else null,
                updatedAt = System.currentTimeMillis()
            )
        )
        notifyDataChanged()
    }

    suspend fun toggleItemPinned(item: ListItem) = withContext(Dispatchers.IO) {
        val newPinned = !item.pinned
        itemDao.updateItem(
            item.copy(
                pinned = newPinned,
                pinnedAt = if (newPinned) System.currentTimeMillis() else null,
                updatedAt = System.currentTimeMillis()
            )
        )
        notifyDataChanged()
    }

    suspend fun updateItemProgress(
        item: ListItem,
        season: Int = item.currentSeason,
        episode: Int = item.currentEpisode,
        page: Int = item.currentPage,
        platform: String = item.gamePlatform
    ) = withContext(Dispatchers.IO) {
        val isCompleted = (item.totalEpisodes > 0 && episode >= item.totalEpisodes) ||
                (item.totalPages > 0 && page >= item.totalPages)
        val newStatus = when {
            isCompleted -> ItemStatus.COMPLETED
            episode > 0 || page > 0 -> ItemStatus.IN_PROGRESS
            else -> item.status
        }
        itemDao.updateItem(
            item.copy(
                currentSeason = season,
                currentEpisode = episode,
                currentPage = page,
                gamePlatform = platform,
                status = newStatus,
                marked = isCompleted,
                markedAt = if (isCompleted) System.currentTimeMillis() else item.markedAt,
                updatedAt = System.currentTimeMillis()
            )
        )
        notifyDataChanged()
    }

    suspend fun updateItemRating(item: ListItem, userRating: Float) = withContext(Dispatchers.IO) {
        itemDao.updateItem(
            item.copy(
                userRating = userRating,
                updatedAt = System.currentTimeMillis()
            )
        )
        notifyDataChanged()
    }

    suspend fun updateItemNotes(item: ListItem, notes: String) = withContext(Dispatchers.IO) {
        itemDao.updateItem(
            item.copy(
                userNotes = notes,
                updatedAt = System.currentTimeMillis()
            )
        )
        notifyDataChanged()
    }

    suspend fun deleteItem(item: ListItem) = withContext(Dispatchers.IO) {
        tombstoneManager.recordDeletedItem(item.uuid, System.currentTimeMillis())
        itemDao.deleteItem(item)
        notifyDataChanged()
    }

    suspend fun clearCompleted(listId: Long) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val completed = itemDao.getItemsForListSync(listId).filter { it.marked }
        completed.forEach { item ->
            tombstoneManager.recordDeletedItem(item.uuid, now)
        }
        itemDao.clearCompleted(listId)
        notifyDataChanged()
    }

    // Export full database to JSON string (v2 schema with UUIDs and all fields)
    suspend fun exportDataToJson(): String = withContext(Dispatchers.IO) {
        val lists = listDao.getAllListsSync()
        val items = itemDao.getAllItemsSync()

        val root = JSONObject()
        root.put("schemaVersion", 2)
        root.put("lastModified", System.currentTimeMillis())

        // Export rating scale preferences
        val ratingPrefs = context.getSharedPreferences("lists_rating_prefs", Context.MODE_PRIVATE)
        val prefsObj = JSONObject()
        val ratingObj = JSONObject()
        Category.entries.forEach { cat ->
            val saved = ratingPrefs.getString("rating_scale_${cat.id}", null)
            if (saved != null) ratingObj.put(cat.id, saved)
        }
        prefsObj.put("ratingScales", ratingObj)
        root.put("preferences", prefsObj)

        val listsArr = JSONArray()
        for (l in lists) {
            val obj = JSONObject()
            obj.put("uuid", l.uuid)
            obj.put("id", l.id)
            obj.put("name", l.name)
            obj.put("description", l.description)
            obj.put("category", l.category)
            obj.put("iconName", l.iconName)
            obj.put("colorHex", l.colorHex)
            obj.put("customOrderIndex", l.customOrderIndex)
            obj.put("sortingOption", l.sortingOption)
            obj.put("filterOption", l.filterOption)
            obj.put("viewMode", l.viewMode)
            obj.put("createdAt", l.createdAt)
            obj.put("updatedAt", l.updatedAt)
            listsArr.put(obj)
        }
        root.put("lists", listsArr)

        val itemsArr = JSONArray()
        for (it in items) {
            val obj = JSONObject()
            obj.put("uuid", it.uuid)
            obj.put("listId", it.listId)
            // Find parent list uuid for cross-device reference
            val parentList = lists.firstOrNull { l -> l.id == it.listId }
            obj.put("listUuid", parentList?.uuid ?: "")
            obj.put("title", it.title)
            obj.put("originalTitle", it.originalTitle)
            obj.put("overview", it.overview)
            obj.put("creator", it.creator)
            obj.put("coverUrl", it.coverUrl)
            obj.put("backdropUrl", it.backdropUrl)
            obj.put("releaseDate", it.releaseDate)
            obj.put("releaseYear", it.releaseYear)
            obj.put("rating", it.rating.toDouble())
            obj.put("userRating", it.userRating.toDouble())
            obj.put("status", it.status.name)
            obj.put("marked", it.marked)
            obj.put("markedAt", it.markedAt ?: JSONObject.NULL)
            obj.put("pinned", it.pinned)
            obj.put("pinnedAt", it.pinnedAt ?: JSONObject.NULL)
            obj.put("userNotes", it.userNotes)
            obj.put("externalUrl", it.externalUrl)
            obj.put("category", it.category)
            obj.put("currentSeason", it.currentSeason)
            obj.put("currentEpisode", it.currentEpisode)
            obj.put("totalEpisodes", it.totalEpisodes)
            obj.put("currentPage", it.currentPage)
            obj.put("totalPages", it.totalPages)
            obj.put("gamePlatform", it.gamePlatform)
            obj.put("customOrder", it.customOrder)
            obj.put("reviewRating", it.reviewRating)
            obj.put("checkedEpisodesJson", it.checkedEpisodesJson)
            obj.put("episodesDataJson", it.episodesDataJson)
            obj.put("createdAt", it.createdAt)
            obj.put("updatedAt", it.updatedAt)
            itemsArr.put(obj)
        }
        root.put("items", itemsArr)

        // Export tombstones (deleted lists and items)
        val deletedListsObj = JSONObject()
        tombstoneManager.getDeletedLists().forEach { (k, v) -> deletedListsObj.put(k, v) }
        root.put("deletedLists", deletedListsObj)

        val deletedItemsObj = JSONObject()
        tombstoneManager.getDeletedItems().forEach { (k, v) -> deletedItemsObj.put(k, v) }
        root.put("deletedItems", deletedItemsObj)

        root.toString(2)
    }

    // Restore database from JSON string (backwards compatible with v1 and v2 schemas)
    suspend fun importDataFromJson(jsonStr: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonStr)
            val listsArr = root.optJSONArray("lists") ?: JSONArray()
            val itemsArr = root.optJSONArray("items") ?: JSONArray()

            val idMapping = mutableMapOf<Long, Long>()

            for (i in 0 until listsArr.length()) {
                val obj = listsArr.getJSONObject(i)
                val oldId = obj.optLong("id")
                val name = obj.optString("name", "List")
                val desc = obj.optString("description", "")
                val cat = obj.optString("category", "MOVIE")
                val color = obj.optString("colorHex", "")
                val uuid = obj.optString("uuid", "")

                val newId = listDao.insertList(
                    UserList(
                        uuid = if (uuid.isNotBlank()) uuid else java.util.UUID.randomUUID().toString(),
                        name = name,
                        description = desc,
                        category = cat,
                        colorHex = color,
                        iconName = obj.optString("iconName", ""),
                        customOrderIndex = obj.optInt("customOrderIndex", 0),
                        sortingOption = obj.optString("sortingOption", "DATE_ADDED_DESC"),
                        filterOption = obj.optString("filterOption", "ALL"),
                        viewMode = obj.optString("viewMode", "GRID_2"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
                idMapping[oldId] = newId
            }

            for (i in 0 until itemsArr.length()) {
                val obj = itemsArr.getJSONObject(i)
                val oldListId = obj.optLong("listId")
                val targetListId = idMapping[oldListId] ?: continue
                val uuid = obj.optString("uuid", "")

                val item = ListItem(
                    uuid = if (uuid.isNotBlank()) uuid else java.util.UUID.randomUUID().toString(),
                    listId = targetListId,
                    title = obj.optString("title", "Untitled"),
                    originalTitle = obj.optString("originalTitle", ""),
                    overview = obj.optString("overview", ""),
                    creator = obj.optString("creator", ""),
                    coverUrl = obj.optString("coverUrl", ""),
                    backdropUrl = obj.optString("backdropUrl", ""),
                    releaseDate = obj.optString("releaseDate", ""),
                    releaseYear = obj.optString("releaseYear", ""),
                    rating = obj.optDouble("rating", 0.0).toFloat(),
                    userRating = obj.optDouble("userRating", 0.0).toFloat(),
                    status = try {
                        ItemStatus.valueOf(obj.optString("status", ItemStatus.BACKLOG.name))
                    } catch (e: Exception) {
                        ItemStatus.BACKLOG
                    },
                    marked = obj.optBoolean("marked", false),
                    markedAt = if (obj.isNull("markedAt")) null else obj.optLong("markedAt"),
                    pinned = obj.optBoolean("pinned", false),
                    pinnedAt = if (obj.isNull("pinnedAt")) null else obj.optLong("pinnedAt"),
                    userNotes = obj.optString("userNotes", ""),
                    externalUrl = obj.optString("externalUrl", ""),
                    category = obj.optString("category", "MOVIE"),
                    currentSeason = obj.optInt("currentSeason", 1),
                    currentEpisode = obj.optInt("currentEpisode", 0),
                    totalEpisodes = obj.optInt("totalEpisodes", 0),
                    currentPage = obj.optInt("currentPage", 0),
                    totalPages = obj.optInt("totalPages", 0),
                    gamePlatform = obj.optString("gamePlatform", ""),
                    customOrder = obj.optInt("customOrder", 0),
                    reviewRating = obj.optInt("reviewRating", 0),
                    checkedEpisodesJson = obj.optString("checkedEpisodesJson", ""),
                    episodesDataJson = obj.optString("episodesDataJson", ""),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
                itemDao.insertItem(item)
            }

            // Import preferences if present
            val prefsObj = root.optJSONObject("preferences")
            if (prefsObj != null) {
                val ratingObj = prefsObj.optJSONObject("ratingScales")
                if (ratingObj != null) {
                    val ratingPrefs = context.getSharedPreferences("lists_rating_prefs", Context.MODE_PRIVATE).edit()
                    ratingObj.keys().forEach { key ->
                        ratingPrefs.putString("rating_scale_$key", ratingObj.optString(key))
                    }
                    ratingPrefs.apply()
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Smart merge from remote JSON — compares items by UUID and keeps the one
     * with the later updatedAt timestamp. New items from remote are inserted,
     * local-only items are kept.
     * Returns the merged JSON string suitable for uploading back to remote.
     */
    suspend fun mergeFromRemote(remoteJsonStr: String): String = withContext(Dispatchers.IO) {
        try {
            val remote = JSONObject(remoteJsonStr)
            val remoteLists = remote.optJSONArray("lists") ?: JSONArray()
            val remoteItems = remote.optJSONArray("items") ?: JSONArray()

            // 1. Process and merge tombstones (deletions)
            val remoteDeletedLists = mutableMapOf<String, Long>()
            val remoteDeletedListsObj = remote.optJSONObject("deletedLists")
            if (remoteDeletedListsObj != null) {
                remoteDeletedListsObj.keys().forEach { k ->
                    remoteDeletedLists[k] = remoteDeletedListsObj.optLong(k)
                }
            }

            val remoteDeletedItems = mutableMapOf<String, Long>()
            val remoteDeletedItemsObj = remote.optJSONObject("deletedItems")
            if (remoteDeletedItemsObj != null) {
                remoteDeletedItemsObj.keys().forEach { k ->
                    remoteDeletedItems[k] = remoteDeletedItemsObj.optLong(k)
                }
            }

            tombstoneManager.mergeRemoteTombstones(remoteDeletedLists, remoteDeletedItems)
            val allDeletedLists = tombstoneManager.getDeletedLists()
            val allDeletedItems = tombstoneManager.getDeletedItems()

            // 2. Delete any local lists that have been marked deleted
            val currentLocalLists = listDao.getAllListsSync()
            for (localList in currentLocalLists) {
                val deletedAt = allDeletedLists[localList.uuid]
                if (deletedAt != null && deletedAt >= localList.updatedAt) {
                    listDao.deleteList(localList)
                }
            }

            // Delete any local items that have been marked deleted
            val currentLocalItems = itemDao.getAllItemsSync()
            for (localItem in currentLocalItems) {
                val deletedAt = allDeletedItems[localItem.uuid]
                if (deletedAt != null && deletedAt >= localItem.updatedAt) {
                    itemDao.deleteItem(localItem)
                }
            }

            // 3. Clean up duplicates with identical category and name (merge items into one)
            val localListsAfterDeletions = listDao.getAllListsSync()
            val grouped = localListsAfterDeletions.groupBy { "${it.category}_${it.name.trim().lowercase()}" }
            for ((_, group) in grouped) {
                if (group.size > 1) {
                    val listCounts = group.associateWith { itemDao.getItemsForListSync(it.id).size }
                    val sorted = group.sortedByDescending { listCounts[it] ?: 0 }
                    val primary = sorted.first()
                    val duplicates = sorted.drop(1)
                    for (dup in duplicates) {
                        val dupItems = itemDao.getItemsForListSync(dup.id)
                        dupItems.forEach { item ->
                            itemDao.updateItem(item.copy(listId = primary.id))
                        }
                        listDao.deleteList(dup)
                    }
                }
            }

            // Clean up duplicate items with identical title within the same list
            for (l in listDao.getAllListsSync()) {
                val listItems = itemDao.getItemsForListSync(l.id)
                val itemsGrouped = listItems.groupBy { it.title.trim().lowercase() }
                for ((_, itemGroup) in itemsGrouped) {
                    if (itemGroup.size > 1) {
                        val primaryItem = itemGroup.maxByOrNull { item ->
                            var score = 0
                            if (item.marked) score += 10
                            if (item.userRating > 0f) score += 10
                            if (item.userNotes.isNotBlank()) score += 10
                            if (item.currentEpisode > 0 || item.currentPage > 0) score += 10
                            score
                        } ?: itemGroup.first()
                        val dups = itemGroup.filter { it.id != primaryItem.id }
                        dups.forEach { itemDao.deleteItem(it) }
                    }
                }
            }

            // 4. Build maps of remote lists/items by UUID
            val remoteListMap = mutableMapOf<String, JSONObject>()
            for (i in 0 until remoteLists.length()) {
                val obj = remoteLists.getJSONObject(i)
                val uuid = obj.optString("uuid", "")
                if (uuid.isNotBlank()) remoteListMap[uuid] = obj
            }

            val remoteItemMap = mutableMapOf<String, JSONObject>()
            for (i in 0 until remoteItems.length()) {
                val obj = remoteItems.getJSONObject(i)
                val uuid = obj.optString("uuid", "")
                if (uuid.isNotBlank()) remoteItemMap[uuid] = obj
            }

            // 5. Merge remote lists
            for ((uuid, remoteObj) in remoteListMap) {
                // If this list was deleted, do not restore it!
                val remoteUpdated = remoteObj.optLong("updatedAt", 0)
                val deletedAt = allDeletedLists[uuid]
                if (deletedAt != null && deletedAt >= remoteUpdated) {
                    continue
                }

                // Check by UUID first
                var localList = listDao.getListByUuid(uuid)

                // If not found by UUID, check if a local list has the exact same name & category
                // to avoid creating a duplicate folder!
                if (localList == null) {
                    val remoteName = remoteObj.optString("name", "List").trim().lowercase()
                    val remoteCat = remoteObj.optString("category", "MOVIE")
                    val matchingList = listDao.getAllListsSync().firstOrNull {
                        it.category == remoteCat && it.name.trim().lowercase() == remoteName
                    }
                    if (matchingList != null) {
                        // Adopt remote UUID for this list
                        val updated = matchingList.copy(uuid = uuid)
                        listDao.updateList(updated)
                        localList = updated
                    }
                }

                if (localList == null) {
                    // New from remote — insert
                    listDao.insertList(
                        UserList(
                            uuid = uuid,
                            name = remoteObj.optString("name", "List"),
                            description = remoteObj.optString("description", ""),
                            category = remoteObj.optString("category", "MOVIE"),
                            iconName = remoteObj.optString("iconName", ""),
                            colorHex = remoteObj.optString("colorHex", ""),
                            customOrderIndex = remoteObj.optInt("customOrderIndex", 0),
                            sortingOption = remoteObj.optString("sortingOption", "DATE_ADDED_DESC"),
                            filterOption = remoteObj.optString("filterOption", "ALL"),
                            viewMode = remoteObj.optString("viewMode", "GRID_2"),
                            createdAt = remoteObj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = remoteObj.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                } else {
                    // Exists locally — keep the one with later updatedAt
                    if (remoteUpdated > localList.updatedAt) {
                        listDao.updateList(
                            localList.copy(
                                name = remoteObj.optString("name", localList.name),
                                description = remoteObj.optString("description", localList.description),
                                category = remoteObj.optString("category", localList.category),
                                iconName = remoteObj.optString("iconName", localList.iconName),
                                colorHex = remoteObj.optString("colorHex", localList.colorHex),
                                customOrderIndex = remoteObj.optInt("customOrderIndex", localList.customOrderIndex),
                                sortingOption = remoteObj.optString("sortingOption", localList.sortingOption),
                                filterOption = remoteObj.optString("filterOption", localList.filterOption),
                                viewMode = remoteObj.optString("viewMode", localList.viewMode),
                                updatedAt = remoteUpdated
                            )
                        )
                    }
                }
            }

            // 6. Merge remote items
            for ((uuid, remoteObj) in remoteItemMap) {
                // If item was deleted, skip
                val remoteUpdated = remoteObj.optLong("updatedAt", 0)
                val deletedAt = allDeletedItems[uuid]
                if (deletedAt != null && deletedAt >= remoteUpdated) {
                    continue
                }

                var localItem = itemDao.getItemByUuid(uuid)
                val listUuid = remoteObj.optString("listUuid", "")
                val parentList = if (listUuid.isNotBlank()) listDao.getListByUuid(listUuid) else null

                if (localItem == null && parentList != null) {
                    val remoteTitle = remoteObj.optString("title", "").trim()
                    if (remoteTitle.isNotBlank()) {
                        val matchingItem = itemDao.getItemsForListSync(parentList.id).firstOrNull {
                            it.title.trim().equals(remoteTitle, ignoreCase = true)
                        }
                        if (matchingItem != null) {
                            val updated = matchingItem.copy(uuid = uuid)
                            itemDao.updateItem(updated)
                            localItem = updated
                        }
                    }
                }

                if (localItem == null && parentList != null) {
                    // New from remote — insert
                    itemDao.insertItem(
                        ListItem(
                            uuid = uuid,
                            listId = parentList.id,
                            title = remoteObj.optString("title", "Untitled"),
                            originalTitle = remoteObj.optString("originalTitle", ""),
                            overview = remoteObj.optString("overview", ""),
                            creator = remoteObj.optString("creator", ""),
                            coverUrl = remoteObj.optString("coverUrl", ""),
                            backdropUrl = remoteObj.optString("backdropUrl", ""),
                            releaseDate = remoteObj.optString("releaseDate", ""),
                            releaseYear = remoteObj.optString("releaseYear", ""),
                            rating = remoteObj.optDouble("rating", 0.0).toFloat(),
                            userRating = remoteObj.optDouble("userRating", 0.0).toFloat(),
                            status = try {
                                ItemStatus.valueOf(remoteObj.optString("status", ItemStatus.BACKLOG.name))
                            } catch (e: Exception) { ItemStatus.BACKLOG },
                            marked = remoteObj.optBoolean("marked", false),
                            markedAt = if (remoteObj.isNull("markedAt")) null else remoteObj.optLong("markedAt"),
                            pinned = remoteObj.optBoolean("pinned", false),
                            pinnedAt = if (remoteObj.isNull("pinnedAt")) null else remoteObj.optLong("pinnedAt"),
                            userNotes = remoteObj.optString("userNotes", ""),
                            externalUrl = remoteObj.optString("externalUrl", ""),
                            category = remoteObj.optString("category", "MOVIE"),
                            currentSeason = remoteObj.optInt("currentSeason", 1),
                            currentEpisode = remoteObj.optInt("currentEpisode", 0),
                            totalEpisodes = remoteObj.optInt("totalEpisodes", 0),
                            currentPage = remoteObj.optInt("currentPage", 0),
                            totalPages = remoteObj.optInt("totalPages", 0),
                            gamePlatform = remoteObj.optString("gamePlatform", ""),
                            customOrder = remoteObj.optInt("customOrder", 0),
                            reviewRating = remoteObj.optInt("reviewRating", 0),
                            checkedEpisodesJson = remoteObj.optString("checkedEpisodesJson", ""),
                            episodesDataJson = remoteObj.optString("episodesDataJson", ""),
                            createdAt = remoteObj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = remoteObj.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                } else if (localItem != null) {
                    // Exists locally — keep the one with later updatedAt
                    if (remoteUpdated > localItem.updatedAt) {
                        val resolvedListId = parentList?.id ?: localItem.listId
                        itemDao.updateItem(
                            localItem.copy(
                                listId = resolvedListId,
                                title = remoteObj.optString("title", localItem.title),
                                originalTitle = remoteObj.optString("originalTitle", localItem.originalTitle),
                                overview = remoteObj.optString("overview", localItem.overview),
                                creator = remoteObj.optString("creator", localItem.creator),
                                coverUrl = remoteObj.optString("coverUrl", localItem.coverUrl),
                                backdropUrl = remoteObj.optString("backdropUrl", localItem.backdropUrl),
                                releaseDate = remoteObj.optString("releaseDate", localItem.releaseDate),
                                releaseYear = remoteObj.optString("releaseYear", localItem.releaseYear),
                                rating = remoteObj.optDouble("rating", localItem.rating.toDouble()).toFloat(),
                                userRating = remoteObj.optDouble("userRating", localItem.userRating.toDouble()).toFloat(),
                                status = try {
                                    ItemStatus.valueOf(remoteObj.optString("status", localItem.status.name))
                                } catch (e: Exception) { localItem.status },
                                marked = remoteObj.optBoolean("marked", localItem.marked),
                                markedAt = if (remoteObj.isNull("markedAt")) null else remoteObj.optLong("markedAt"),
                                pinned = remoteObj.optBoolean("pinned", localItem.pinned),
                                pinnedAt = if (remoteObj.isNull("pinnedAt")) null else remoteObj.optLong("pinnedAt"),
                                userNotes = remoteObj.optString("userNotes", localItem.userNotes),
                                externalUrl = remoteObj.optString("externalUrl", localItem.externalUrl),
                                currentSeason = remoteObj.optInt("currentSeason", localItem.currentSeason),
                                currentEpisode = remoteObj.optInt("currentEpisode", localItem.currentEpisode),
                                totalEpisodes = remoteObj.optInt("totalEpisodes", localItem.totalEpisodes),
                                currentPage = remoteObj.optInt("currentPage", localItem.currentPage),
                                totalPages = remoteObj.optInt("totalPages", localItem.totalPages),
                                gamePlatform = remoteObj.optString("gamePlatform", localItem.gamePlatform),
                                customOrder = remoteObj.optInt("customOrder", localItem.customOrder),
                                reviewRating = remoteObj.optInt("reviewRating", localItem.reviewRating),
                                checkedEpisodesJson = remoteObj.optString("checkedEpisodesJson", localItem.checkedEpisodesJson),
                                episodesDataJson = remoteObj.optString("episodesDataJson", localItem.episodesDataJson),
                                updatedAt = remoteUpdated
                            )
                        )
                    }
                }
            }

            // 7. Merge preferences from remote if newer
            val remotePrefs = remote.optJSONObject("preferences")
            if (remotePrefs != null) {
                val remoteRatings = remotePrefs.optJSONObject("ratingScales")
                if (remoteRatings != null) {
                    val localPrefs = context.getSharedPreferences("lists_rating_prefs", Context.MODE_PRIVATE)
                    val editor = localPrefs.edit()
                    remoteRatings.keys().forEach { key ->
                        if (!localPrefs.contains("rating_scale_$key")) {
                            editor.putString("rating_scale_$key", remoteRatings.optString(key))
                        }
                    }
                    editor.apply()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Return the fresh merged export
        exportDataToJson()
    }
}
