package com.lists.app.data.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import com.lists.app.data.repository.AppRepository
import java.io.ByteArrayOutputStream
import java.util.Collections

/**
 * Manages bidirectional sync between local Room DB and Google Drive appDataFolder.
 * 
 * Sync algorithm:
 * 1. Export local DB -> JSON
 * 2. Check if remote file exists in appDataFolder
 * 3. If no remote -> upload local
 * 4. If remote exists -> download, merge tombstones & items, re-upload merged
 */
class DriveSyncManager(private val context: Context) {

    sealed class SyncStatus {
        data object Idle : SyncStatus()
        data object Syncing : SyncStatus()
        data class Success(val timestamp: Long) : SyncStatus()
        data class Error(val message: String) : SyncStatus()
    }

    companion object {
        private const val TAG = "DriveSyncManager"
        private const val SYNC_FILE_NAME = "listy_sync.json"
        private const val PREFS_NAME = "listy_sync_prefs"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
        private const val KEY_AUTO_SYNC = "auto_sync_enabled"
        private const val KEY_WIFI_ONLY = "wifi_only_sync"

        private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
        val syncStatus: StateFlow<SyncStatus> = _syncStatus

        fun setStatus(status: SyncStatus) {
            _syncStatus.value = status
        }
    }

    private val syncPrefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var lastSyncTimestamp: Long
        get() = syncPrefs.getLong(KEY_LAST_SYNC, 0L)
        private set(value) {
            syncPrefs.edit().putLong(KEY_LAST_SYNC, value).apply()
            _syncStatus.value = if (value > 0) SyncStatus.Success(value) else SyncStatus.Idle
        }

    var autoSyncEnabled: Boolean
        get() = syncPrefs.getBoolean(KEY_AUTO_SYNC, true)
        set(value) = syncPrefs.edit().putBoolean(KEY_AUTO_SYNC, value).apply()

    var wifiOnlySync: Boolean
        get() = syncPrefs.getBoolean(KEY_WIFI_ONLY, true)
        set(value) = syncPrefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()

    init {
        val last = syncPrefs.getLong(KEY_LAST_SYNC, 0L)
        if (last > 0 && _syncStatus.value is SyncStatus.Idle) {
            _syncStatus.value = SyncStatus.Success(last)
        }
    }

    /**
     * Build a Drive service instance from the signed-in account.
     */
    private fun getDriveService(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            Collections.singleton(DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccount = account.account

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("Lists")
            .build()
    }

    /**
     * Find the sync file in appDataFolder. Returns the file metadata or null.
     */
    private suspend fun findRemoteFile(drive: Drive): File? = withContext(Dispatchers.IO) {
        try {
            val result = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = '$SYNC_FILE_NAME'")
                .setFields("files(id, name, modifiedTime)")
                .setPageSize(1)
                .execute()

            result.files?.firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Error finding remote file", e)
            null
        }
    }

    /**
     * Download the sync file content from Drive.
     */
    private suspend fun downloadFile(drive: Drive, fileId: String): String? = withContext(Dispatchers.IO) {
        try {
            val outputStream = ByteArrayOutputStream()
            drive.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            outputStream.toString("UTF-8")
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading file", e)
            null
        }
    }

    /**
     * Upload/create the sync file in appDataFolder.
     */
    private suspend fun uploadFile(drive: Drive, content: String, existingFileId: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val mediaContent = ByteArrayContent.fromString("application/json", content)

            if (existingFileId != null) {
                // Update existing file
                drive.files().update(existingFileId, null, mediaContent)
                    .execute()
            } else {
                // Create new file
                val fileMetadata = File().apply {
                    name = SYNC_FILE_NAME
                    parents = listOf("appDataFolder")
                }
                drive.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading file", e)
            false
        }
    }

    /**
     * Result of a sync operation.
     */
    sealed class SyncResult {
        data object Success : SyncResult()
        data object NoAccount : SyncResult()
        data object NoChanges : SyncResult()
        data class Error(val message: String) : SyncResult()
    }

    /**
     * Perform a full bidirectional sync.
     */
    suspend fun syncNow(account: GoogleSignInAccount, repository: AppRepository): SyncResult = withContext(Dispatchers.IO) {
        _syncStatus.value = SyncStatus.Syncing
        val result = try {
            Log.d(TAG, "Starting sync...")

            val drive = getDriveService(account)

            // Step 1: Export local data (includes tombstones)
            val localJson = repository.exportDataToJson()

            // Step 2: Check for remote file
            val remoteFile = findRemoteFile(drive)

            if (remoteFile == null) {
                // No remote file — first sync, upload local snapshot
                Log.d(TAG, "No remote file found. Uploading local data...")
                val success = uploadFile(drive, localJson)
                if (success) {
                    lastSyncTimestamp = System.currentTimeMillis()
                    Log.d(TAG, "Initial upload complete")
                    SyncResult.Success
                } else {
                    SyncResult.Error("Failed to upload initial sync file")
                }
            } else {
                // Remote file exists — download and merge
                Log.d(TAG, "Remote file found (${remoteFile.id}). Downloading...")
                val remoteJson = downloadFile(drive, remoteFile.id)

                if (remoteJson == null) {
                    SyncResult.Error("Failed to download remote sync file")
                } else {
                    // Merge: handles both directions, tombstones, and deletion propagation
                    Log.d(TAG, "Merging local and remote data...")
                    val mergedJson = repository.mergeFromRemote(remoteJson)

                    // Upload the merged result back to Drive
                    Log.d(TAG, "Uploading merged data...")
                    val success = uploadFile(drive, mergedJson, remoteFile.id)
                    if (success) {
                        lastSyncTimestamp = System.currentTimeMillis()
                        Log.d(TAG, "Sync complete")
                        SyncResult.Success
                    } else {
                        SyncResult.Error("Failed to upload merged sync file")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            SyncResult.Error(e.message ?: "Unknown error")
        }

        when (result) {
            is SyncResult.Success -> _syncStatus.value = SyncStatus.Success(lastSyncTimestamp)
            is SyncResult.Error -> _syncStatus.value = SyncStatus.Error(result.message)
            else -> _syncStatus.value = SyncStatus.Idle
        }
        result
    }

    /**
     * Force upload local data to Drive (overwrite remote).
     */
    suspend fun forceUpload(account: GoogleSignInAccount, repository: AppRepository): SyncResult = withContext(Dispatchers.IO) {
        _syncStatus.value = SyncStatus.Syncing
        val result = try {
            val drive = getDriveService(account)
            val localJson = repository.exportDataToJson()
            val remoteFile = findRemoteFile(drive)
            val success = uploadFile(drive, localJson, remoteFile?.id)
            if (success) {
                lastSyncTimestamp = System.currentTimeMillis()
                SyncResult.Success
            } else {
                SyncResult.Error("Failed to upload")
            }
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Unknown error")
        }
        when (result) {
            is SyncResult.Success -> _syncStatus.value = SyncStatus.Success(lastSyncTimestamp)
            is SyncResult.Error -> _syncStatus.value = SyncStatus.Error(result.message)
            else -> _syncStatus.value = SyncStatus.Idle
        }
        result
    }

    /**
     * Force download remote data and replace local (destructive restore).
     */
    suspend fun forceDownload(account: GoogleSignInAccount, repository: AppRepository): SyncResult = withContext(Dispatchers.IO) {
        _syncStatus.value = SyncStatus.Syncing
        val result = try {
            val drive = getDriveService(account)
            val remoteFile = findRemoteFile(drive) ?: return@withContext SyncResult.Error("No remote backup found")
            val remoteJson = downloadFile(drive, remoteFile.id) ?: return@withContext SyncResult.Error("Failed to download")
            val success = repository.importDataFromJson(remoteJson)
            if (success) {
                lastSyncTimestamp = System.currentTimeMillis()
                SyncResult.Success
            } else {
                SyncResult.Error("Failed to import remote data")
            }
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Unknown error")
        }
        when (result) {
            is SyncResult.Success -> _syncStatus.value = SyncStatus.Success(lastSyncTimestamp)
            is SyncResult.Error -> _syncStatus.value = SyncStatus.Error(result.message)
            else -> _syncStatus.value = SyncStatus.Idle
        }
        result
    }
}
