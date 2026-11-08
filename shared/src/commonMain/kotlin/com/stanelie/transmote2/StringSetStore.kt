package com.stanelie.transmote2

/**
 * Small string-set store for the RSS seen/notified GUID lists.
 *
 * Deliberately NOT DataStore: on Android this must keep reading and writing
 * the existing `rss_prefs` SharedPreferences file, or every already-seen RSS
 * item would light up as new on first launch after the port.
 */
expect class StringSetStore(name: String) {
    fun getStringSet(key: String): Set<String>
    fun putStringSet(key: String, values: Set<String>)
}
