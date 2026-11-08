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

    private val _seenItemGuids = MutableStateFlow<Set<String>>(emptySet())
    val seenItemGuids: StateFlow<Set<String>> = _seenItemGuids.asStateFlow()

    private val seenStore = StringSetStore(RssPrefs.PREFS_NAME)
    internal var initialGuids: Set<String> = emptySet() // Changed from private to internal

    init {
        loadSeenItems()
        // Removed fetchRssFeed() from init to avoid fetching on app start
    }

    private fun loadSeenItems() {
        val guids = seenStore.getStringSet(RssPrefs.SEEN_GUIDS_KEY)
        _seenItemGuids.value = guids
        initialGuids = guids // Store the initial "seen" list
    }

    fun refreshFeed() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _error.value = null
            try {
                val rssUrl = preferencesDataStore.settingsFlow.first().rssFeedUrl
                if (rssUrl.isNotBlank()) {
                    val items = rssReader.fetchRssItems(rssUrl)
                    // New items are those not in the initial "seen" list
                    val newItemsGuids = items.map { it.guid }.toSet() - initialGuids
                    val currentSeenGuids = _seenItemGuids.value
                    
                    _rssItems.value = items
                    
                    // Mark all current items as seen for the *next* visit
                    markAllAsSeen(items.map { it.guid })
                    
                    // But for this view, restore the original "seen" status
                    // so that only newly fetched items appear as "new"
                    _seenItemGuids.value = currentSeenGuids + newItemsGuids

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
