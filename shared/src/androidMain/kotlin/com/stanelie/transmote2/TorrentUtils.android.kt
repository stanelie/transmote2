package com.stanelie.transmote2

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp * 1000))
}
