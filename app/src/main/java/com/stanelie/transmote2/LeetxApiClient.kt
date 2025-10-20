package com.stanelie.transmote2

import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.jsoup.Jsoup

// Data classes remain the same as they are for holding the parsed data
data class SearchResult(
    val name: String,
    val seeders: String,
    val leechers: String,
    val size: String,
    val time: String,
    val detailsUrl: String // Changed 'id' to 'detailsUrl'
)

data class TorrentDetails(
    val name: String,
    val magnet: String
)

class LeetxApiClient {
    private val client = HttpClient(OkHttp)
    private val TAG = "LeetxApiClient"
    private val BASE_URL = "https://1337x.to"

    private suspend fun getHtml(url: String): String {
        return client.get(url) {
            header(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
        }.body()
    }

    suspend fun search(query: String, category: String): List<SearchResult> {
        val encodedQuery = query.encodeURLPath()
        // Use category-search for better results, similar to the Java example
        val url = "$BASE_URL/category-search/$encodedQuery/$category/1/"
        Log.d(TAG, "Searching URL: $url")
        
        return try {
            val html = getHtml(url)
            val doc = Jsoup.parse(html, BASE_URL)
            val rows = doc.select("table.table-list > tbody > tr")
            
            val results = mutableListOf<SearchResult>()
            for (row in rows) {
                val columns = row.select("td")
                if (columns.size < 5) continue

                val nameCol = columns.get(0)
                val links = nameCol.select("a")
                if (links.size < 2) continue

                val detailsUrl = BASE_URL + links.get(1).attr("href")
                val name = links.get(1).text()
                val seeders = columns.get(1).text()
                val leechers = columns.get(2).text()
                val time = columns.get(3).text()
                val size = columns.get(4).ownText()

                results.add(SearchResult(name, seeders, leechers, size, time, detailsUrl))
            }
            results
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            emptyList()
        }
    }

    suspend fun getTorrentDetails(detailsUrl: String): TorrentDetails? {
        Log.d(TAG, "Fetching details from URL: $detailsUrl")
        return try {
            val html = getHtml(detailsUrl)
            val doc = Jsoup.parse(html)
            val magnetLinkElement = doc.selectFirst("a[href^=magnet:]")
            val name = doc.selectFirst("div.box-info-heading h1")?.text() ?: ""
            
            if (magnetLinkElement != null) {
                TorrentDetails(name, magnetLinkElement.attr("href"))
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get torrent details", e)
            null
        }
    }
}
