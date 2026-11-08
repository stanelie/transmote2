package com.stanelie.transmote2

import kotlinx.coroutines.flow.first

/**
 * One pass of "has anything happened?", run from whatever background
 * mechanism the platform offers - BGAppRefreshTask on iOS.
 *
 * Mirrors what TorrentMonitorService and TorrentCheckWorker already do on
 * Android, including their deliberately separate bookkeeping: NOTIFIED_*
 * keys track what has been announced, while RssPrefs.SEEN_GUIDS_KEY tracks
 * what the user has actually looked at and drives the in-app highlight.
 * Announcing something must never mark it as seen.
 */
object BackgroundChecker {

    private const val TAG = "BackgroundChecker"
    private const val MONITOR_PREFS = "transmote2_prefs"
    private const val NOTIFIED_IDS_KEY = "notified_torrent_ids"

    /** @return how many notifications were posted, so the caller can report
     *  "new data" to the OS and earn more frequent wake-ups. */
    suspend fun runCheck(): Int {
        var posted = 0
        val settings = try {
            PreferencesDataStore().settingsFlow.first()
        } catch (e: Exception) {
            Logger.e(TAG, "could not read settings: ${e.message}")
            return 0
        }

        posted += checkCompletedTorrents(settings)
        posted += checkRssFeed(settings)
        Logger.d(TAG, "check finished, $posted notification(s)")
        return posted
    }

    private suspend fun checkCompletedTorrents(settings: ConnectionSettings): Int {
        if (settings.serverAddress.isBlank() || settings.port.isBlank()) return 0
        return try {
            val store = StringSetStore(MONITOR_PREFS)
            val torrents = TransmissionRpcClient(settings).getTorrents()
            val allIds = torrents.map { it.id.toString() }.toSet()

            // Intersecting first drops ids for torrents that no longer exist,
            // so removing and re-adding a torrent notifies again.
            val synced = store.getStringSet(NOTIFIED_IDS_KEY).intersect(allIds)
            val newlyCompleted = torrents.filter {
                it.percentDone >= 1.0 && it.isFinished && it.id.toString() !in synced
            }
            if (newlyCompleted.isEmpty()) return 0

            newlyCompleted.forEach {
                showLocalNotification(
                    id = "torrent-${it.id}",
                    title = "Download complete",
                    body = it.name,
                    screen = "main"
                )
            }
            store.putStringSet(NOTIFIED_IDS_KEY, synced + newlyCompleted.map { it.id.toString() })
            newlyCompleted.size
        } catch (e: Exception) {
            Logger.e(TAG, "torrent check failed: ${e.message}")
            0
        }
    }

    private suspend fun checkRssFeed(settings: ConnectionSettings): Int {
        if (settings.rssFeedUrl.isBlank()) return 0
        return try {
            val store = StringSetStore(RssPrefs.PREFS_NAME)
            val notified = store.getStringSet(RssPrefs.NOTIFIED_GUIDS_KEY)
            val items = RssReader().fetchRssItems(settings.rssFeedUrl)
            if (items.isEmpty()) return 0

            val new = items.filter { it.guid !in notified }
            // First ever run: adopt the backlog silently rather than firing a
            // notification per existing item.
            if (notified.isEmpty()) {
                store.putStringSet(RssPrefs.NOTIFIED_GUIDS_KEY, items.map { it.guid }.toSet())
                Logger.d(TAG, "first RSS check, adopted ${items.size} items silently")
                return 0
            }
            if (new.isEmpty()) return 0

            if (new.size == 1) {
                showLocalNotification("rss-${new[0].guid}", "New in RSS", new[0].title, "rss")
            } else {
                showLocalNotification("rss-batch", "New in RSS", "${new.size} new items", "rss")
            }
            store.putStringSet(RssPrefs.NOTIFIED_GUIDS_KEY, notified + items.map { it.guid })
            1
        } catch (e: Exception) {
            Logger.e(TAG, "rss check failed: ${e.message}")
            0
        }
    }
}
