package com.stanelie.transmote2

import platform.Foundation.NSUserDefaults

actual class StringSetStore actual constructor(private val name: String) {
    private val defaults = NSUserDefaults.standardUserDefaults

    private fun scoped(key: String) = "$name.$key"

    actual fun getStringSet(key: String): Set<String> {
        val stored = defaults.arrayForKey(scoped(key)) ?: return emptySet()
        return stored.filterIsInstance<String>().toSet()
    }

    actual fun putStringSet(key: String, values: Set<String>) {
        defaults.setObject(values.toList(), scoped(key))
    }
}
