package com.stanelie.transmote2

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first

class TorrentCheckWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORKER_NAME = "TorrentCheckWorker"
        private const val PREFS_NAME = "transmote2_prefs"
        private const val NOTIFIED_IDS_KEY = "notified_torrent_ids"
    }

    private val TAG = "TorrentCheckWorker"
    private val notificationHelper = NotificationHelper(applicationContext)

    override suspend fun doWork(): Result {
        Log.d(TAG, "Worker is running...")
        notificationHelper.createNotificationChannel()

        return try {
            val preferencesDataStore = PreferencesDataStore(applicationContext)
            val settings = preferencesDataStore.settingsFlow.first()

            if (settings.serverAddress.isBlank() || settings.port.isBlank()) {
                Log.d(TAG, "Worker exiting: Server not configured.")
                return Result.success()
            }

            val client = TransmissionRpcClient(settings)
            val allServerTorrents = client.getTorrents()
            val allServerTorrentIds = allServerTorrents.map { it.id.toString() }.toSet()

            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val previouslyNotifiedIds = prefs.getStringSet(NOTIFIED_IDS_KEY, emptySet()) ?: emptySet()
            
            // **New Logic**: Sync the notified list with the server state.
            // Keep only the IDs that are still on the server.
            val syncedNotifiedIds = previouslyNotifiedIds.intersect(allServerTorrentIds)
            
            val completedTorrents = allServerTorrents.filter { it.percentDone >= 1.0 && it.isFinished }
            
            // Find torrents that are complete but not in our synced list of notified torrents.
            val newCompletedTorrents = completedTorrents.filter { it.id.toString() !in syncedNotifiedIds }

            if (newCompletedTorrents.isNotEmpty()) {
                Log.d(TAG, "Found ${newCompletedTorrents.size} new completed torrents.")
                newCompletedTorrents.forEach { torrent ->
                    notificationHelper.showCompletionNotification(torrent.name, torrent.id)
                }

                // Update the set of notified IDs with the newly completed ones.
                val updatedNotifiedIds = syncedNotifiedIds.toMutableSet()
                newCompletedTorrents.forEach { updatedNotifiedIds.add(it.id.toString()) }
                prefs.edit().putStringSet(NOTIFIED_IDS_KEY, updatedNotifiedIds).apply()
                Log.d(TAG, "Updated notified IDs: $updatedNotifiedIds")
            } else {
                Log.d(TAG, "No new completed torrents found.")
                // Also re-save the synced list in case items were removed
                if (syncedNotifiedIds.size != previouslyNotifiedIds.size) {
                    prefs.edit().putStringSet(NOTIFIED_IDS_KEY, syncedNotifiedIds).apply()
                    Log.d(TAG, "Cleaned up notified IDs list: $syncedNotifiedIds")
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed", e)
            Result.failure()
        }
    }
}
