package com.stanelie.transmote2

import android.content.Context
import java.io.File

/**
 * Shared code has no Context, so the Application hands one over once at startup.
 */
object AndroidApp {
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val context: Context
        get() = requireNotNull(appContext) {
            "AndroidApp.init() must be called from Application.onCreate()"
        }
}

// Same path the preferencesDataStore(name = "settings") delegate used, so
// existing installs keep their settings.
actual fun settingsFilePath(): String =
    File(AndroidApp.context.filesDir, "datastore/settings.preferences_pb").absolutePath
