package com.stanelie.transmote2

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        TorrentMonitorService.start(this)
        scheduleRssWorkFromSettings()
    }

    private fun scheduleRssWorkFromSettings() {
        CoroutineScope(Dispatchers.IO).launch {
            val settings = PreferencesDataStore(applicationContext).settingsFlow.first()
            SettingsViewModel.scheduleRssWork(applicationContext, settings.rssPollingIntervalHours)
        }
    }
}
