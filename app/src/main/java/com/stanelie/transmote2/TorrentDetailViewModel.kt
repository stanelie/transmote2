package com.stanelie.transmote2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TorrentDetailViewModel(private val torrentId: Int, private val preferencesDataStore: PreferencesDataStore) : ViewModel() {

    private val _torrentFiles = MutableStateFlow<List<TorrentFile>>(emptyList())
    val torrentFiles: StateFlow<List<TorrentFile>> = _torrentFiles.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _selectedFileIndices = MutableStateFlow<Set<Int>>(emptySet())
    val selectedFileIndices: StateFlow<Set<Int>> = _selectedFileIndices.asStateFlow()

    private var transmissionRpcClient: TransmissionRpcClient? = null

    init {
        viewModelScope.launch {
            preferencesDataStore.settingsFlow.collectLatest { settings ->
                // Re-initialize client if settings change
                transmissionRpcClient = TransmissionRpcClient(settings)
                if (torrentId != -1) {
                    fetchTorrentFiles()
                }
            }
        }
    }

    fun fetchTorrentFiles() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val client = transmissionRpcClient
                if (client != null) {
                    val files = client.getTorrentFiles(torrentId)
                    _torrentFiles.value = files
                } else {
                    _errorMessage.value = "RPC Client not initialized. Check settings."
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error fetching files: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleFileSelection(fileIndex: Int) {
        _selectedFileIndices.value = if (_selectedFileIndices.value.contains(fileIndex)) {
            _selectedFileIndices.value - fileIndex
        } else {
            _selectedFileIndices.value + fileIndex
        }
    }

    fun clearSelection() {
        _selectedFileIndices.value = emptySet()
    }

    fun startSelection(fileIndex: Int) {
        _selectedFileIndices.value = setOf(fileIndex)
    }

    fun setPriorityForSelectedFiles(priority: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val client = transmissionRpcClient
                if (client != null) {
                    client.setFilePriorities(torrentId, _selectedFileIndices.value.toList(), priority)

                    // Optimistic UI update:
                    val updatedFiles = _torrentFiles.value.mapIndexed { index, file ->
                        if (_selectedFileIndices.value.contains(index)) {
                            file.copy(priority = priority)
                        } else {
                            file
                        }
                    }
                    _torrentFiles.value = updatedFiles

                    clearSelection() // Clear selection after action
                } else {
                    _errorMessage.value = "RPC Client not initialized. Check settings."
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error setting file priority: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    companion object {
        fun provideFactory(torrentId: Int, preferencesDataStore: PreferencesDataStore): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TorrentDetailViewModel(torrentId, preferencesDataStore) as T
            }
        }
    }
}