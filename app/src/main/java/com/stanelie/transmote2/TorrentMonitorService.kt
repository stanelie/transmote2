package com.stanelie.transmote2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TorrentMonitorService : Service() {

    companion object {
        private const val MONITOR_NOTIFICATION_ID = 2001
        private const val MONITOR_CHANNEL_ID = "torrent_monitor_channel"
        private const val PREFS_NAME = "transmote2_prefs"
        private const val NOTIFIED_IDS_KEY = "notified_torrent_ids"
        private const val POLL_INTERVAL_MS = 60_000L
        private val TAG = "TorrentMonitorService"

        fun start(context: Context) {
            val intent = Intent(context, TorrentMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createMonitorChannel()
        NotificationHelper(applicationContext).createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        startMonitoring()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createMonitorChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MONITOR_CHANNEL_ID,
                "Download Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors active downloads for completion"
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildMonitorNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, MONITOR_CHANNEL_ID)
            .setContentTitle("Transmote")
            .setContentText("Monitoring downloads")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startForegroundCompat() {
        val notification = buildMonitorNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                MONITOR_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(MONITOR_NOTIFICATION_ID, notification)
        }
    }

    private fun startMonitoring() {
        serviceScope.launch {
            val preferencesDataStore = PreferencesDataStore()
            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val notificationHelper = NotificationHelper(applicationContext)

            while (isActive) {
                try {
                    val settings = preferencesDataStore.settingsFlow.first()
                    if (settings.serverAddress.isNotBlank() && settings.port.isNotBlank()) {
                        val allTorrents = TransmissionRpcClient(settings).getTorrents()
                        val allIds = allTorrents.map { it.id.toString() }.toSet()

                        val previouslyNotified = prefs.getStringSet(NOTIFIED_IDS_KEY, emptySet()) ?: emptySet()
                        val synced = previouslyNotified.intersect(allIds)

                        val newlyCompleted = allTorrents.filter {
                            it.percentDone >= 1.0 && it.isFinished && it.id.toString() !in synced
                        }

                        if (newlyCompleted.isNotEmpty()) {
                            newlyCompleted.forEach {
                                notificationHelper.showCompletionNotification(it.name, it.id)
                            }
                            val updated = synced.toMutableSet()
                                .also { set -> newlyCompleted.forEach { set.add(it.id.toString()) } }
                            prefs.edit().putStringSet(NOTIFIED_IDS_KEY, updated).apply()
                            Log.d(TAG, "Notified for ${newlyCompleted.size} completed torrents.")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Poll error", e)
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }
}
