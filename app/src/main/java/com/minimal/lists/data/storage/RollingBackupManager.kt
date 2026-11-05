package com.minimal.lists.data.storage

import android.content.Context
import com.minimal.lists.data.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupSnapshot(
    val id: Int,
    val file: File,
    val timestamp: Long,
    val itemCount: Int,
    val listCount: Int,
    val formattedDate: String,
    val formattedSize: String
)

class RollingBackupManager(private val context: Context) {
    private val snapshotDir = File(context.filesDir, "snapshots").apply { mkdirs() }

    suspend fun listSnapshots(): List<BackupSnapshot> = withContext(Dispatchers.IO) {
        val files = snapshotDir.listFiles { f -> f.extension == "json" } ?: emptyArray()
        files.sortedByDescending { it.lastModified() }.take(1).mapIndexed { index, file ->
            var items = 0
            var lists = 0
            var time = file.lastModified()
            try {
                val content = file.readText()
                val json = JSONObject(content)
                lists = json.optJSONArray("lists")?.length() ?: 0
                items = json.optJSONArray("items")?.length() ?: 0
                time = json.optLong("exportedAt", time)
            } catch (_: Exception) {}

            val sizeKb = (file.length() / 1024).coerceAtLeast(1)
            val dateStr = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(time))

            BackupSnapshot(
                id = index + 1,
                file = file,
                timestamp = time,
                itemCount = items,
                listCount = lists,
                formattedDate = dateStr,
                formattedSize = "$sizeKb KB"
            )
        }
    }

    suspend fun captureSnapshot(repository: AppRepository): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = repository.exportDataToJson()
            // Keep strictly 1 local rolling snapshot
            val existing = snapshotDir.listFiles { f -> f.extension == "json" } ?: emptyArray()
            existing.forEach { it.delete() }
            val newFile = File(snapshotDir, "snapshot_latest.json")
            newFile.writeText(json)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun restoreSnapshot(snapshot: BackupSnapshot, repository: AppRepository): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = snapshot.file.readText()
            repository.importDataFromJson(json)
        } catch (_: Exception) {
            false
        }
    }
}

