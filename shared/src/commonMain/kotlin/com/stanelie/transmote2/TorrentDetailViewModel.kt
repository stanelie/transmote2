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

    private suspend fun rpcClient(): TransmissionRpcClient {
        transmissionRpcClient?.let { return it }
        val client = TransmissionRpcClient(preferencesDataStore.settingsFlow.first())
        transmissionRpcClient = client
        return client
    }

    /**
     * Called every time the screen comes to the foreground. File priorities can be changed by
     * other Transmission clients at any time, so they are always re-read from the server. Once
     * a list is on screen the refresh is silent, so the list is not replaced by a spinner.
     */
    fun refreshFiles() {
        fetchTorrentFiles(silent = _torrentFiles.value.isNotEmpty())
    }

    fun fetchTorrentFiles(silent: Boolean = false) {
        if (torrentId == -1) return
        viewModelScope.launch {
            if (!silent) {
                _isLoading.value = true
                _errorMessage.value = null
            }
            try {
                _torrentFiles.value = rpcClient().getTorrentFiles(torrentId)
                _errorMessage.value = null
            } catch (e: Exception) {
                // A failed background refresh keeps showing the last known list.
                if (!silent) _errorMessage.value = "Error fetching files: ${e.message}"
            } finally {
                if (!silent) _isLoading.value = false
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

    /** Selects every file in a folder, or deselects them all if they are already all selected. */
    fun toggleFolderSelection(fileIndices: List<Int>) {
        if (fileIndices.isEmpty()) return
        _selectedFileIndices.value = if (_selectedFileIndices.value.containsAll(fileIndices)) {
            _selectedFileIndices.value - fileIndices.toSet()
        } else {
            _selectedFileIndices.value + fileIndices
        }
    }

    fun startSelection(fileIndex: Int) {
        _selectedFileIndices.value = setOf(fileIndex)
    }

    fun setPriorityForSelectedFiles(priority: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                rpcClient().setFilePriorities(torrentId, _selectedFileIndices.value.toList(), priority)

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
                // Confirm against what the server actually holds.
                fetchTorrentFiles(silent = true)
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
                        rpcClient().renameTorrent(torrentId, fullPath, newName)
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
                rpcClient().renameTorrent(torrentId, filePath, newName)
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
        var correctedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        // A base URL saved without a scheme ("www.example.com/files") parses as a
        // relative URI, which no browser will handle - ACTION_VIEW then throws
        // ActivityNotFoundException. Default it to https.
        if (!correctedBaseUrl.contains("://")) {
            correctedBaseUrl = "https://$correctedBaseUrl"
        }
        
        // Encode each path segment individually
        val encodedPath = fileName.split('/')
            .joinToString("/") { part ->
                encodeUrlPathSegment(part)
            }
            
        return correctedBaseUrl + encodedPath
    }


    companion object {
        fun provideFactory(torrentId: Int, preferencesDataStore: PreferencesDataStore) = viewModelFactory {
            initializer { TorrentDetailViewModel(torrentId, preferencesDataStore) }
        }
    }
}
