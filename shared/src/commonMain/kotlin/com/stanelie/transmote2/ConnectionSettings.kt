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
    val rssPollingIntervalHours: Int = 6,
    /**
     * Base URL of the push server (see server/). Blank disables push.
     * Treat it as a capability URL - the app registers its APNs token and
     * current RSS feed URL there, so the secret lives in the path.
     */
    val pushServerUrl: String = ""
)
