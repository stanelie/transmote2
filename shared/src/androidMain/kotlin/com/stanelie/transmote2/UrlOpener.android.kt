package com.stanelie.transmote2

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri

actual fun openUrlExternally(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        // Launched from application context rather than an Activity.
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        AndroidApp.context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // A malformed or schemeless URL matches no browser. Log rather than
        // take the whole app down.
        Logger.e("UrlOpener", "No activity could open [$url]: ${e.message}")
    }
}
