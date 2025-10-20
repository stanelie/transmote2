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
            val torrents = client.getTorrents()
            
            val completedTorrents = torrents.filter { it.percentDone >= 1.0 && it.isFinished }
            
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val notifiedIds = prefs.getStringSet(NOTIFIED_IDS_KEY, emptySet()) ?: emptySet()
            
            val newCompletedTorrents = completedTorrents.filter { it.id.toString() !in notifiedIds }

            if (newCompletedTorrents.isNotEmpty()) {
                Log.d(TAG, "Found ${newCompletedTorrents.size} new completed torrents.")
                newCompletedTorrents.forEach { torrent ->
                    notificationHelper.showCompletionNotification(torrent.name, torrent.id)
                }

                // Update the set of notified IDs
                val updatedNotifiedIds = notifiedIds.toMutableSet()
                newCompletedTorrents.forEach { updatedNotifiedIds.add(it.id.toString()) }
                prefs.edit().putStringSet(NOTIFIED_IDS_KEY, updatedNotifiedIds).apply()
            } else {
                Log.d(TAG, "No new completed torrents found.")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed", e)
            Result.failure()
        }
    }
}
