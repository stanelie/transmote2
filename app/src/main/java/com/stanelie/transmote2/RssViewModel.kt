package com.stanelie.transmote2

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RssViewModel(
    private val preferencesDataStore: PreferencesDataStore,
    private val rssReader: RssReader,
    private val appContext: Context
) : ViewModel() {

    private val _rssItems = MutableStateFlow<List<RssItem>>(emptyList())
    val rssItems: StateFlow<List<RssItem>> = _rssItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _seenItemGuids = MutableStateFlow<Set<String>>(emptySet())
    val seenItemGuids: StateFlow<Set<String>> = _seenItemGuids.asStateFlow()

    private val sharedPreferences = appContext.getSharedPreferences("rss_prefs", Context.MODE_PRIVATE)

    init {
        loadSeenItems()
        fetchRssFeed()
    }

    private fun loadSeenItems() {
        _seenItemGuids.value = sharedPreferences.getStringSet("seen_guids", emptySet()) ?: emptySet()
    }

    fun fetchRssFeed() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val rssUrl = preferencesDataStore.settingsFlow.first().rssFeedUrl
                if (rssUrl.isNotBlank()) {
                    val items = rssReader.fetchRssItems(rssUrl)
                    _rssItems.value = items
                    // After fetching, mark all current items as seen
                    markAllAsSeen(items.map { it.guid })
                } else {
                    _error.value = "RSS Feed URL is not configured in settings."
                }
            } catch (e: Exception) {
                _error.value = "Failed to fetch RSS feed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun markAllAsSeen(guids: List<String>) {
        val newSeenGuids = _seenItemGuids.value + guids
        _seenItemGuids.value = newSeenGuids
        sharedPreferences.edit().putStringSet("seen_guids", newSeenGuids).apply()
    }

    companion object {
        fun provideFactory(
            preferencesDataStore: PreferencesDataStore,
            rssReader: RssReader,
            appContext: Context
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return RssViewModel(preferencesDataStore, rssReader, appContext) as T
            }
        }
    }
}
