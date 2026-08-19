package com.stanelie.transmote2

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

fun getTorrentStatusString(status: Int): String {
    return when (status) {
        0 -> "Stopped"
        1 -> "Check Waiting"
        2 -> "Checking"
        3 -> "Download Waiting"
        4 -> "Downloading"
        5 -> "Seed Waiting"
        6 -> "Seeding"
        else -> "Unknown"
    }
}

fun formatBytes(bytes: Long, showDecimals: Boolean = true): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
    val pattern = if (showDecimals) "#,##0.#" else "#,##0"
    return DecimalFormat(pattern).format(bytes / 1024.0.pow(digitGroups)) + " " + units[digitGroups]
}

fun formatEta(eta: Long): String {
    if (eta < 0) return "Unknown"
    if (eta == 0L) return "Done"
    val hours = eta / 3600
    val minutes = (eta % 3600) / 60
    val seconds = eta % 60

    return when {
        hours > 0 -> String.format("%dh %dm %ds", hours, minutes, seconds)
        minutes > 0 -> String.format("%dm %ds", minutes, seconds)
        else -> String.format("%ds", seconds)
    }
}

fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp * 1000) // Transmission RPC returns Unix timestamp in seconds
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(date)
}
