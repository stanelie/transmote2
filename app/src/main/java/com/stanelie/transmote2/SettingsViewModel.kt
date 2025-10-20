package com.stanelie.transmote2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val preferencesDataStore: PreferencesDataStore) : ViewModel() {

    private val _serverAddress = MutableStateFlow("")
    val serverAddress: StateFlow<String> = _serverAddress.asStateFlow()

    private val _port = MutableStateFlow("")
    val port: StateFlow<String> = _port.asStateFlow()

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _useSsl = MutableStateFlow(false)
    val useSsl: StateFlow<Boolean> = _useSsl.asStateFlow()

    private val _refreshFrequency = MutableStateFlow("")
    val refreshFrequency: StateFlow<String> = _refreshFrequency.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            preferencesDataStore.settingsFlow.collect {
                _serverAddress.value = it.serverAddress
                _port.value = it.port
                _username.value = it.username
                _password.value = it.password
                _useSsl.value = it.useSsl
                _refreshFrequency.value = it.refreshFrequency.toString()
            }
        }
    }

    fun updateServerAddress(address: String) {
        _serverAddress.value = address
    }

    fun updatePort(p: String) {
        _port.value = p
    }

    fun updateUsername(user: String) {
        _username.value = user
    }

    fun updatePassword(pass: String) {
        _password.value = pass
    }

    fun updateUseSsl(ssl: Boolean) {
        _useSsl.value = ssl
    }

    fun updateRefreshFrequency(frequency: String) {
        _refreshFrequency.value = frequency
    }

    fun saveSettings() {
        viewModelScope.launch {
            preferencesDataStore.saveSettings(
                serverAddress = _serverAddress.value,
                port = _port.value,
                username = _username.value,
                password = _password.value,
                useSsl = _useSsl.value,
                refreshFrequency = _refreshFrequency.value.toIntOrNull() ?: 30 // Default to 30 seconds if invalid
            )
        }
    }

    companion object {
        fun provideFactory(preferencesDataStore: PreferencesDataStore): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(preferencesDataStore) as T
            }
        }
    }
}