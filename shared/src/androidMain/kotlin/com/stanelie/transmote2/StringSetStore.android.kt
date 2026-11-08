package com.stanelie.transmote2

import android.content.Context

actual class StringSetStore actual constructor(name: String) {
    private val prefs =
        AndroidApp.context.getSharedPreferences(name, Context.MODE_PRIVATE)

    actual fun getStringSet(key: String): Set<String> =
        prefs.getStringSet(key, emptySet()) ?: emptySet()

    actual fun putStringSet(key: String, values: Set<String>) {
        prefs.edit().putStringSet(key, values).apply()
    }
}
