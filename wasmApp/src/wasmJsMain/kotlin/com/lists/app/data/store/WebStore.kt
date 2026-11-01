package com.lists.app.data.store

import com.lists.app.currentTimeMillis
import com.lists.app.data.model.*
import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.w3c.dom.get
import org.w3c.dom.set

object WebStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val _lists = MutableStateFlow<List<UserList>>(emptyList())
    val lists: StateFlow<List<UserList>> = _lists.asStateFlow()

    private val _items = MutableStateFlow<List<ListItem>>(emptyList())
    val items: StateFlow<List<ListItem>> = _items.asStateFlow()

    private val _tombstones = MutableStateFlow<List<Tombstone>>(emptyList())
    val tombstones: StateFlow<List<Tombstone>> = _tombstones.asStateFlow()

    private val _ratingPrefs = MutableStateFlow<Map<String, String>>(emptyMap())
    val ratingPrefs: StateFlow<Map<String, String>> = _ratingPrefs.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()

    fun init() {
        val freshKey = "lists_fresh_defaults_cleared_v2"
        if (localStorage[freshKey] == null) {
            localStorage[freshKey] = "true"
            val itemsJson = localStorage["lists_items"] ?: localStorage["listy_items"]
            val hasItems = !itemsJson.isNullOrEmpty() && itemsJson != "[]"
            if (!hasItems) {
                localStorage.removeItem("lists_lists")
                localStorage.removeItem("listy_lists")
                localStorage.removeItem("lists_items")
                localStorage.removeItem("listy_items")
            }
        }
        loadFromStorage()
    }

    private fun loadFromStorage() {
        try {
            val listsJson = localStorage["lists_lists"] ?: localStorage["listy_lists"]
            if (!listsJson.isNullOrEmpty()) {
                _lists.value = json.decodeFromString(listsJson)
            }

            val itemsJson = localStorage["lists_items"] ?: localStorage["listy_items"]
            if (!itemsJson.isNullOrEmpty()) {
                _items.value = json.decodeFromString(itemsJson)
            }

            val tombstonesJson = localStorage["lists_tombstones"] ?: localStorage["listy_tombstones"]
            if (!tombstonesJson.isNullOrEmpty()) {
                _tombstones.value = json.decodeFromString(tombstonesJson)
            }

            val prefsJson = localStorage["lists_rating_prefs"] ?: localStorage["listy_rating_prefs"]
            if (!prefsJson.isNullOrEmpty()) {
                _ratingPrefs.value = json.decodeFromString(prefsJson)
            }

            _userEmail.value = localStorage["lists_user_email"] ?: localStorage["listy_user_email"]
            val lastSync = (localStorage["lists_last_sync"] ?: localStorage["listy_last_sync"])?.toLongOrNull()
            _lastSyncTime.value = lastSync
        } catch (e: Exception) {
            println("Error loading from localStorage: ${e.message}")
        }
    }

    private fun saveToStorage() {
        try {
            localStorage["lists_lists"] = json.encodeToString(_lists.value)
            localStorage["lists_items"] = json.encodeToString(_items.value)
            localStorage["lists_tombstones"] = json.encodeToString(_tombstones.value)
            localStorage["lists_rating_prefs"] = json.encodeToString(_ratingPrefs.value)
            if (_userEmail.value != null) {
                localStorage["lists_user_email"] = _userEmail.value!!
            } else {
                localStorage.removeItem("lists_user_email")
                localStorage.removeItem("listy_user_email")
            }
            if (_lastSyncTime.value != null) {
                localStorage["lists_last_sync"] = _lastSyncTime.value.toString()
            }
        } catch (e: Exception) {
            println("Error saving to localStorage: ${e.message}")
        }
    }

    private fun createDefaultLists() {
        val now = currentTimeMillis()
        val defaultLists = listOf(
            UserList(id = 1, uuid = generateUuid(), name = "Movies to Watch", category = "MOVIE", createdAt = now, updatedAt = now),
            UserList(id = 2, uuid = generateUuid(), name = "TV Shows", category = "TV_SHOW", createdAt = now, updatedAt = now),
            UserList(id = 3, uuid = generateUuid(), name = "Books", category = "BOOK", createdAt = now, updatedAt = now),
            UserList(id = 4, uuid = generateUuid(), name = "Video Games", category = "GAME", createdAt = now, updatedAt = now)
        )
        _lists.value = defaultLists
        saveToStorage()
    }

    fun addList(name: String, category: Category): UserList {
        val now = currentTimeMillis()
        val maxId = _lists.value.maxOfOrNull { it.id } ?: 0L
        val minOrder = _lists.value.minOfOrNull { it.customOrderIndex } ?: 0
        val newList = UserList(
            id = maxId + 1,
            uuid = generateUuid(),
            name = name.trim(),
            category = category.id,
            customOrderIndex = minOrder - 1,
            createdAt = now,
            updatedAt = now
        )
        _lists.value = _lists.value + newList
        saveToStorage()
        return newList
    }

    fun updateList(list: UserList) {
        val now = currentTimeMillis()
        val updated = list.copy(updatedAt = now)
        _lists.value = _lists.value.map { if (it.id == list.id || it.uuid == list.uuid) updated else it }
        saveToStorage()
    }

    fun deleteList(list: UserList) {
        val now = currentTimeMillis()
        _lists.value = _lists.value.filterNot { it.id == list.id || it.uuid == list.uuid }
        val deletedItems = _items.value.filter { it.listId == list.id || it.listUuid == list.uuid }
        _items.value = _items.value.filterNot { it.listId == list.id || it.listUuid == list.uuid }

        val newTombstones = _tombstones.value.toMutableList()
        newTombstones.add(Tombstone(entityUuid = list.uuid, entityType = "LIST", deletedAt = now))
        deletedItems.forEach { item ->
            newTombstones.add(Tombstone(entityUuid = item.uuid, entityType = "ITEM", deletedAt = now))
        }
        _tombstones.value = newTombstones
        try {
            localStorage.removeItem("view_mode_${list.id}")
            localStorage.removeItem("lists_view_prefs_view_mode_${list.id}")
        } catch (_: Exception) {}
        saveToStorage()
    }

    fun reorderLists(reorderedIds: List<Long>) {
        val map = reorderedIds.mapIndexed { idx, id -> id to idx }.toMap()
        _lists.value = _lists.value.map { list ->
            val order = map[list.id]
            if (order != null) list.copy(customOrderIndex = order) else list
        }
        saveToStorage()
    }

    fun addItem(item: ListItem) {
        val now = currentTimeMillis()
        val maxId = _items.value.maxOfOrNull { it.id } ?: 0L
        val newItem = item.copy(
            id = if (item.id == 0L) maxId + 1 else item.id,
            uuid = if (item.uuid.isEmpty()) generateUuid() else item.uuid,
            createdAt = if (item.createdAt == 0L) now else item.createdAt,
            updatedAt = now
        )
        _items.value = _items.value + newItem
        saveToStorage()
    }

    fun updateItem(item: ListItem) {
        val now = currentTimeMillis()
        val updated = item.copy(updatedAt = now)
        _items.value = _items.value.map { if (it.id == item.id || it.uuid == item.uuid) updated else it }
        saveToStorage()
    }

    fun deleteItem(item: ListItem) {
        val now = currentTimeMillis()
        _items.value = _items.value.filterNot { it.id == item.id || it.uuid == item.uuid }
        val newTombstones = _tombstones.value.toMutableList()
        newTombstones.add(Tombstone(entityUuid = item.uuid, entityType = "ITEM", deletedAt = now))
        _tombstones.value = newTombstones
        saveToStorage()
    }

    fun clearCompletedItems(listId: Long) {
        val now = currentTimeMillis()
        val toDelete = _items.value.filter {
            it.listId == listId && (it.marked || it.status == ItemStatus.COMPLETED)
        }
        if (toDelete.isEmpty()) return

        _items.value = _items.value.filterNot { item -> toDelete.any { it.id == item.id || it.uuid == item.uuid } }
        val newTombstones = _tombstones.value.toMutableList()
        toDelete.forEach { item ->
            newTombstones.add(Tombstone(entityUuid = item.uuid, entityType = "ITEM", deletedAt = now))
        }
        _tombstones.value = newTombstones
        saveToStorage()
    }

    fun createBackup(): BackupData {
        return BackupData(
            version = 1,
            exportedAt = currentTimeMillis(),
            lists = _lists.value,
            items = _items.value,
            tombstones = _tombstones.value
        )
    }

    fun exportBackupJson(): String {
        val now = currentTimeMillis()
        val backup = BackupData(
            version = 1,
            exportedAt = now,
            lists = _lists.value,
            items = _items.value,
            tombstones = _tombstones.value
        )
        return json.encodeToString(backup)
    }

    fun restoreBackupJson(jsonString: String): Boolean {
        return try {
            val backup = json.decodeFromString<BackupData>(jsonString)
            _lists.value = backup.lists
            _items.value = backup.items
            _tombstones.value = backup.tombstones
            saveToStorage()
            true
        } catch (e: Exception) {
            println("Restore failed: ${e.message}")
            false
        }
    }

    fun mergeFromRemote(remoteBackup: BackupData): BackupData {
        val now = currentTimeMillis()

        // 1. Merge tombstones
        val combinedTombstones = (_tombstones.value + remoteBackup.tombstones)
            .distinctBy { it.entityUuid }

        val deletedListUuids = combinedTombstones.filter { it.entityType == "LIST" }.map { it.entityUuid }.toSet()
        val deletedItemUuids = combinedTombstones.filter { it.entityType == "ITEM" }.map { it.entityUuid }.toSet()

        // 2. Reconcile Lists
        val localLists = _lists.value.filterNot { it.uuid in deletedListUuids }
        val remoteLists = remoteBackup.lists.filterNot { it.uuid in deletedListUuids }

        val mergedListsMap = mutableMapOf<String, UserList>()
        for (l in localLists) {
            val key = "${l.name.trim().lowercase()}_${l.category}"
            mergedListsMap[key] = l
        }
        for (rl in remoteLists) {
            val key = "${rl.name.trim().lowercase()}_${rl.category}"
            val existing = mergedListsMap[key]
            if (existing == null) {
                mergedListsMap[key] = rl
            } else {
                if (rl.updatedAt > existing.updatedAt) {
                    mergedListsMap[key] = rl.copy(id = existing.id)
                }
            }
        }
        val finalLists = mergedListsMap.values.toList()

        // 3. Reconcile Items
        val localItems = _items.value.filterNot { it.uuid in deletedItemUuids }
        val remoteItems = remoteBackup.items.filterNot { it.uuid in deletedItemUuids }

        val mergedItemsMap = mutableMapOf<String, ListItem>()
        for (item in localItems) {
            mergedItemsMap[item.uuid] = item
        }
        for (ri in remoteItems) {
            val existing = mergedItemsMap[ri.uuid]
            if (existing == null) {
                mergedItemsMap[ri.uuid] = ri
            } else {
                if (ri.updatedAt > existing.updatedAt) {
                    mergedItemsMap[ri.uuid] = ri.copy(id = existing.id)
                }
            }
        }
        val finalItems = mergedItemsMap.values.toList()

        _lists.value = finalLists
        _items.value = finalItems
        _tombstones.value = combinedTombstones
        _lastSyncTime.value = now
        saveToStorage()

        return BackupData(
            version = 1,
            exportedAt = now,
            lists = finalLists,
            items = finalItems,
            tombstones = combinedTombstones
        )
    }

    fun setAuth(email: String?) {
        _userEmail.value = email
        if (email != null) {
            localStorage["lists_user_email"] = email
        } else {
            localStorage.removeItem("lists_user_email")
            localStorage.removeItem("listy_user_email")
        }
    }

    fun setLastSync(time: Long) {
        _lastSyncTime.value = time
        localStorage["lists_last_sync"] = time.toString()
    }
}
