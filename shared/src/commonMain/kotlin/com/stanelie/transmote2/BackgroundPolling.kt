package com.stanelie.transmote2

/**
 * Hook for periodic background polling of the RSS feed.
 *
 * Android registers a WorkManager-backed implementation from :app, which is
 * where the worker, notifications and boot receiver live. iOS registers
 * nothing yet: it has no equivalent of a guaranteed periodic job, and the
 * closest thing (BGAppRefreshTask) is opportunistic, so this needs a
 * different design rather than a port.
 */
object BackgroundPolling {
    private var scheduler: ((intervalHours: Int) -> Unit)? = null

    fun register(scheduler: (intervalHours: Int) -> Unit) {
        this.scheduler = scheduler
    }

    fun schedule(intervalHours: Int) {
        scheduler?.invoke(intervalHours)
    }
}
