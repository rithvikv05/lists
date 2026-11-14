package com.lists.app.data.api

import kotlinx.serialization.json.*

fun compactMetadataSnapshot(raw: String): String {
    if (raw.isBlank()) return ""
    return try {
        val root = Json.parseToJsonElement(raw).jsonObject
        val supplied = root["offlineSnapshot"] as? JsonObject
        if (supplied != null && supplied.toString().encodeToByteArray().size <= 2048) supplied.toString()
        else {
            val keys = setOf("schemaVersion", "id", "revision", "entityKind", "ratings", "isbn", "sourceRefs", "genres", "platforms", "totalChapters", "totalVolumes", "totalIssues", "runtimeMinutes")
            val fields = root.filterKeys { it in keys }.toMutableMap()
            root["canonicalId"]?.let { fields["id"] = it }
            for (key in listOf("genres", "platforms", "ratings", "sourceRefs")) {
                if (JsonObject(fields).toString().encodeToByteArray().size > 2048) fields.remove(key)
            }
            val result = JsonObject(fields).toString()
            if (result.encodeToByteArray().size <= 2048) result else ""
        }
    } catch (_: Exception) { "" }
}
