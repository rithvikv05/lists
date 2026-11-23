package com.minimal.lists.data.api

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class GamePlaytime(
    val source: String,
    val sourceUrl: String,
    val main: Int,
    val extras: Int,
    val complete: Int
)

sealed interface PlaytimeResult {
    data class Available(val times: GamePlaytime, val cached: Boolean = false) : PlaytimeResult
    data object NotFound : PlaytimeResult
    data object Unavailable : PlaytimeResult
}

/** Fetches optional estimates on demand; only small, bounded successful results are cached. */
object GamePlaytimeRepository {
    private val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS).build()

    suspend fun lookup(context: Context, title: String, year: String, metadata: String): PlaytimeResult = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext PlaytimeResult.NotFound
        readSaved(metadata, title, year)?.let { return@withContext PlaytimeResult.Available(it, cached = true) }
        val igdbId = extractIgdbId(metadata)
        val key = MessageDigest.getInstance("SHA-256").digest("$title|$year|$igdbId".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val prefs = context.applicationContext.getSharedPreferences("game_playtime_cache_v2", Context.MODE_PRIVATE)
        val cached = try { JSONObject(prefs.getString(key, "") ?: "") } catch (_: Exception) { null }
        val old = cached?.let(::parseEstimate)
        if (old != null) {
            return@withContext PlaytimeResult.Available(old, cached = true)
        }
        try {
            val builder = "${SearchRepository.remoteEngineUrl.trimEnd('/')}/games/playtime".toHttpUrl().newBuilder()
                .addQueryParameter("title", title)
            if (year.matches(Regex("(?:18|19|20)\\d{2}"))) builder.addQueryParameter("year", year)
            if (igdbId.isNotBlank()) builder.addQueryParameter("igdbId", igdbId)
            val request = Request.Builder().url(builder.build()).build()
            val payload = client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
            if (payload == null) return@withContext old?.let { PlaytimeResult.Available(it, cached = true) } ?: PlaytimeResult.Unavailable
            val body = JSONObject(payload)
            if (body.optString("status") == "not_found") return@withContext PlaytimeResult.NotFound
            val estimate = body.optJSONObject("estimate")?.let(::parseEstimate)
                ?: return@withContext old?.let { PlaytimeResult.Available(it, cached = true) } ?: PlaytimeResult.Unavailable
            val record = JSONObject().put("source", estimate.source).put("url", estimate.sourceUrl)
                .put("mainSeconds", estimate.main).put("sidesSeconds", estimate.extras).put("completeSeconds", estimate.complete)
                .put("savedAt", System.currentTimeMillis())
            val edit = prefs.edit()
            if (prefs.all.size >= 100 && !prefs.contains(key)) {
                prefs.all.entries.minByOrNull { entry ->
                    try { JSONObject(entry.value as? String ?: "").optLong("savedAt") } catch (_: Exception) { 0L }
                }?.let { edit.remove(it.key) }
            }
            edit.putString(key, record.toString()).apply()
            PlaytimeResult.Available(estimate)
        } catch (_: Exception) {
            old?.let { PlaytimeResult.Available(it, cached = true) } ?: PlaytimeResult.Unavailable
        }
    }

    fun readSaved(metadata: String, title: String, year: String): GamePlaytime? {
        return try {
            val saved = JSONObject(metadata).optJSONObject("gamePlaytime") ?: return null
            if (saved.optString("title") != title || saved.optString("year") != year) null else parseEstimate(saved)
        } catch (_: Exception) { null }
    }

    fun withSaved(metadata: String, title: String, year: String, estimate: GamePlaytime): String {
        val root = try { JSONObject(compactMetadataSnapshot(metadata)) } catch (_: Exception) { JSONObject() }
        root.put("gamePlaytime", JSONObject().put("title", title).put("year", year)
            .put("source", estimate.source).put("url", estimate.sourceUrl)
            .put("mainSeconds", estimate.main).put("sidesSeconds", estimate.extras).put("completeSeconds", estimate.complete))
        return compactMetadataSnapshot(root.toString())
    }

    fun mergeSaved(incoming: String, local: String, title: String, year: String): String {
        val compact = compactMetadataSnapshot(incoming)
        if (readSaved(compact, title, year) != null) return compact
        val saved = readSaved(local, title, year) ?: return compact
        return withSaved(compact, title, year, saved)
    }

    private fun extractIgdbId(metadata: String): String = try {
        val refs = JSONObject(metadata).optJSONArray("sourceRefs")
        (0 until (refs?.length() ?: 0)).firstNotNullOfOrNull { i ->
            val ref = refs?.optJSONObject(i)
            ref?.optString("id")?.takeIf { ref.optString("source").equals("igdb", true) && it.matches(Regex("\\d{1,12}")) }
        } ?: ""
    } catch (_: Exception) { "" }

    private fun parseEstimate(obj: JSONObject): GamePlaytime? {
        val source = obj.optString("source")
        val url = obj.optString("url")
        val validUrl = try {
            val parsed = url.toHttpUrl()
            parsed.isHttps && when (source) {
                "howlongtobeat" -> parsed.host == "howlongtobeat.com"
                "igdb" -> parsed.host == "www.igdb.com"
                else -> false
            }
        } catch (_: Exception) { false }
        if (!validUrl) return null
        fun seconds(key: String) = obj.optInt(key).coerceIn(0, 99_999_999)
        val result = GamePlaytime(source, url, seconds("mainSeconds"), seconds("sidesSeconds"), seconds("completeSeconds"))
        return result.takeIf { listOf(it.main, it.extras, it.complete).any { duration -> duration > 0 } }
    }
}
