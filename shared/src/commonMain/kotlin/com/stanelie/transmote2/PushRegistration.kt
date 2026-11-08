package com.stanelie.transmote2

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds this device's APNs token and registers it with the push server.
 *
 * Registration carries the RSS feed URL as well as the token. The server
 * polls that feed, but the URL is owned by this app and the user can change
 * it in Settings at any time, so the server cannot hold its own copy without
 * going stale.
 */
object PushRegistration {
    private val _token = MutableStateFlow<String?>(null)
    val token: StateFlow<String?> = _token.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastRegistered: Pair<String, String>? = null

    fun onToken(token: String) {
        Logger.d("PushRegistration", "APNs device token: $token")
        _token.value = token
        _error.value = null
        pending?.let { (url, feed) -> registerIfPossible(url, feed) }
    }

    /** Remembered so a token arriving after settings load still registers. */
    private var pending: Pair<String, String>? = null

    fun registerIfPossible(pushServerUrl: String, rssFeedUrl: String) {
        if (pushServerUrl.isBlank()) return
        pending = pushServerUrl to rssFeedUrl
        val deviceToken = _token.value ?: return          // retried from onToken
        if (lastRegistered == deviceToken to rssFeedUrl) return

        scope.launch {
            try {
                val base = pushServerUrl.trimEnd('/')
                val client = createHttpClient()
                val response: HttpResponse = client.post("$base/register") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{"token":"$deviceToken","rssFeedUrl":"$rssFeedUrl","platform":"ios"}"""
                    )
                }
                if (response.status.isSuccess()) {
                    lastRegistered = deviceToken to rssFeedUrl
                    Logger.d("PushRegistration", "registered with $base")
                } else {
                    Logger.e("PushRegistration", "register failed: ${response.status}")
                }
            } catch (e: Exception) {
                Logger.e("PushRegistration", "register failed: ${e.message}")
            }
        }
    }

    fun onError(message: String) {
        Logger.e("PushRegistration", "APNs registration failed: $message")
        _error.value = message
    }
}

/** Screen a tapped push notification should open. */
object PushRouting {
    private val _pendingScreen = MutableStateFlow<String?>(null)
    val pendingScreen: StateFlow<String?> = _pendingScreen.asStateFlow()

    fun onNotificationOpened(screen: String?) {
        _pendingScreen.value = when (screen) {
            "rss" -> Routes.RSS_SCREEN
            else -> Routes.MAIN_SCREEN
        }
    }

    fun consume() {
        _pendingScreen.value = null
    }
}
