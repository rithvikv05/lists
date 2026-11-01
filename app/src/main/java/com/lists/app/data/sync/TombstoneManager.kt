package com.lists.app.data.sync

import android.content.Context
import org.json.JSONObject

/**
 * Tracks tombstones (deleted list and item UUIDs with timestamps)
 * to ensure deletions propagate reliably across all devices and prevent
 * resurrection during sync.
 */
class TombstoneManager(context: Context) {
    companion object {
        private const val PREFS_NAME = "listy_tombstones"
        private const val KEY_DELETED_LISTS = "deleted_lists_json"
        private const val KEY_DELETED_ITEMS = "deleted_items_json"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recordDeletedList(uuid: String, timestamp: Long = System.currentTimeMillis()) {
        if (uuid.isBlank()) return
        val map = getDeletedLists().toMutableMap()
        map[uuid] = timestamp
        saveMap(KEY_DELETED_LISTS, map)
    }

    fun recordDeletedItem(uuid: String, timestamp: Long = System.currentTimeMillis()) {
        if (uuid.isBlank()) return
        val map = getDeletedItems().toMutableMap()
        map[uuid] = timestamp
        saveMap(KEY_DELETED_ITEMS, map)
    }

    fun getDeletedLists(): Map<String, Long> {
        return loadMap(KEY_DELETED_LISTS)
    }

    fun getDeletedItems(): Map<String, Long> {
        return loadMap(KEY_DELETED_ITEMS)
    }

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

    private fun loadMap(key: String): Map<String, Long> {
        val jsonStr = prefs.getString(key, null) ?: return emptyMap()
        return try {
            val json = JSONObject(jsonStr)
            val result = mutableMapOf<String, Long>()
            json.keys().forEach { k ->
                result[k] = json.optLong(k)
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun saveMap(key: String, map: Map<String, Long>) {
        val json = JSONObject()
        map.forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString(key, json.toString()).apply()
    }
}
