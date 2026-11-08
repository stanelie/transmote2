package com.stanelie.transmote2

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

private const val CHANNEL_ID = "transmote_events"

actual fun showLocalNotification(id: String, title: String, body: String, screen: String?) {
    val context = AndroidApp.context
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Transmote", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    @Suppress("DEPRECATION")
    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        android.app.Notification.Builder(context, CHANNEL_ID)
    } else {
        android.app.Notification.Builder(context)
    }

    manager.notify(
        id.hashCode(),
        builder
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(context.applicationInfo.icon)
            .setAutoCancel(true)
            .build()
    )
}
