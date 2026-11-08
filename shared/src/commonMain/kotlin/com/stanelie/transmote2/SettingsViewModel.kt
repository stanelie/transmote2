package com.stanelie.transmote2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferencesDataStore: PreferencesDataStore
) : ViewModel() {

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

    private val _rssFeedUrl = MutableStateFlow("")
    val rssFeedUrl: StateFlow<String> = _rssFeedUrl.asStateFlow()

    private val _webDownloadUrl = MutableStateFlow("")
    val webDownloadUrl: StateFlow<String> = _webDownloadUrl.asStateFlow()

    private val _rssPollingIntervalHours = MutableStateFlow("")
    val rssPollingIntervalHours: StateFlow<String> = _rssPollingIntervalHours.asStateFlow()

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
                _rssFeedUrl.value = it.rssFeedUrl
                _webDownloadUrl.value = it.webDownloadUrl
                _rssPollingIntervalHours.value = it.rssPollingIntervalHours.toString()
            }
        }
    }

    fun updateServerAddress(address: String) { _serverAddress.value = address }
    fun updatePort(p: String) { _port.value = p }
    fun updateUsername(user: String) { _username.value = user }
    fun updatePassword(pass: String) { _password.value = pass }
    fun updateUseSsl(ssl: Boolean) { _useSsl.value = ssl }
    fun updateRefreshFrequency(frequency: String) { _refreshFrequency.value = frequency }
    fun updateRssFeedUrl(url: String) { _rssFeedUrl.value = url }
    fun updateWebDownloadUrl(url: String) { _webDownloadUrl.value = url }
    fun updateRssPollingIntervalHours(hours: String) { _rssPollingIntervalHours.value = hours }

    fun saveSettings() {
        viewModelScope.launch {
            val intervalHours = _rssPollingIntervalHours.value.toIntOrNull() ?: 6
            preferencesDataStore.saveSettings(
                serverAddress = _serverAddress.value,
                port = _port.value,
                username = _username.value,
                password = _password.value,
                useSsl = _useSsl.value,
                refreshFrequency = _refreshFrequency.value.toIntOrNull() ?: 30,
                rssFeedUrl = _rssFeedUrl.value,
                webDownloadUrl = _webDownloadUrl.value,
                rssPollingIntervalHours = intervalHours
            )
            BackgroundPolling.schedule(intervalHours)
        }
    }

    companion object {
        fun provideFactory(preferencesDataStore: PreferencesDataStore) = viewModelFactory {
            initializer { SettingsViewModel(preferencesDataStore) }
        }
    }
}
