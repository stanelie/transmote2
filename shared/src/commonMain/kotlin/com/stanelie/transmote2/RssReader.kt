package com.stanelie.transmote2

import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.xmlStreaming

data class RssItem(
    val title: String,
    val link: String,
    val guid: String,
    // What to hand Transmission. Torrent feeds conventionally put the
    // human-readable details page in <link> and the .torrent (or a magnet) in
    // <enclosure url="...">, so the enclosure wins when the feed has one;
    // feeds that point <link> straight at the file still work.
    val downloadUrl: String = link
)

// Shared SharedPreferences-style keys for RSS "seen"/"notified" tracking, used by
// TorrentCheckWorker (background notification dedup) and RssViewModel/RssScreen
// (in-app "new item" highlighting). These are deliberately separate keys: the
// worker must not mark items as "seen" for the UI before the user actually
// views the RSS list, or the highlight would never show.
object RssPrefs {
    const val PREFS_NAME = "rss_prefs"
    const val SEEN_GUIDS_KEY = "seen_guids"
    const val NOTIFIED_GUIDS_KEY = "notified_guids"
}

class RssReader {
    private val client = createHttpClient()

    suspend fun fetchRssItems(url: String): List<RssItem> {
        return withContext(Dispatchers.Default) {
            try {
                val response: HttpResponse = client.get(url)
                parse(response.bodyAsText())
            } catch (e: Exception) {
                Logger.e("RssReader", "Failed to fetch $url: ${e.message}")
                emptyList()
            }
        }
    }

    private fun parse(xml: String): List<RssItem> {
        val reader = xmlStreaming.newReader(xml)
        val items = mutableListOf<RssItem>()
        var inItem = false
        var currentTitle: String? = null
        var currentLink: String? = null
        var currentGuid: String? = null
        var currentEnclosure: String? = null
        val text = StringBuilder()

        while (reader.hasNext()) {
            when (reader.next()) {
                EventType.START_ELEMENT -> {
                    if (reader.localName.equals("item", ignoreCase = true)) inItem = true
                    if (inItem && reader.localName.equals("enclosure", ignoreCase = true)) {
                        currentEnclosure = (0 until reader.attributeCount)
                            .firstOrNull { reader.getAttributeLocalName(it).equals("url", ignoreCase = true) }
                            ?.let { reader.getAttributeValue(it) }
                    }
                    text.clear()
                }
                EventType.TEXT, EventType.CDSECT -> text.append(reader.text)
                EventType.END_ELEMENT -> if (inItem) {
                    val name = reader.localName
                    when {
                        name.equals("title", ignoreCase = true) -> currentTitle = text.toString()
                        name.equals("link", ignoreCase = true) -> currentLink = text.toString()
                        name.equals("guid", ignoreCase = true) -> currentGuid = text.toString()
                        name.equals("item", ignoreCase = true) -> {
                            val title = currentTitle ?: ""
                            val link = currentLink ?: ""
                            // Use link as fallback for guid
                            val guid = currentGuid ?: link
                            val download = currentEnclosure?.trim()?.takeIf { it.isNotBlank() }
                                ?: link.trim()

                            if (title.isNotBlank() && download.isNotBlank()) {
                                items.add(
                                    RssItem(title.trim(), link.trim(), guid.trim(), download)
                                )
                            }

                            inItem = false
                            currentTitle = null
                            currentLink = null
                            currentGuid = null
                            currentEnclosure = null
                        }
                    }
                    text.clear()
                }
                else -> {}
            }
        }
        return items
    }
}
