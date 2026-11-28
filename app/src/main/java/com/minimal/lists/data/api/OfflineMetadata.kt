package com.minimal.lists.data.api

import org.json.JSONObject

/** Search evidence is shared server data, not another offline copy per saved item. */
fun compactMetadataSnapshot(raw: String): String {
    if (raw.isBlank()) return ""
    return try {
        val root = JSONObject(raw)
        val supplied = root.optJSONObject("offlineSnapshot")
        if (supplied != null && supplied.toString().toByteArray(Charsets.UTF_8).size <= 2048) supplied.toString()
        else {
            val snapshot = JSONObject()
            for (key in listOf("schemaVersion", "id", "revision", "entityKind", "ratings", "isbn", "sourceRefs", "genres", "platforms", "totalChapters", "totalVolumes", "totalIssues", "runtimeMinutes", "gamePlaytime", "gameCompletionLog")) {
                if (root.has(key) && !root.isNull(key)) snapshot.put(key, root.get(key))
            }
            if (root.has("canonicalId")) snapshot.put("id", root.optString("canonicalId"))
            for (key in listOf("genres", "platforms", "ratings", "sourceRefs")) {
                if (snapshot.toString().toByteArray(Charsets.UTF_8).size > 2048) snapshot.remove(key)
            }
            if (snapshot.toString().toByteArray(Charsets.UTF_8).size <= 2048) snapshot.toString() else ""
        }
    } catch (_: Exception) { "" }
}
