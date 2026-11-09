package com.stanelie.transmote2

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A torrent handed to the app from outside - a magnet link or a .torrent
 * file opened from Safari, Files or a share sheet.
 *
 * The payload is whatever MainViewModel.addTorrent expects: a "magnet:" URI,
 * or the base64 of a .torrent file's bytes, which it sends as metainfo.
 *
 * Held here rather than passed straight to the ViewModel because an opened
 * link can cold-launch the app, so it may arrive before any composition
 * exists to act on it.
 */
object PendingTorrentLink {
    private val _payload = MutableStateFlow<String?>(null)
    val payload: StateFlow<String?> = _payload.asStateFlow()

    fun offerMagnet(url: String) {
        if (url.startsWith("magnet:", ignoreCase = true)) {
            Logger.d("PendingTorrentLink", "received magnet link")
            _payload.value = url
        } else {
            Logger.e("PendingTorrentLink", "ignoring non-magnet url")
        }
    }

    /** Base64 of a .torrent file's bytes. */
    fun offerMetainfo(base64: String) {
        if (base64.isNotBlank()) {
            Logger.d("PendingTorrentLink", "received .torrent metainfo (${base64.length} b64 chars)")
            _payload.value = base64
        } else {
            Logger.e("PendingTorrentLink", "ignoring empty .torrent")
        }
    }

    fun consume() {
        _payload.value = null
    }
}
