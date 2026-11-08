package com.stanelie.transmote2

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AndroidApp.init(this)
        BackgroundPolling.register { hours -> scheduleRssWork(applicationContext, hours) }
        TorrentMonitorService.start(this)
        scheduleRssWorkFromSettings()
    }

    private fun scheduleRssWorkFromSettings() {
        CoroutineScope(Dispatchers.IO).launch {
            val settings = PreferencesDataStore().settingsFlow.first()
            scheduleRssWork(applicationContext, settings.rssPollingIntervalHours)
        }
    }
}
