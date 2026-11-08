package com.stanelie.transmote2

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

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

/** 1234567 -> "1,234,567". Replaces the grouping half of DecimalFormat. */
private fun groupThousands(whole: Long): String {
    val digits = abs(whole).toString()
    val sb = StringBuilder()
    for ((i, c) in digits.withIndex()) {
        if (i > 0 && (digits.length - i) % 3 == 0) sb.append(',')
        sb.append(c)
    }
    if (whole < 0) sb.insert(0, '-')
    return sb.toString()
}

/** Stands in for the DecimalFormat patterns "#,##0.#" and "#,##0". */
private fun formatScaled(value: Double, showDecimals: Boolean): String {
    if (!showDecimals) return groupThousands(value.roundToLong())
    val tenths = (value * 10).roundToLong()
    val whole = tenths / 10
    val frac = abs(tenths % 10)
    return if (frac == 0L) groupThousands(whole) else "${groupThousands(whole)}.$frac"
}

fun formatBytes(bytes: Long, showDecimals: Boolean = true): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
    return formatScaled(bytes / 1024.0.pow(digitGroups), showDecimals) + " " + units[digitGroups]
}

fun formatEta(eta: Long): String {
    if (eta < 0) return "Unknown"
    if (eta == 0L) return "Done"
    val hours = eta / 3600
    val minutes = (eta % 3600) / 60
    val seconds = eta % 60

    return when {
        hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

/**
 * Left per-platform so each keeps its own locale-aware formatter, rather than
 * hardcoding English month names in common code.
 * [timestamp] is Unix seconds, as Transmission RPC returns it.
 */
expect fun formatTimestamp(timestamp: Long): String
