package com.stanelie.transmote2

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "torrent_completion_channel"
        const val CHANNEL_NAME = "Torrent Completions"
        const val RSS_CHANNEL_ID = "rss_feed_channel"
        const val RSS_CHANNEL_NAME = "RSS Feed Updates"
        const val RSS_NOTIFICATION_ID = -100 // Fixed ID for the single-item or summary RSS notification
        const val RSS_GROUP_KEY = "com.stanelie.transmote2.RSS_GROUP"

        const val ACTION_SHOW_RSS = "com.stanelie.transmote2.ACTION_SHOW_RSS"
        const val ACTION_ADD_RSS_TORRENT = "com.stanelie.transmote2.ACTION_ADD_RSS_TORRENT"
        const val EXTRA_RSS_LINK = "extra_rss_link"
        const val EXTRA_RSS_GUID = "extra_rss_guid"
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for completed torrents"
            }
            notificationManager.createNotificationChannel(channel)

            val rssChannel = NotificationChannel(
                RSS_CHANNEL_ID,
                RSS_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for new items in the RSS feed"
            }
            notificationManager.createNotificationChannel(rssChannel)
        }
    }

    fun showCompletionNotification(torrentName: String, torrentId: Int) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Download Complete")
            .setContentText("$torrentName has finished downloading.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        notificationManager.notify(torrentId, builder.build())
    }

    /**
     * Tapping the notification for a single new item (or one item expanded out of a
     * group) adds that torrent directly. Tapping a group summary (several items at
     * once) opens the RSS list instead, where the new items are highlighted.
     */
    fun showRssNotification(newItems: List<RssItem>) {
        if (newItems.isEmpty()) return

        if (newItems.size == 1) {
            val item = newItems.first()
            val builder = NotificationCompat.Builder(context, RSS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("New RSS item")
                .setContentText(item.title)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(addTorrentPendingIntent(item))
                .setAutoCancel(true)
            notificationManager.notify(RSS_NOTIFICATION_ID, builder.build())
            return
        }

        // One real notification per item so each can be tapped individually when the
        // group is expanded, plus a summary notification for the collapsed group.
        newItems.forEach { item ->
            val builder = NotificationCompat.Builder(context, RSS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("New RSS item")
                .setContentText(item.title)
                .setGroup(RSS_GROUP_KEY)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(addTorrentPendingIntent(item))
                .setAutoCancel(true)
            notificationManager.notify(item.guid.hashCode(), builder.build())
        }

        val title = "${newItems.size} new RSS items"
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        newItems.take(5).forEach { style.addLine(it.title) }
        if (newItems.size > 5) style.setSummaryText("+${newItems.size - 5} more")

        val summaryBuilder = NotificationCompat.Builder(context, RSS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(newItems.first().title)
            .setNumber(newItems.size)
            .setGroup(RSS_GROUP_KEY)
            .setGroupSummary(true)
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(showRssListPendingIntent())
            .setAutoCancel(true)

        notificationManager.notify(RSS_NOTIFICATION_ID, summaryBuilder.build())
    }

    private fun showRssListPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_SHOW_RSS
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun addTorrentPendingIntent(item: RssItem): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_ADD_RSS_TORRENT
            putExtra(EXTRA_RSS_LINK, item.link)
            putExtra(EXTRA_RSS_GUID, item.guid)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        // Unique request code per item so each PendingIntent keeps its own extras
        // instead of the system collapsing them into one shared instance.
        return PendingIntent.getActivity(context, item.guid.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
