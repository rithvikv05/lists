package com.minimal.lists.ui.components

import android.content.Context
import android.os.Build
import android.widget.NumberPicker
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.minimal.lists.data.db.ListItem
import com.minimal.lists.data.repository.AppRepository
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun GameCompletionCard(item: ListItem, repository: AppRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val names = listOf("Main", "Main + Sides", "Completion")
    val record = remember(item.metadataJson) { runCatching { JSONObject(item.metadataJson).optJSONArray("gameCompletionLog") }.getOrNull() }
    val values = remember(item.id, item.metadataJson) {
        if (record != null) List(3) { record.optInt(it).coerceAtLeast(0) }
        else {
            val prefs = context.getSharedPreferences("lists_game_completion_prefs", Context.MODE_PRIVATE)
            listOf("story", "extra", "comp").map { key ->
                if (prefs.contains("game_${item.id}_${key}_hours")) prefs.getInt("game_${item.id}_${key}_hours", 0) * 60
                else prefs.getInt("game_${item.id}_$key", 0)
            }
        }
    }
    LaunchedEffect(item.id, item.metadataJson) {
        if (record == null && values.any { it > 0 }) repository.saveGameCompletion(item.id, values)
    }
    var editing by remember(item.id) { mutableStateOf<Int?>(null) }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Completion Log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = { editing = values.indexOfFirst { it == 0 }.takeIf { it >= 0 } ?: 0 }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, "Add completion time", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
            if (values.any { it > 0 }) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    names.forEachIndexed { index, label ->
                        Column(Modifier.weight(1f).clickable { editing = index }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(durationLabel(values[index]), style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                            Text(if (index == 2) "Complete" else label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    editing?.let { initial ->
        var selected by remember { mutableIntStateOf(initial) }
        var hours by remember { mutableIntStateOf(values[initial] / 60) }
        var minutes by remember { mutableIntStateOf(values[initial] % 60) }
        var menu by remember { mutableStateOf(false) }
        var saving by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf(false) }
        fun save(value: Int) {
            saving = true
            scope.launch {
                val updated = values.toMutableList().apply { this[selected] = value }
                val success = runCatching { repository.saveGameCompletion(item.id, updated) }.getOrDefault(false)
                saving = false
                if (success) editing = null else error = true
            }
        }
        AlertDialog(onDismissRequest = { if (!saving) editing = null }, title = { Text("Completion Log") },
            text = {
                Column {
                    Box {
                        OutlinedButton(onClick = { menu = true }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                            Text(names[selected], modifier = Modifier.weight(1f))
                            Icon(Icons.Default.KeyboardArrowDown, null)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            names.forEachIndexed { index, name ->
                                DropdownMenuItem(text = { Text(name) }, onClick = {
                                    selected = index; hours = values[index] / 60; minutes = values[index] % 60; menu = false
                                })
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        DurationWheel("Hours", hours, 9999, !saving) { hours = it }
                        DurationWheel("Minutes", minutes, 59, !saving) { minutes = it }
                    }
                    if (error) Text("Could not save. Please try again.", color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { TextButton(onClick = { save(hours * 60 + minutes) }, enabled = !saving && hours * 60 + minutes > 0) { Text("Save") } },
            dismissButton = {
                Row {
                    if (values[selected] > 0) TextButton(onClick = { save(0) }, enabled = !saving) { Text("Remove") }
                    TextButton(onClick = { editing = null }, enabled = !saving) { Text("Cancel") }
                }
            })
    }
}

@Composable
private fun DurationWheel(label: String, value: Int, maximum: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val latestOnChange by rememberUpdatedState(onChange)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AndroidView(factory = { context ->
            NumberPicker(context).apply {
                minValue = 0; maxValue = maximum; wrapSelectorWheel = false
                descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                setOnValueChangedListener { _, _, current -> latestOnChange(current) }
            }
        }, update = { picker ->
            picker.value = value.coerceIn(0, maximum)
            picker.isEnabled = enabled
            picker.contentDescription = label
            if (Build.VERSION.SDK_INT >= 29) picker.textColor = textColor
        }, modifier = Modifier.width(100.dp).height(152.dp))
    }
}

private fun durationLabel(minutes: Int): String = when {
    minutes <= 0 -> "—"
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}

@Composable
fun GamePlatformsCard(item: ListItem) {
    val platforms = remember(item.metadataJson, item.gamePlatform) {
        val metadata = runCatching { JSONObject(item.metadataJson).optJSONArray("platforms") }.getOrNull()
        val catalog = (0 until (metadata?.length() ?: 0)).mapNotNull { metadata?.optString(it)?.takeIf(String::isNotBlank) }
        (catalog + item.gamePlatform.split(",")).map(String::trim).filter(String::isNotBlank).distinctBy { it.lowercase() }
    }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Platforms", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(if (platforms.isEmpty()) "Platform information unavailable" else platforms.joinToString(" • "),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
