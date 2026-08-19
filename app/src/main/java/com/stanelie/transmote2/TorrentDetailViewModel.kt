package com.stanelie.transmote2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.URLEncoder

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
            // Initialize the client once with the current settings
            val settings = preferencesDataStore.settingsFlow.first()
            transmissionRpcClient = TransmissionRpcClient(settings)
            if (torrentId != -1) {
                fetchTorrentFiles()
            }
        }
    }

    fun fetchTorrentFiles() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val files = transmissionRpcClient?.getTorrentFiles(torrentId)
                if (files != null) {
                    _torrentFiles.value = files
                } else {
                    _errorMessage.value = "Error fetching files: RPC Client might be null or call failed."
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

    fun selectAll() {
        _selectedFileIndices.value = _torrentFiles.value.indices.toSet()
    }

    fun startSelection(fileIndex: Int) {
        _selectedFileIndices.value = setOf(fileIndex)
    }

    fun setPriorityForSelectedFiles(priority: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                transmissionRpcClient?.setFilePriorities(torrentId, _selectedFileIndices.value.toList(), priority)

                // Optimistic UI update
                val updatedFiles = _torrentFiles.value.mapIndexed { index, file ->
                    if (_selectedFileIndices.value.contains(index)) {
                        file.copy(priority = priority)
                    } else {
                        file
                    }
                }
                _torrentFiles.value = updatedFiles
                clearSelection()
            } catch (e: Exception) {
                _errorMessage.value = "Error setting file priority: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun batchRenameFiles(sortedFileIndices: List<Int>, template: String, varValuesPerFile: List<List<String>>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                sortedFileIndices.forEachIndexed { listIdx, fileIndex ->
                    val fullPath = _torrentFiles.value[fileIndex].name
                    var newName = template
                    varValuesPerFile[listIdx].forEachIndexed { varIdx, value ->
                        newName = newName.replace("{${varIdx + 1}}", value)
                    }
                    val oldName = fullPath.split("/").last()
                    if (newName != oldName) {
                        transmissionRpcClient?.renameTorrent(torrentId, fullPath, newName)
                    }
                }
                clearSelection()
                fetchTorrentFiles()
            } catch (e: Exception) {
                _errorMessage.value = "Error renaming files: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    fun renameFile(filePath: String, newName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                transmissionRpcClient?.renameTorrent(torrentId, filePath, newName)
                fetchTorrentFiles()
            } catch (e: Exception) {
                _errorMessage.value = "Error renaming file: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    suspend fun createDownloadUrl(fileName: String): String? {
        val settings = preferencesDataStore.settingsFlow.first()
        val baseUrl = settings.webDownloadUrl
        if (baseUrl.isBlank()) {
            _errorMessage.value = "Web Download URL is not configured in settings."
            return null
        }
        // Ensure base URL ends with a slash
        val correctedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        
        // Encode each path segment individually
        val encodedPath = fileName.split('/')
            .joinToString("/") { part ->
                URLEncoder.encode(part, "UTF-8").replace("+", "%20")
            }
            
        return correctedBaseUrl + encodedPath
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
