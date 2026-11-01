package com.lists.app.data.sync

import com.lists.app.WebContext
import com.lists.app.currentTimeMillis
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TombstoneManager(context: Any? = null) {
    companion object {
        private const val PREFS_NAME = "lists_tombstones"
        private const val LEGACY_PREFS_NAME = "listy_tombstones"
        private const val KEY_DELETED_LISTS = "deleted_lists_json"
        private const val KEY_DELETED_ITEMS = "deleted_items_json"
    }

    private val prefs = run {
        val webCtx = (context as? WebContext ?: com.lists.app.WebContext())
        val newPrefs = webCtx.getSharedPreferences(PREFS_NAME, 0)
        val legacyPrefs = webCtx.getSharedPreferences(LEGACY_PREFS_NAME, 0)
        if (newPrefs.getString(KEY_DELETED_LISTS, null) == null && legacyPrefs.getString(KEY_DELETED_LISTS, null) != null) {
            legacyPrefs.getString(KEY_DELETED_LISTS, null)?.let { newPrefs.putString(KEY_DELETED_LISTS, it) }
            legacyPrefs.getString(KEY_DELETED_ITEMS, null)?.let { newPrefs.putString(KEY_DELETED_ITEMS, it) }
        }
        newPrefs
    }
    private val json = Json { ignoreUnknownKeys = true }

    fun recordDeletedList(uuid: String, timestamp: Long = currentTimeMillis()) {
        if (uuid.isBlank()) return
        val map = getDeletedLists().toMutableMap()
        map[uuid] = timestamp
        saveMap(KEY_DELETED_LISTS, map)
    }

    fun recordDeletedItem(uuid: String, timestamp: Long = currentTimeMillis()) {
        if (uuid.isBlank()) return
        val map = getDeletedItems().toMutableMap()
        map[uuid] = timestamp
        saveMap(KEY_DELETED_ITEMS, map)
    }

    fun getDeletedLists(): Map<String, Long> = loadMap(KEY_DELETED_LISTS)
    fun getDeletedItems(): Map<String, Long> = loadMap(KEY_DELETED_ITEMS)

    fun mergeRemoteTombstones(remoteLists: Map<String, Long>, remoteItems: Map<String, Long>) {
        val localLists = getDeletedLists().toMutableMap()
        remoteLists.forEach { (uuid, ts) ->
            val existing = localLists[uuid] ?: 0L
            if (ts > existing) localLists[uuid] = ts
        }
        saveMap(KEY_DELETED_LISTS, localLists)

        val localItems = getDeletedItems().toMutableMap()
        remoteItems.forEach { (uuid, ts) ->
            val existing = localItems[uuid] ?: 0L
            if (ts > existing) localItems[uuid] = ts
        }
        saveMap(KEY_DELETED_ITEMS, localItems)
    }

    fun isListDeleted(uuid: String, updatedAt: Long): Boolean {
        val deletedAt = getDeletedLists()[uuid] ?: return false
        return deletedAt >= updatedAt
    }

    fun isItemDeleted(uuid: String, updatedAt: Long): Boolean {
        val deletedAt = getDeletedItems()[uuid] ?: return false
        return deletedAt >= updatedAt
    }

    private fun saveMap(key: String, map: Map<String, Long>) {
        try {
            prefs.putString(key, json.encodeToString(map))
        } catch (_: Exception) {}
    }

    private fun loadMap(key: String): Map<String, Long> {
        val raw = prefs.getString(key, null) ?: return emptyMap()
        return try {
            json.decodeFromString<Map<String, Long>>(raw)
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
