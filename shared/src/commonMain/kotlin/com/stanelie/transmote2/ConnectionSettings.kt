package com.stanelie.transmote2

data class ConnectionSettings(
    val serverAddress: String = "",
    val port: String = "",
    val username: String = "",
    val password: String = "",
    val useSsl: Boolean = false,
    val refreshFrequency: Int = 30,
    val rssFeedUrl: String = "",
    val webDownloadUrl: String = "",
    val rssPollingIntervalHours: Int = 6
)
