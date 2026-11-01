package com.lists.app.data.sync

import com.lists.app.currentTimeMillis
import com.lists.app.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DriveSyncManager(val context: Any? = null) {
    sealed class SyncStatus {
        data object Idle : SyncStatus()
        data object Syncing : SyncStatus()
        data class Success(val timestamp: Long) : SyncStatus()
        data class Error(val message: String) : SyncStatus()
    }

    sealed class SyncResult {
        data object Success : SyncResult()
        data class Error(val message: String) : SyncResult()
    }

    companion object {
        private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
        val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

        fun setStatus(status: SyncStatus) {
            _syncStatus.value = status
        }
    }

    var lastSyncTimestamp: Long
        get() = GoogleDriveSync.getLastSyncTime() ?: 0L
        set(value) {
            GoogleDriveSync.setLastSyncTime(value)
            _syncStatus.value = if (value > 0) SyncStatus.Success(value) else SyncStatus.Idle
        }

    var autoSyncEnabled: Boolean = true
    var wifiOnlySync: Boolean = false

    suspend fun syncNow(account: GoogleSignInAccount? = null, repository: AppRepository? = null): SyncResult {
        _syncStatus.value = SyncStatus.Syncing
        val success = GoogleDriveSync.syncNow()
        val now = currentTimeMillis()
        return if (success) {
            lastSyncTimestamp = now
            _syncStatus.value = SyncStatus.Success(now)
            SyncResult.Success
        } else {
            val err = "Sync failed. Check connection or sign in again."
            _syncStatus.value = SyncStatus.Error(err)
            SyncResult.Error(err)
        }
    }

    suspend fun forceUpload(account: GoogleSignInAccount, repository: AppRepository): SyncResult {
        return syncNow(account, repository)
    }

    suspend fun forceDownload(account: GoogleSignInAccount, repository: AppRepository): SyncResult {
        return syncNow(account, repository)
    }
}
