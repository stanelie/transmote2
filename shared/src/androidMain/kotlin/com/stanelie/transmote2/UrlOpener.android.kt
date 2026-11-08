package com.stanelie.transmote2

import android.content.Intent
import android.net.Uri

actual fun openUrlExternally(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        // Launched from application context rather than an Activity.
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    AndroidApp.context.startActivity(intent)
}
