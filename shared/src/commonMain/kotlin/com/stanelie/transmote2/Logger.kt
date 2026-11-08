package com.stanelie.transmote2

/** Logging seam so shared code need not depend on android.util.Log. */
expect object Logger {
    fun d(tag: String, message: String)
    fun e(tag: String, message: String)
}
