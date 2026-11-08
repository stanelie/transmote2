package com.stanelie.transmote2

/**
 * Bridge for the Swift BGAppRefreshTask handler.
 *
 * Kotlin suspend functions surface in Swift as completion-handler calls, so
 * Swift gets `runBackgroundCheck { posted, error in ... }` and can report
 * .newData or .noData back to iOS - which influences how often the system
 * schedules the next wake-up.
 */
suspend fun runBackgroundCheck(): Int = BackgroundChecker.runCheck()
