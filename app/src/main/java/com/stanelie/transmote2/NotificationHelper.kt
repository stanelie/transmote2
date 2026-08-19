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
        const val RSS_NOTIFICATION_ID = -100 // A fixed ID for the RSS notification group
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

    fun showRssNotification(newItems: List<RssItem>) {
        if (newItems.isEmpty()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "ACTION_SHOW_RSS"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val title = if (newItems.size == 1) "New RSS item" else "${newItems.size} new RSS items"
        val builder = NotificationCompat.Builder(context, RSS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(newItems.first().title)
            .setNumber(newItems.size)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (newItems.size > 1) {
            val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
            newItems.take(5).forEach { style.addLine(it.title) }
            if (newItems.size > 5) style.setSummaryText("+${newItems.size - 5} more")
            builder.setStyle(style)
        }

        notificationManager.notify(RSS_NOTIFICATION_ID, builder.build())
    }
}
