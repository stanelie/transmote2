package com.stanelie.transmote2

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * WorkManager side of BackgroundPolling. Lives in :app, next to the worker,
 * the notification helper and the boot receiver it belongs with.
 */
fun scheduleRssWork(context: Context, intervalHours: Int) {
    val workManager = WorkManager.getInstance(context)
    if (intervalHours <= 0) {
        workManager.cancelUniqueWork(TorrentCheckWorker.WORKER_NAME)
        return
    }
    val request = PeriodicWorkRequestBuilder<TorrentCheckWorker>(
        intervalHours.toLong(), TimeUnit.HOURS
    ).build()
    workManager.enqueueUniquePeriodicWork(
        TorrentCheckWorker.WORKER_NAME,
        ExistingPeriodicWorkPolicy.UPDATE,
        request
    )
}
