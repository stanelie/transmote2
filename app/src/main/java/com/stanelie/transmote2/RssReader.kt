package com.stanelie.transmote2

import android.util.Xml
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

data class RssItem(
    val title: String,
    val link: String,
    val guid: String
)

class RssReader {
    private val client = HttpClient(OkHttp)

    suspend fun fetchRssItems(url: String): List<RssItem> {
        return withContext(Dispatchers.IO) {
            try {
                val response: HttpResponse = client.get(url)
                val inputStream = response.body<InputStream>()
                parse(inputStream)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    private fun parse(inputStream: InputStream): List<RssItem> {
        inputStream.use { stream ->
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(stream, null)

            val items = mutableListOf<RssItem>()
            var inItem = false
            var currentTitle: String? = null
            var currentLink: String? = null
            var currentGuid: String? = null
            var text: String? = null
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tagName.equals("item", ignoreCase = true)) {
                            inItem = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        text = parser.text
                    }
                    XmlPullParser.END_TAG -> if (inItem) {
                        when {
                            tagName.equals("title", ignoreCase = true) -> {
                                currentTitle = text
                            }
                            tagName.equals("link", ignoreCase = true) -> {
                                currentLink = text
                            }
                            tagName.equals("guid", ignoreCase = true) -> {
                                currentGuid = text
                            }
                            tagName.equals("item", ignoreCase = true) -> {
                                val title = currentTitle ?: ""
                                val link = currentLink ?: ""
                                // Use link as fallback for guid
                                val guid = currentGuid ?: link
                                
                                if (title.isNotBlank() && link.isNotBlank()) {
                                    items.add(RssItem(title, link, guid))
                                }
                                
                                inItem = false
                                currentTitle = null
                                currentLink = null
                                currentGuid = null
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
            return items
        }
    }
}
