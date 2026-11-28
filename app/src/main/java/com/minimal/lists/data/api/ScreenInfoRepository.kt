package com.minimal.lists.data.api

import android.util.LruCache
import com.minimal.lists.data.db.ListItem
import com.minimal.lists.data.model.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.TimeUnit

internal fun String.asReleaseDate(): LocalDate? = runCatching { LocalDate.parse(take(10)) }.getOrNull()

internal fun JSONObject.stringList(name: String): List<String> {
    val array = optJSONArray(name) ?: return emptyList()
    return (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }
}

internal fun ListItem.metadataObject(): JSONObject =
    runCatching { JSONObject(metadataJson) }.getOrElse { JSONObject() }

internal fun ListItem.cachedEpisodes(): List<EpisodeItem> {
    val data = runCatching { JSONArray(episodesDataJson) }.getOrNull() ?: return emptyList()
    return (0 until data.length()).mapNotNull {
        val episode = data.optJSONObject(it) ?: return@mapNotNull null
        EpisodeItem(episode.optInt("season"), episode.optInt("number"), episode.optString("name"),
            episode.optString("airdate"), episode.optString("airstamp"))
    }
}

internal object ScreenInfoRepository {
    private val cache = LruCache<String, Pair<Long, JSONObject>>(200)
    private val client = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build()

    suspend fun get(item: ListItem): JSONObject? = withContext(Dispatchers.IO) {
        val kind = when (item.category) {
            Category.MOVIE.id -> "movie"
            Category.TV_SHOW.id -> "tv"
            else -> return@withContext null
        }
        val endpoint = "${SearchRepository.remoteEngineUrl.trimEnd('/')}/screen-info"
            .toHttpUrlOrNull()?.newBuilder() ?: return@withContext null
        val sources = item.metadataObject().optJSONArray("sourceRefs")
        val id = (0 until (sources?.length() ?: 0)).firstNotNullOfOrNull { index ->
            val ref = sources?.optJSONObject(index) ?: return@firstNotNullOfOrNull null
            ref.optString("id").takeIf { ref.optString("source") == "tmdb" && it.startsWith("$kind:") }
                ?.substringAfter(':')?.takeIf { it.matches(Regex("\\d{1,12}")) }
        }
        val country = Locale.getDefault().country.takeIf { it.matches(Regex("[A-Z]{2}")) } ?: "IN"
        endpoint.addQueryParameter("kind", kind).addQueryParameter("country", country)
        if (id != null) endpoint.addQueryParameter("id", id)
        else {
            endpoint.addQueryParameter("title", item.title.take(200))
            item.releaseYear.takeIf { it.matches(Regex("\\d{4}")) }?.let { endpoint.addQueryParameter("year", it) }
        }
        val url = endpoint.build()
        val key = url.toString()
        cache.get(key)?.takeIf { System.currentTimeMillis() - it.first < TimeUnit.HOURS.toMillis(6) }
            ?.let { return@withContext it.second }
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                JSONObject(body).also { cache.put(key, System.currentTimeMillis() to it) }
            }
        } catch (_: java.io.IOException) { null }
        catch (_: org.json.JSONException) { null }
    }
}
