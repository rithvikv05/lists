package com.lists.app.data.store

import com.lists.app.data.model.*
import kotlinx.browser.localStorage
import kotlinx.browser.window
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
        loadFromStorage()
        if (_lists.value.isEmpty()) {
            createDefaultLists()
        }
    }

    private fun loadFromStorage() {
        try {
            val listsJson = localStorage["listy_lists"]
            if (!listsJson.isNullOrEmpty()) {
                _lists.value = json.decodeFromString(listsJson)
            }

            val itemsJson = localStorage["listy_items"]
            if (!itemsJson.isNullOrEmpty()) {
                _items.value = json.decodeFromString(itemsJson)
            }

            val tombstonesJson = localStorage["listy_tombstones"]
            if (!tombstonesJson.isNullOrEmpty()) {
                _tombstones.value = json.decodeFromString(tombstonesJson)
            }

            val prefsJson = localStorage["listy_rating_prefs"]
            if (!prefsJson.isNullOrEmpty()) {
                _ratingPrefs.value = json.decodeFromString(prefsJson)
            }

            _userEmail.value = localStorage["listy_user_email"]
            val lastSync = localStorage["listy_last_sync"]?.toLongOrNull()
            _lastSyncTime.value = lastSync
        } catch (e: Exception) {
            println("Error loading from localStorage: ${e.message}")
        }
    }

    private fun saveToStorage() {
        try {
            localStorage["listy_lists"] = json.encodeToString(_lists.value)
            localStorage["listy_items"] = json.encodeToString(_items.value)
            localStorage["listy_tombstones"] = json.encodeToString(_tombstones.value)
            localStorage["listy_rating_prefs"] = json.encodeToString(_ratingPrefs.value)
            if (_userEmail.value != null) {
                localStorage["listy_user_email"] = _userEmail.value!!
            } else {
                localStorage.removeItem("listy_user_email")
            }
            if (_lastSyncTime.value != null) {
                localStorage["listy_last_sync"] = _lastSyncTime.value.toString()
            }
        } catch (e: Exception) {
            println("Error saving to localStorage: ${e.message}")
        }
    }

    private fun createDefaultLists() {
        val now = (window.performance.now() + 1700000000000.0).toLong()
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
        val now = (window.performance.now() + 1700000000000.0).toLong()
        val maxId = _lists.value.maxOfOrNull { it.id } ?: 0L
        val newList = UserList(
            id = maxId + 1,
            uuid = generateUuid(),
            name = name.trim(),
            category = category.id,
            createdAt = now,
            updatedAt = now
        )
        _lists.value = _lists.value + newList
        saveToStorage()
        return newList
    }

    fun updateList(list: UserList) {
        val now = (window.performance.now() + 1700000000000.0).toLong()
        val updated = list.copy(updatedAt = now)
        _lists.value = _lists.value.map { if (it.id == list.id || it.uuid == list.uuid) updated else it }
        saveToStorage()
    }

    fun deleteList(list: UserList) {
        val now = (window.performance.now() + 1700000000000.0).toLong()
        _lists.value = _lists.value.filterNot { it.id == list.id || it.uuid == list.uuid }
        // Also delete items in this list
        val deletedItems = _items.value.filter { it.listId == list.id || it.listUuid == list.uuid }
        _items.value = _items.value.filterNot { it.listId == list.id || it.listUuid == list.uuid }

        val newTombstones = _tombstones.value.toMutableList()
        newTombstones.add(Tombstone(entityUuid = list.uuid, entityType = "LIST", deletedAt = now))
        deletedItems.forEach { item ->
            newTombstones.add(Tombstone(entityUuid = item.uuid, entityType = "ITEM", deletedAt = now))
        }
        _tombstones.value = newTombstones
        saveToStorage()
    }

    fun addItem(item: ListItem) {
        val now = (window.performance.now() + 1700000000000.0).toLong()
        val maxId = _items.value.maxOfOrNull { it.id } ?: 0L
        val newItem = item.copy(
            id = maxId + 1,
            uuid = if (item.uuid.isEmpty()) generateUuid() else item.uuid,
            createdAt = if (item.createdAt == 0L) now else item.createdAt,
            updatedAt = now
        )
        _items.value = _items.value + newItem
        saveToStorage()
    }

    fun updateItem(item: ListItem) {
        val now = (window.performance.now() + 1700000000000.0).toLong()
        val updated = item.copy(updatedAt = now)
        _items.value = _items.value.map { if (it.id == item.id || it.uuid == item.uuid) updated else it }
        saveToStorage()
    }

    fun deleteItem(item: ListItem) {
        val now = (window.performance.now() + 1700000000000.0).toLong()
        _items.value = _items.value.filterNot { it.id == item.id || it.uuid == item.uuid }
        val newTombstones = _tombstones.value.toMutableList()
        newTombstones.add(Tombstone(entityUuid = item.uuid, entityType = "ITEM", deletedAt = now))
        _tombstones.value = newTombstones
        saveToStorage()
    }

    fun clearCompletedItems(listId: Long, listUuid: String) {
        val now = (window.performance.now() + 1700000000000.0).toLong()
        val toDelete = _items.value.filter {
            (it.listId == listId || (listUuid.isNotEmpty() && it.listUuid == listUuid)) && (it.marked || it.status == ItemStatus.COMPLETED)
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

    fun setRatingScale(category: Category, scale: RatingScaleOption) {
        val newPrefs = _ratingPrefs.value.toMutableMap()
        newPrefs["rating_scale_${category.name}"] = scale.id
        _ratingPrefs.value = newPrefs
        saveToStorage()
    }

    fun getRatingScale(category: Category): RatingScaleOption {
        val id = _ratingPrefs.value["rating_scale_${category.name}"]
        return RatingScaleOption.fromId(id, RatingScaleOption.getDefault(category))
    }

    fun exportBackupJson(): String {
        val now = (window.performance.now() + 1700000000000.0).toLong()
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
        val now = (window.performance.now() + 1700000000000.0).toLong()

        // 1. Merge tombstones
        val combinedTombstones = (_tombstones.value + remoteBackup.tombstones)
            .distinctBy { it.entityUuid }

        val deletedListUuids = combinedTombstones.filter { it.entityType == "LIST" }.map { it.entityUuid }.toSet()
        val deletedItemUuids = combinedTombstones.filter { it.entityType == "ITEM" }.map { it.entityUuid }.toSet()

        // 2. Reconcile Lists
        val localLists = _lists.value.filterNot { it.uuid in deletedListUuids }
        val remoteLists = remoteBackup.lists.filterNot { it.uuid in deletedListUuids }

        val mergedListsMap = mutableMapOf<String, UserList>() // keyed by normalized name + category
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

        val mergedItemsMap = mutableMapOf<String, ListItem>() // keyed by uuid
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
            localStorage["listy_user_email"] = email
        } else {
            localStorage.removeItem("listy_user_email")
        }
    }
}
