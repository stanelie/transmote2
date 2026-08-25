package com.stanelie.transmote2

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first

class TorrentCheckWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORKER_NAME = "RssCheckWorker"
    }

    private val TAG = "TorrentCheckWorker"

    override suspend fun doWork(): Result {
        Log.d(TAG, "RSS worker running.")
        val notificationHelper = NotificationHelper(applicationContext)
        notificationHelper.createNotificationChannel()

        return try {
            val settings = PreferencesDataStore(applicationContext).settingsFlow.first()
            if (settings.rssFeedUrl.isBlank()) {
                Log.d(TAG, "RSS URL not configured, skipping.")
                return Result.success()
            }

            val rssPrefs = applicationContext.getSharedPreferences(RssPrefs.PREFS_NAME, Context.MODE_PRIVATE)
            // Tracks which items have already triggered a notification; kept separate
            // from RssPrefs.SEEN_GUIDS_KEY, which tracks what the user has actually
            // viewed in the RSS list and drives the "new item" highlight there.
            val notifiedGuids = rssPrefs.getStringSet(RssPrefs.NOTIFIED_GUIDS_KEY, emptySet()) ?: emptySet()

            val items = RssReader().fetchRssItems(settings.rssFeedUrl)
            val newItems = items.filter { it.guid !in notifiedGuids }

            if (newItems.isNotEmpty()) {
                Log.d(TAG, "Found ${newItems.size} new RSS items.")
                notificationHelper.showRssNotification(newItems)
                val updatedGuids = notifiedGuids.toMutableSet()
                    .also { it.addAll(items.map { i -> i.guid }) }
                rssPrefs.edit().putStringSet(RssPrefs.NOTIFIED_GUIDS_KEY, updatedGuids).apply()
            } else {
                Log.d(TAG, "No new RSS items.")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "RSS worker failed", e)
            Result.failure()
        }
    }
}
