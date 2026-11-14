package app.luxion.shogunai.ui.worktree

import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class DeviceRefreshSchedule(private val clock: () -> Instant) {
    private val lastRefreshes = mutableMapOf<String, Instant>()

    fun isDue(projectId: String, interval: Duration): Boolean {
        val now = clock()
        val last = lastRefreshes[projectId]
        if (last != null && (now.toEpochMilli() - last.toEpochMilli()).milliseconds < interval) return false
        lastRefreshes[projectId] = now
        return true
    }

    fun forget(projectId: String) {
        lastRefreshes.remove(projectId)
    }
}
