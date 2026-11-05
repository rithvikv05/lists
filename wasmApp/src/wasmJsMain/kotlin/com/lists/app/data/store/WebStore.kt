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

    fun hasLocalChanges(): Boolean {
        val lastMutation = localStorage["lists_last_local_mutation"]?.toLongOrNull() ?: 0L
        val lastSync = _lastSyncTime.value ?: 0L
        if (lastSync == 0L && (_lists.value.isNotEmpty() || _items.value.isNotEmpty())) {
            return true
        }
        return lastMutation > lastSync
    }

    fun markLocalMutation() {
        val now = currentTimeMillis()
        localStorage["lists_last_local_mutation"] = now.toString()
    }

    fun markLocalChangesSynced() {
        val now = _lastSyncTime.value ?: currentTimeMillis()
        localStorage["lists_last_local_mutation"] = now.toString()
    }

    fun init() {
        val freshKey = "lists_empty_state_v1"
        if (localStorage[freshKey] == null) {
            localStorage[freshKey] = "true"
            // Ensure first-time opening is completely clean and empty
            val userEmail = localStorage["lists_user_email"]
            if (userEmail.isNullOrBlank()) {
                localStorage.removeItem("lists_lists")
                localStorage.removeItem("lists_items")
            }
        }
        loadFromStorage()
    }

    private fun loadFromStorage() {
        try {
            val listsJson = localStorage["lists_lists"]
            if (!listsJson.isNullOrEmpty()) {
                _lists.value = json.decodeFromString(listsJson)
            }

            val itemsJson = localStorage["lists_items"]
            if (!itemsJson.isNullOrEmpty()) {
                _items.value = json.decodeFromString(itemsJson)
            }

            val tombstonesJson = localStorage["lists_tombstones"]
            if (!tombstonesJson.isNullOrEmpty()) {
                _tombstones.value = json.decodeFromString(tombstonesJson)
            }

            val prefsJson = localStorage["lists_rating_prefs"]
            if (!prefsJson.isNullOrEmpty()) {
                _ratingPrefs.value = json.decodeFromString(prefsJson)
            }

            _userEmail.value = localStorage["lists_user_email"]
            val lastSync = localStorage["lists_last_sync"]?.toLongOrNull()
            _lastSyncTime.value = lastSync
        } catch (e: Exception) {
            println("Error loading from localStorage: ${e.message}")
        }
    }

    private fun saveToStorage(isUserMutation: Boolean = true) {
        try {
            if (isUserMutation) {
                markLocalMutation()
            }
            localStorage["lists_lists"] = json.encodeToString(_lists.value)
            localStorage["lists_items"] = json.encodeToString(_items.value)
            localStorage["lists_tombstones"] = json.encodeToString(_tombstones.value)
            localStorage["lists_rating_prefs"] = json.encodeToString(_ratingPrefs.value)
            if (_userEmail.value != null) {
                localStorage["lists_user_email"] = _userEmail.value!!
            } else {
                localStorage.removeItem("lists_user_email")
            }
            if (_lastSyncTime.value != null) {
                localStorage["lists_last_sync"] = _lastSyncTime.value.toString()
            }
        } catch (e: Exception) {
            println("Error saving to localStorage: ${e.message}")
        }
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

    fun updateLists(updated: List<UserList>) {
        val now = currentTimeMillis()
        val map = updated.associateBy { it.id }
        _lists.value = _lists.value.map {
            val u = map[it.id]
            if (u != null) u.copy(updatedAt = now) else it
        }
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

    fun deleteLists(lists: List<UserList>) {
        val now = currentTimeMillis()
        val ids = lists.map { it.id }.toSet()
        val uuids = lists.map { it.uuid }.filter { it.isNotBlank() }.toSet()
        _lists.value = _lists.value.filterNot { ids.contains(it.id) || uuids.contains(it.uuid) }
        val deletedItems = _items.value.filter { ids.contains(it.listId) || uuids.contains(it.listUuid) }
        _items.value = _items.value.filterNot { ids.contains(it.listId) || uuids.contains(it.listUuid) }

        val newTombstones = _tombstones.value.toMutableList()
        lists.forEach { list ->
            newTombstones.add(Tombstone(entityUuid = list.uuid, entityType = "LIST", deletedAt = now))
            try {
                localStorage.removeItem("view_mode_${list.id}")
                localStorage.removeItem("lists_view_prefs_view_mode_${list.id}")
            } catch (_: Exception) {}
        }
        deletedItems.forEach { item ->
            newTombstones.add(Tombstone(entityUuid = item.uuid, entityType = "ITEM", deletedAt = now))
        }
        _tombstones.value = newTombstones
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

    fun updateItems(updated: List<ListItem>) {
        val now = currentTimeMillis()
        val map = updated.associateBy { it.id }
        _items.value = _items.value.map {
            val u = map[it.id]
            if (u != null) u.copy(updatedAt = now) else it
        }
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

    fun deleteItems(items: List<ListItem>) {
        val now = currentTimeMillis()
        val ids = items.map { it.id }.toSet()
        val uuids = items.map { it.uuid }.filter { it.isNotBlank() }.toSet()
        _items.value = _items.value.filterNot { ids.contains(it.id) || uuids.contains(it.uuid) }
        val newTombstones = _tombstones.value.toMutableList()
        items.forEach { item ->
            newTombstones.add(Tombstone(entityUuid = item.uuid, entityType = "ITEM", deletedAt = now))
        }
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

    // ==================== BACKUP SAFETY NET ====================

    private const val LOCAL_BACKUP_KEY = "lists_local_backup_latest"
    private const val PRESYNC_BACKUP_KEY = "lists_presync_backup"

    /**
     * Save 1 local rolling snapshot with timestamp.
     */
    fun saveAutoBackup() {
        try {
            val backup = createBackup()
            if (backup.lists.isEmpty() && backup.items.isEmpty()) return

            val now = currentTimeMillis()
            val backupJson = json.encodeToString(backup)
            localStorage[LOCAL_BACKUP_KEY] = backupJson
            localStorage["${LOCAL_BACKUP_KEY}_timestamp"] = now.toString()
            localStorage["${LOCAL_BACKUP_KEY}_count"] = "${backup.lists.size} lists · ${backup.items.size} items"
            println("Local rolling backup saved (1 local backup: ${backup.lists.size} lists, ${backup.items.size} items)")
        } catch (e: Exception) {
            println("Auto-backup failed: ${e.message}")
        }
    }

    /**
     * Save a pre-sync snapshot — called right before mergeFromRemote.
     */
    fun savePreSyncBackup() {
        try {
            val backup = createBackup()
            if (backup.lists.isEmpty() && backup.items.isEmpty()) return

            val backupJson = json.encodeToString(backup)
            localStorage[PRESYNC_BACKUP_KEY] = backupJson
            localStorage["${PRESYNC_BACKUP_KEY}_timestamp"] = currentTimeMillis().toString()
            localStorage["${PRESYNC_BACKUP_KEY}_count"] = "${backup.lists.size} lists · ${backup.items.size} items"
            println("Pre-sync backup saved (${backup.lists.size} lists, ${backup.items.size} items)")
        } catch (e: Exception) {
            println("Pre-sync backup failed: ${e.message}")
        }
    }

    /**
     * Data class representing an available backup.
     */
    data class BackupInfo(
        val key: String,
        val label: String,
        val timestamp: Long,
        val summary: String,
        val isDrive: Boolean = false,
        val driveFileId: String? = null
    )

    /**
     * Get list of available backups for recovery (1 local snapshot + pre-sync if present).
     */
    fun getAvailableBackups(): List<BackupInfo> {
        val backups = mutableListOf<BackupInfo>()

        // 1 Local Rolling Backup
        val localJson = localStorage[LOCAL_BACKUP_KEY]
        if (!localJson.isNullOrEmpty()) {
            val ts = localStorage["${LOCAL_BACKUP_KEY}_timestamp"]?.toLongOrNull() ?: 0L
            val count = localStorage["${LOCAL_BACKUP_KEY}_count"] ?: "?"
            backups.add(BackupInfo(
                key = LOCAL_BACKUP_KEY,
                label = "Local Backup (1/1)",
                timestamp = ts,
                summary = count
            ))
        }

        // Pre-sync safety snapshot
        val presyncJson = localStorage[PRESYNC_BACKUP_KEY]
        if (!presyncJson.isNullOrEmpty()) {
            val ts = localStorage["${PRESYNC_BACKUP_KEY}_timestamp"]?.toLongOrNull() ?: 0L
            val count = localStorage["${PRESYNC_BACKUP_KEY}_count"] ?: "?"
            backups.add(BackupInfo(
                key = PRESYNC_BACKUP_KEY,
                label = "Pre-sync safety snapshot",
                timestamp = ts,
                summary = count
            ))
        }

        return backups.sortedByDescending { it.timestamp }
    }

    /**
     * Restore from a specific backup by key.
     */
    fun restoreFromBackup(key: String): Boolean {
        return try {
            val backupJson = localStorage[key] ?: return false
            val backup = json.decodeFromString<BackupData>(backupJson)
            if (backup.lists.isEmpty() && backup.items.isEmpty()) {
                println("Restore skipped: backup is empty")
                return false
            }
            // Save current state as a safety backup before restoring
            saveAutoBackup()

            _lists.value = backup.lists
            _items.value = backup.items
            _tombstones.value = backup.tombstones
            saveToStorage()
            println("Restored from $key (${backup.lists.size} lists, ${backup.items.size} items)")
            true
        } catch (e: Exception) {
            println("Restore from backup failed: ${e.message}")
            false
        }
    }

    // ==================== MERGE ====================

    fun mergeFromRemote(remoteBackup: BackupData): BackupData {
        // Safety: save pre-sync backup before any merge
        savePreSyncBackup()

        val now = currentTimeMillis()

        // 1. Process tombstones with timestamps
        val tombstoneMap = mutableMapOf<String, Long>()
        for (t in _tombstones.value) {
            tombstoneMap[t.entityUuid] = maxOf(tombstoneMap[t.entityUuid] ?: 0L, t.deletedAt)
        }
        for (t in remoteBackup.tombstones) {
            tombstoneMap[t.entityUuid] = maxOf(tombstoneMap[t.entityUuid] ?: 0L, t.deletedAt)
        }

        // 2. Reconcile Lists: only delete if tombstone deletedAt >= list.updatedAt
        val localLists = _lists.value.filterNot { list ->
            val deletedAt = tombstoneMap[list.uuid]
            deletedAt != null && deletedAt >= list.updatedAt
        }
        val remoteLists = remoteBackup.lists.filterNot { list ->
            val deletedAt = tombstoneMap[list.uuid]
            deletedAt != null && deletedAt >= list.updatedAt
        }

        val mergedListsMap = mutableMapOf<String, UserList>()
        val uuidToLocalList = mutableMapOf<String, UserList>()
        val remoteIdToLocalList = mutableMapOf<Long, UserList>()

        for (local in localLists) {
            mergedListsMap[local.uuid] = local
            uuidToLocalList[local.uuid] = local
        }

        for (remote in remoteLists) {
            val existingByUuid = mergedListsMap[remote.uuid]
            if (existingByUuid != null) {
                if (remote.updatedAt > existingByUuid.updatedAt) {
                    val updated = remote.copy(id = existingByUuid.id)
                    mergedListsMap[remote.uuid] = updated
                    uuidToLocalList[remote.uuid] = updated
                }
            } else {
                val existingByNameCat = mergedListsMap.values.firstOrNull {
                    it.category == remote.category && it.name.trim().equals(remote.name.trim(), ignoreCase = true)
                }
                if (existingByNameCat != null) {
                    val adopted = existingByNameCat.copy(
                        uuid = remote.uuid,
                        updatedAt = maxOf(existingByNameCat.updatedAt, remote.updatedAt)
                    )
                    mergedListsMap.remove(existingByNameCat.uuid)
                    mergedListsMap[remote.uuid] = adopted
                    uuidToLocalList[remote.uuid] = adopted
                    uuidToLocalList[existingByNameCat.uuid] = adopted
                    remoteIdToLocalList[remote.id] = adopted
                } else {
                    mergedListsMap[remote.uuid] = remote
                    uuidToLocalList[remote.uuid] = remote
                    remoteIdToLocalList[remote.id] = remote
                }
            }
        }
        // Guarantee every list has a unique, positive ID for Compose keys and navigation
        val assignedListMap = mutableMapOf<String, Long>()
        var nextListId = 1L
        val finalLists = mergedListsMap.values.map { list ->
            val assignedId = nextListId++
            assignedListMap[list.uuid] = assignedId
            list.copy(id = assignedId)
        }

        // 3. Reconcile Items: only delete if tombstone deletedAt >= item.updatedAt
        val localItems = _items.value.filterNot { item ->
            val deletedAt = tombstoneMap[item.uuid]
            deletedAt != null && deletedAt >= item.updatedAt
        }
        val remoteItems = remoteBackup.items.filterNot { item ->
            val deletedAt = tombstoneMap[item.uuid]
            deletedAt != null && deletedAt >= item.updatedAt
        }

        val mergedItemsMap = mutableMapOf<String, ListItem>()
        for (item in localItems) {
            val assignedListId = assignedListMap[item.listUuid] ?: item.listId
            mergedItemsMap[item.uuid] = item.copy(listId = assignedListId)
        }
        for (ri in remoteItems) {
            val targetList = uuidToLocalList[ri.listUuid]
                ?: remoteIdToLocalList[ri.listId]
                ?: finalLists.firstOrNull { it.id == ri.listId }
                ?: finalLists.firstOrNull { it.category == ri.category }
                ?: finalLists.firstOrNull()

            val assignedListId = targetList?.let { assignedListMap[it.uuid] ?: it.id } ?: ri.listId
            val remappedListUuid = targetList?.uuid ?: ri.listUuid
            val remapped = ri.copy(listId = assignedListId, listUuid = remappedListUuid)

            val existing = mergedItemsMap[ri.uuid]
            if (existing == null) {
                mergedItemsMap[ri.uuid] = remapped
            } else {
                if (ri.updatedAt > existing.updatedAt) {
                    mergedItemsMap[ri.uuid] = remapped.copy(id = existing.id)
                }
            }
        }

        // Guarantee every item has a unique, positive ID for Compose keys and navigation
        var nextItemId = 1L
        val finalItems = mergedItemsMap.values.map { item ->
            item.copy(id = nextItemId++)
        }

        val combinedTombstones = tombstoneMap.map { (uuid, ts) ->
            val existing = _tombstones.value.firstOrNull { it.entityUuid == uuid }
                ?: remoteBackup.tombstones.firstOrNull { it.entityUuid == uuid }
            Tombstone(
                entityUuid = uuid,
                entityType = existing?.entityType ?: "ITEM",
                deletedAt = ts
            )
        }

        _lists.value = finalLists
        _items.value = finalItems
        _tombstones.value = combinedTombstones
        _lastSyncTime.value = now
        saveToStorage()

        // Save an auto-backup of the successful merge result
        saveAutoBackup()

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
        }
    }

    fun setLastSync(time: Long) {
        _lastSyncTime.value = time
        localStorage["lists_last_sync"] = time.toString()
    }
}
