package com.stanelie.transmote2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainViewModel(private val preferencesDataStore: PreferencesDataStore) : ViewModel() {

    private val _torrents = MutableStateFlow<List<TorrentInfo>>(emptyList())
    val torrents: StateFlow<List<TorrentInfo>> = _torrents.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _isMenuVisible = MutableStateFlow(false)
    val isMenuVisible: StateFlow<Boolean> = _isMenuVisible.asStateFlow()

    private var transmissionRpcClient: TransmissionRpcClient? = null
    private var currentSettings: ConnectionSettings? = null
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            preferencesDataStore.settingsFlow.collectLatest { settings ->
                currentSettings = settings
                transmissionRpcClient = TransmissionRpcClient(settings)
                performFetch()
                startAutoRefresh(settings.refreshFrequency)
            }
        }
    }

    private fun startAutoRefresh(frequencySeconds: Int) {
        refreshJob?.cancel()

        if (frequencySeconds > 0) {
            refreshJob = viewModelScope.launch {
                while (true) {
                    delay(frequencySeconds * 1000L)
                    if (!_isMenuVisible.value) {
                        performFetch() // Call the private method
                    }
                }
            }
        }
    }

    // New public function for manual refresh
    fun manualRefresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                performFetch()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    // Renamed from fetchTorrents(), no longer manages _isRefreshing state
    private suspend fun performFetch() {
        try {
            val client = transmissionRpcClient
            if (client != null && currentSettings != null) {
                val fetchedTorrents = client.getTorrents().sortedByDescending { it.addedDate }
                val existing = _torrents.value.associateBy { it.id }
                _torrents.value = fetchedTorrents.map { new -> existing[new.id]?.takeIf { it == new } ?: new }
                _connectionStatus.value = "Connected to ${currentSettings!!.serverAddress}:${currentSettings!!.port}"
            } else {
                _connectionStatus.value = "Not configured"
                _torrents.value = emptyList()
            }
        } catch (e: Exception) {
            _connectionStatus.value = "Error: ${e.message}"
            _torrents.value = emptyList()
        }
    }

    fun onMenuVisibilityChanged(isVisible: Boolean) {
        _isMenuVisible.value = isVisible
    }

    fun startTorrent(torrentId: Int) {
        viewModelScope.launch {
            try {
                transmissionRpcClient?.startTorrents(listOf(torrentId))
                performFetch() // Refresh list after action
            } catch (e: Exception) {
                _connectionStatus.value = "Error: ${e.message}"
            }
        }
    }

    fun stopTorrent(torrentId: Int) {
        viewModelScope.launch {
            try {
                transmissionRpcClient?.stopTorrents(listOf(torrentId))
                performFetch() // Refresh list after action
            } catch (e: Exception) {
                _connectionStatus.value = "Error: ${e.message}"
            }
        }
    }

    fun removeTorrent(torrentId: Int, deleteData: Boolean) {
        viewModelScope.launch {
            try {
                transmissionRpcClient?.removeTorrents(listOf(torrentId), deleteData)
                performFetch() // Refresh list after action
            } catch (e: Exception) {
                _connectionStatus.value = "Error: ${e.message}"
            }
        }
    }

    fun renameTorrent(torrentId: Int, currentName: String, newName: String) {
        viewModelScope.launch {
            try {
                transmissionRpcClient?.renameTorrent(torrentId, currentName, newName)
                performFetch()
            } catch (e: Exception) {
                _connectionStatus.value = "Error: ${e.message}"
            }
        }
    }

    fun addTorrent(magnetOrBase64: String) {
        viewModelScope.launch {
            try {
                transmissionRpcClient?.addTorrent(magnetOrBase64)
                performFetch() // Refresh list after adding
            } catch (e: Exception) {
                _connectionStatus.value = "Error: ${e.message}"
            }
        }
    }

    companion object {
        fun provideFactory(preferencesDataStore: PreferencesDataStore): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(preferencesDataStore) as T
            }
        }
    }
}