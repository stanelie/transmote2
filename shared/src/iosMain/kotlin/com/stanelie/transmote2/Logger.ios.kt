package com.stanelie.transmote2

import platform.Foundation.NSLog

actual object Logger {
    actual fun d(tag: String, message: String) { NSLog("D/%s: %s", tag, message) }
    actual fun e(tag: String, message: String) { NSLog("E/%s: %s", tag, message) }
}
