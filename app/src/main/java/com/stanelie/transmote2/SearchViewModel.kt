package com.stanelie.transmote2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {

    private val apiClient = LeetxApiClient()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedCategory = MutableStateFlow("Movies")
    val selectedCategory: StateFlow<String> = _selectedCategory

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val categories = listOf("Movies", "TV", "Games", "Music", "Apps", "Anime", "Documentaries", "Other")

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelected(category: String) {
        _selectedCategory.value = category
    }

    fun executeSearch() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _searchResults.value = apiClient.search(_searchQuery.value, _selectedCategory.value)
            } catch (e: Exception) {
                _error.value = "Search failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getMagnetLink(detailsUrl: String, onMagnetLinkReady: (String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val details = apiClient.getTorrentDetails(detailsUrl)
                details?.magnet?.let {
                    onMagnetLinkReady(it)
                } ?: run {
                    _error.value = "Could not retrieve magnet link."
                }
            } catch (e: Exception) {
                _error.value = "Failed to fetch torrent details: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
