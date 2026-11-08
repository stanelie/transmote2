package com.stanelie.transmote2

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds this device's APNs token so it can be shown in Settings and copied to
 * the push server's config.
 *
 * Deliberately not auto-registering with a backend: this is a single-user app,
 * the token only changes on reinstall or device restore, and a paste beats
 * standing up a registration endpoint and storing tokens server-side.
 */
object PushRegistration {
    private val _token = MutableStateFlow<String?>(null)
    val token: StateFlow<String?> = _token.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun onToken(token: String) {
        Logger.d("PushRegistration", "APNs device token: $token")
        _token.value = token
        _error.value = null
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
