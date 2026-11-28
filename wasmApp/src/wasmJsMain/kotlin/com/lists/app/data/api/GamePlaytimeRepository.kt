package com.lists.app.data.api

import com.lists.app.encodeURIComponent
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*

data class GamePlaytime(val source: String, val sourceUrl: String, val main: Int, val extras: Int, val complete: Int)

sealed interface PlaytimeResult {
    data class Available(val times: GamePlaytime) : PlaytimeResult
    data object NotFound : PlaytimeResult
    data object Unavailable : PlaytimeResult
}

object GamePlaytimeRepository {
    private val client = HttpClient()
    private fun parse(raw: String): JsonObject = try { Json.parseToJsonElement(raw).jsonObject } catch (_: Exception) { JsonObject(emptyMap()) }
    private fun text(obj: JsonObject, key: String) = (obj[key] as? JsonPrimitive)?.contentOrNull ?: ""
    private fun seconds(obj: JsonObject, key: String) = ((obj[key] as? JsonPrimitive)?.intOrNull ?: 0).coerceIn(0, 99_999_999)
    private fun estimate(obj: JsonObject): GamePlaytime? {
        val source = text(obj, "source")
        val url = text(obj, "url")
        if (source != "igdb" && source != "howlongtobeat") return null
        if (source == "igdb" && !url.startsWith("https://www.igdb.com/games/") || source == "howlongtobeat" && !url.startsWith("https://howlongtobeat.com/game/")) return null
        val value = GamePlaytime(source, url, seconds(obj, "mainSeconds"), seconds(obj, "sidesSeconds"), seconds(obj, "completeSeconds"))
        return value.takeIf { it.main > 0 || it.extras > 0 || it.complete > 0 }
    }
    fun readSaved(raw: String, title: String, year: String): GamePlaytime? {
        val saved = parse(raw)["gamePlaytime"] as? JsonObject ?: return null
        return if (text(saved, "title") == title && text(saved, "year") == year) estimate(saved) else null
    }
    fun withSaved(raw: String, title: String, year: String, value: GamePlaytime): String {
        val fields = parse(compactMetadataSnapshot(raw)).toMutableMap()
        fields["gamePlaytime"] = buildJsonObject {
            put("title", title); put("year", year); put("source", value.source); put("url", value.sourceUrl)
            put("mainSeconds", value.main); put("sidesSeconds", value.extras); put("completeSeconds", value.complete)
        }
        return compactMetadataSnapshot(JsonObject(fields).toString())
    }
    fun mergeSaved(incoming: String, local: String, title: String, year: String): String {
        var compact = compactMetadataSnapshot(incoming)
        val fields = parse(compact).toMutableMap()
        val localLog = parse(local)["gameCompletionLog"] as? JsonArray
        if (fields["gameCompletionLog"] == null && localLog != null) {
            fields["gameCompletionLog"] = localLog
            compact = compactMetadataSnapshot(JsonObject(fields).toString())
        }
        if (readSaved(compact, title, year) != null) return compact
        return readSaved(local, title, year)?.let { withSaved(compact, title, year, it) } ?: compact
    }
    suspend fun lookup(title: String, year: String, metadata: String): PlaytimeResult {
        readSaved(metadata, title, year)?.let { return PlaytimeResult.Available(it) }
        if (title.isBlank()) return PlaytimeResult.NotFound
        val refs = parse(metadata)["sourceRefs"] as? JsonArray
        val id = refs?.mapNotNull { it as? JsonObject }?.firstOrNull { text(it, "source").equals("igdb", true) }
            ?.let { text(it, "id") }?.takeIf { it.matches(Regex("\\d{1,12}")) } ?: ""
        val y = year.takeIf { it.matches(Regex("(?:18|19|20)\\d{2}")) } ?: ""
        val url = "${SearchRepository.remoteEngineUrl.trimEnd('/')}/games/playtime?title=${encodeURIComponent(title)}" +
            (if (y.isNotEmpty()) "&year=$y" else "") + (if (id.isNotEmpty()) "&igdbId=$id" else "")
        return try {
            val response = withTimeoutOrNull(15000) { client.get(url) } ?: return PlaytimeResult.Unavailable
            if (response.status.value != 200) return PlaytimeResult.Unavailable
            val body = parse(response.bodyAsText())
            if (text(body, "status") == "not_found") return PlaytimeResult.NotFound
            (body["estimate"] as? JsonObject)?.let(::estimate)?.let { PlaytimeResult.Available(it) } ?: PlaytimeResult.Unavailable
        } catch (_: Exception) { PlaytimeResult.Unavailable }
    }
}
