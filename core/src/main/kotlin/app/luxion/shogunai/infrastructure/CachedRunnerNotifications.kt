package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.RunnerNotificationSource
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.RunnerNotifications

class CachedRunnerNotifications(
    private val herdrNotifiesOnDesktop: () -> Boolean,
    private val orcaNotifiedStates: suspend () -> Set<AgentState>,
    private val now: () -> Long = System::currentTimeMillis,
) : RunnerNotificationSource {
    private var cached: RunnerNotifications? = null
    private var readAt = 0L

    override suspend fun read(): RunnerNotifications {
        val current = cached
        if (current != null && now() - readAt < CACHE_MILLIS) return current
        val herdr = if (herdrNotifiesOnDesktop()) RunnerNotifications.STOPPED_STATES else emptySet()
        return RunnerNotifications(herdr = herdr, orca = orcaNotifiedStates()).also {
            cached = it
            readAt = now()
        }
    }

    private companion object {
        const val CACHE_MILLIS = 60_000L
    }
}
