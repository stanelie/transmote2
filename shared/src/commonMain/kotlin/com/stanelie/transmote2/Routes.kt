package com.stanelie.transmote2

object Routes {
    const val MAIN_SCREEN = "main_screen"
    const val SETTINGS_SCREEN = "settings_screen"
    const val RSS_SCREEN = "rss_screen"
    const val TORRENT_DETAIL_SCREEN = "torrent_detail_screen/{torrentId}"
    fun torrentDetailRoute(torrentId: Int) = "torrent_detail_screen/$torrentId"
}
