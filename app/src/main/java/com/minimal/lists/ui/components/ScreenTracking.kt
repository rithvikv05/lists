package com.minimal.lists.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.minimal.lists.data.api.ScreenInfoRepository
import com.minimal.lists.data.api.asReleaseDate
import com.minimal.lists.data.api.cachedEpisodes
import com.minimal.lists.data.api.metadataObject
import com.minimal.lists.data.api.stringList
import com.minimal.lists.data.db.ListItem
import com.minimal.lists.data.model.Category
import com.minimal.lists.data.storage.OfflineStorageManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal const val RELEASE_CALENDAR_PREF = "show_release_calendar"

private fun LocalDate.label(): String = format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScreenInformation(item: ListItem, category: Category) {
    val stored = remember(item.metadataJson) { item.metadataObject() }
    val info by produceState<JSONObject?>(null, item.title, item.releaseYear, item.metadataId, category) {
        value = null
        value = ScreenInfoRepository.get(item.copy(category = category.id))
    }
    val country = info?.optString("country").orEmpty()
    val certification = listOf(
        info?.optString("certification").orEmpty() to country,
        info?.optString("indiaCertification").orEmpty() to "IN"
    ).filter { it.first.isNotBlank() }.joinToString(" · ") { (rating, region) -> "$rating ($region)" }
    val release = info?.optString("releaseDate")?.takeIf { it.isNotBlank() } ?: item.releaseDate
    val status = info?.optString("status")?.takeIf { it.isNotBlank() } ?: stored.optString("status")
    val runtime = info?.optInt("runtimeMinutes")?.takeIf { it > 0 } ?: stored.optInt("runtimeMinutes")
    val genres = (info?.stringList("genres").orEmpty() + stored.stringList("genres")).distinctBy { it.lowercase() }
    val lines = listOf(
        "Status" to status,
        (if (category == Category.MOVIE) "Release" else "First aired") to
            (release.asReleaseDate()?.label() ?: item.releaseYear),
        "Age rating" to certification,
        "Runtime" to if (runtime > 0) "$runtime min" else "",
        "Network" to info?.stringList("networks").orEmpty().joinToString(", "),
        "Seasons" to info?.optInt("seasons")?.takeIf { it > 0 }?.toString().orEmpty()
    ).filter { it.second.isNotBlank() }
    if (lines.isEmpty() && genres.isEmpty()) return
    Spacer(Modifier.height(16.dp))
    Surface(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            lines.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium)
                    Text(value, Modifier.weight(1.5f), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (genres.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                genres.forEach { genre ->
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Text(genre, Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
internal fun FullScreenCover(item: ListItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val model = remember(item.uuid, item.coverUrl) {
        OfflineStorageManager.getInstance(context).getDisplayCover(item.uuid, item.coverUrl)
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(model = model, contentDescription = item.title, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        val limitX = size.width * (scale - 1f) / 2f
                        val limitY = size.height * (scale - 1f) / 2f
                        offset = Offset((offset.x + pan.x).coerceIn(-limitX, limitX),
                            (offset.y + pan.y).coerceIn(-limitY, limitY))
                    }
                }.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                })
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                Icon(Icons.Default.Close, "Close cover", tint = Color.White)
            }
        }
    }
}

private data class ReleaseEvent(val date: LocalDate, val title: String, val episode: String = "")
private data class ReleaseLookup(val events: List<ReleaseEvent>, val incomplete: Boolean)

private suspend fun releases(item: ListItem, today: LocalDate): ReleaseLookup {
    val info = ScreenInfoRepository.get(item)
    if (item.category == Category.MOVIE.id) {
        val date = info?.optString("regionalReleaseDate")?.asReleaseDate()
            ?: info?.optString("releaseDate")?.asReleaseDate() ?: item.releaseDate.asReleaseDate()
        return ReleaseLookup(listOfNotNull(date?.takeUnless { it.isBefore(today) }?.let { ReleaseEvent(it, item.title) }),
            info == null && date == null)
    }
    val dates = mutableMapOf<Pair<Int, Int>, ReleaseEvent>()
    item.cachedEpisodes().forEach { episode ->
        episode.airdate.asReleaseDate()?.let { date ->
            dates[episode.season to episode.number] = ReleaseEvent(date, item.title, episode.key + " · " + episode.name)
        }
    }
    info?.optJSONObject("nextEpisode")?.let { episode ->
        val season = episode.optInt("season")
        val number = episode.optInt("number")
        episode.optString("airdate").asReleaseDate()?.let { date ->
            dates[season to number] = ReleaseEvent(date, item.title, "S${season}E${number} · ${episode.optString("name")}".trimEnd(' ', '·'))
        }
    }
    return ReleaseLookup(dates.values.filter { !it.date.isBefore(today) }, info == null)
}

@Composable
internal fun ReleaseCalendarButton(items: List<ListItem>, category: Category?) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("lists_settings_prefs", 0) }
    var enabled by remember(preferences) { mutableStateOf(preferences.getBoolean(RELEASE_CALENDAR_PREF, true)) }
    DisposableEffect(preferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == RELEASE_CALENDAR_PREF) enabled = prefs.getBoolean(RELEASE_CALENDAR_PREF, true)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    if (!enabled || (category != Category.MOVIE && category != Category.TV_SHOW)) return
    var opened by remember(category) { mutableStateOf(false) }
    OutlinedButton(onClick = { opened = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Upcoming releases")
    }
    if (opened) {
        val saved = remember(items, category) { items.filter { it.category == category.id }.distinctBy {
            if (it.metadataId.isNotBlank()) it.metadataId else it.title.lowercase() + "|" + it.releaseYear
        } }
        val result by produceState<ReleaseLookup?>(null, saved) {
            value = null
            val today = LocalDate.now()
            val lookups = mutableListOf<ReleaseLookup>()
            for (batch in saved.chunked(4)) {
                lookups += batch.map { async { releases(it, today) } }.awaitAll()
                val events = lookups.flatMap { it.events }.distinct().sortedWith(compareBy({ it.date }, { it.title }))
                if (events.isNotEmpty() || lookups.size == saved.size) {
                    value = ReleaseLookup(events, lookups.size < saved.size || lookups.any { it.incomplete })
                }
            }
            if (saved.isEmpty()) value = ReleaseLookup(emptyList(), false)
        }
        AlertDialog(onDismissRequest = { opened = false }, title = { Text("Upcoming releases") },
            text = {
                val loaded = result
                if (loaded == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else if (loaded.events.isEmpty()) {
                    Text(if (loaded.incomplete) "No upcoming dates found. Some release information is unavailable."
                        else "No upcoming releases in your saved titles.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        loaded.events.groupBy { it.date }.forEach { (date, entries) ->
                            item { Text(date.label(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                            items(entries) { entry ->
                                Column {
                                    Text(entry.title, fontWeight = FontWeight.Medium)
                                    if (entry.episode.isNotBlank()) Text(entry.episode, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        if (loaded.incomplete) item { Text("Some release information is unavailable.", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { opened = false }) { Text("Done") } })
    }
}
