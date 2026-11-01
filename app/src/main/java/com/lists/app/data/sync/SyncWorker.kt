package com.lists.app.data.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.lists.app.data.repository.AppRepository
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for periodic background sync with Google Drive.
 */
class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SyncWorker"
        private const val UNIQUE_PERIODIC_WORK = "lists_periodic_sync"
        private const val UNIQUE_ONE_TIME_WORK = "lists_one_time_sync"

        /**
         * Schedule periodic sync every 6 hours.
         * Constrained to network connectivity; optionally Wi-Fi only.
         */
        fun schedulePeriodicSync(context: Context, wifiOnly: Boolean = true) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                6, TimeUnit.HOURS,
                30, TimeUnit.MINUTES // flex interval
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )

            Log.d(TAG, "Periodic sync scheduled (wifiOnly=$wifiOnly)")
        }

        /**
         * Cancel the periodic sync schedule.
         */
        fun cancelPeriodicSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC_WORK)
            Log.d(TAG, "Periodic sync cancelled")
        }

        /**
         * Trigger a one-time sync (e.g., after data mutation).
         * Debounced by replacing existing enqueued work.
         */
        fun triggerOneTimeSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setInitialDelay(30, TimeUnit.SECONDS) // debounce
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONE_TIME_WORK,
                ExistingWorkPolicy.REPLACE, // replaces any pending debounced sync
                oneTimeRequest
            )

            Log.d(TAG, "One-time sync enqueued (30s debounce)")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Sync worker starting...")

        val signInHelper = GoogleSignInHelper(applicationContext)
        val account = signInHelper.getSignedInAccount()

        if (account == null) {
            Log.d(TAG, "No signed-in account, skipping sync")
            return Result.success()
        }

        val syncManager = DriveSyncManager(applicationContext)
        if (!syncManager.autoSyncEnabled) {
            Log.d(TAG, "Auto-sync disabled, skipping")
            return Result.success()
        }

        val repository = AppRepository(applicationContext)

        return when (val result = syncManager.syncNow(account, repository)) {
            is DriveSyncManager.SyncResult.Success -> {
                Log.d(TAG, "Sync completed successfully")
                Result.success()
            }
            is DriveSyncManager.SyncResult.NoChanges -> {
                Log.d(TAG, "No changes to sync")
                Result.success()
            }
            is DriveSyncManager.SyncResult.NoAccount -> {
                Log.d(TAG, "No account available")
                Result.success()
            }
            is DriveSyncManager.SyncResult.Error -> {
                Log.e(TAG, "Sync failed: ${result.message}")
                Result.retry()
            }
        }
    }
}
