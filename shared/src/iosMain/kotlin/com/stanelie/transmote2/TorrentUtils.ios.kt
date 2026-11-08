package com.stanelie.transmote2

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970

actual fun formatTimestamp(timestamp: Long): String {
    val formatter = NSDateFormatter().apply { dateFormat = "MMM dd, yyyy HH:mm" }
    return formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(timestamp.toDouble()))
}
