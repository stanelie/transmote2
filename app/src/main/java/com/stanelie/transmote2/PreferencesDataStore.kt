package com.stanelie.transmote2

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class ConnectionSettings(
    val serverAddress: String = "",
    val port: String = "",
    val username: String = "",
    val password: String = "",
    val useSsl: Boolean = false,
    val refreshFrequency: Int = 30,
    val rssFeedUrl: String = "",
    val webDownloadUrl: String = "" // Added Web Download URL
)

class PreferencesDataStore(private val context: Context) {

    private object PreferencesKeys {
        val SERVER_ADDRESS = stringPreferencesKey("server_address")
        val PORT = stringPreferencesKey("port")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val USE_SSL = booleanPreferencesKey("use_ssl")
        val REFRESH_FREQUENCY = intPreferencesKey("refresh_frequency")
        val RSS_FEED_URL = stringPreferencesKey("rss_feed_url")
        val WEB_DOWNLOAD_URL = stringPreferencesKey("web_download_url") // Added key
    }

    val settingsFlow: Flow<ConnectionSettings> = context.dataStore.data
        .map { preferences ->
            ConnectionSettings(
                serverAddress = preferences[PreferencesKeys.SERVER_ADDRESS] ?: "",
                port = preferences[PreferencesKeys.PORT] ?: "",
                username = preferences[PreferencesKeys.USERNAME] ?: "",
                password = preferences[PreferencesKeys.PASSWORD] ?: "",
                useSsl = preferences[PreferencesKeys.USE_SSL] ?: false,
                refreshFrequency = preferences[PreferencesKeys.REFRESH_FREQUENCY] ?: 30,
                rssFeedUrl = preferences[PreferencesKeys.RSS_FEED_URL] ?: "",
                webDownloadUrl = preferences[PreferencesKeys.WEB_DOWNLOAD_URL] ?: ""
            )
        }

    suspend fun saveSettings(
        serverAddress: String,
        port: String,
        username: String,
        password: String,
        useSsl: Boolean,
        refreshFrequency: Int,
        rssFeedUrl: String,
        webDownloadUrl: String
    ) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SERVER_ADDRESS] = serverAddress
            preferences[PreferencesKeys.PORT] = port
            preferences[PreferencesKeys.USERNAME] = username
            preferences[PreferencesKeys.PASSWORD] = password
            preferences[PreferencesKeys.USE_SSL] = useSsl
            preferences[PreferencesKeys.REFRESH_FREQUENCY] = refreshFrequency
            preferences[PreferencesKeys.RSS_FEED_URL] = rssFeedUrl
            preferences[PreferencesKeys.WEB_DOWNLOAD_URL] = webDownloadUrl
        }
    }
}
