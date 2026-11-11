package com.stanelie.transmote2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RssViewModel(
    private val preferencesDataStore: PreferencesDataStore,
    private val rssReader: RssReader
) : ViewModel() {

    private val _rssItems = MutableStateFlow<List<RssItem>>(emptyList())
    val rssItems: StateFlow<List<RssItem>> = _rssItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // The baseline the "new" highlight is drawn against: what had already been
    // seen when this visit to the screen began. A refresh deliberately does
    // NOT move it, so items cannot grey out under the user mid-scroll - but it
    // is re-read on every visit, so leaving the screen and coming back does
    // clear the highlight.
    private val _seenItemGuids = MutableStateFlow<Set<String>>(emptySet())
    val seenItemGuids: StateFlow<Set<String>> = _seenItemGuids.asStateFlow()

    private val seenStore = StringSetStore(RssPrefs.PREFS_NAME)

    init {
        startVisit()
        // Deliberately no fetch here: the feed is only fetched from its screen.
    }

    /**
     * Take the stored seen-set as this visit's baseline. Must run before
     * refreshFeed, which marks everything it fetches as seen - afterwards
     * there would be nothing left to highlight.
     */
    fun startVisit() {
        _seenItemGuids.value = seenStore.getStringSet(RssPrefs.SEEN_GUIDS_KEY)
    }

    fun refreshFeed() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _error.value = null
            try {
                val rssUrl = preferencesDataStore.settingsFlow.first().rssFeedUrl
                if (rssUrl.isNotBlank()) {
                    val items = rssReader.fetchRssItems(rssUrl)
                    _rssItems.value = items
                    // Seen as of the *next* visit. _seenItemGuids is left
                    // alone, so this fetch does not erase its own highlight.
                    markAllAsSeen(items.map { it.guid })
                } else {
                    _error.value = "RSS Feed URL is not configured in settings."
                }
            } catch (e: Exception) {
                _error.value = "Failed to fetch RSS feed: ${e.message}"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private fun markAllAsSeen(guids: List<String>) {
        val allGuids = seenStore.getStringSet(RssPrefs.SEEN_GUIDS_KEY)
        seenStore.putStringSet(RssPrefs.SEEN_GUIDS_KEY, allGuids + guids)
    }

    companion object {
        fun provideFactory(
            preferencesDataStore: PreferencesDataStore,
            rssReader: RssReader
        ) = viewModelFactory {
            initializer { RssViewModel(preferencesDataStore, rssReader) }
        }
    }
}
