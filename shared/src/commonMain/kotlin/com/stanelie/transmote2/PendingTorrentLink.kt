package com.stanelie.transmote2

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A magnet link handed to the app from outside - Safari on iOS, an Intent on
 * Android. App() picks it up, adds it, and clears it.
 *
 * Held here rather than passed directly because the link can arrive before
 * the UI exists (a cold launch from a tapped link), so it has to wait for a
 * composition that can act on it.
 */
object PendingTorrentLink {
    private val _link = MutableStateFlow<String?>(null)
    val link: StateFlow<String?> = _link.asStateFlow()

    fun offer(url: String) {
        if (url.startsWith("magnet:", ignoreCase = true)) {
            Logger.d("PendingTorrentLink", "received magnet link")
            _link.value = url
        } else {
            Logger.e("PendingTorrentLink", "ignoring non-magnet url")
        }
    }

    fun consume() {
        _link.value = null
    }
}
