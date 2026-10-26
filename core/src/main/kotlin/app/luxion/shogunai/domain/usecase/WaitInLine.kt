package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.DeviceWaitQueue
import app.luxion.shogunai.domain.io.EnvironmentStateStore
import app.luxion.shogunai.domain.io.ProcessLiveness
import app.luxion.shogunai.domain.model.WaitQueueName
import app.luxion.shogunai.domain.model.WaitTicket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.util.UUID

internal data class Waited<T>(val value: T, val waitedSeconds: Long)

internal data class InLine<B>(val blocked: B, val now: Instant, val position: Int, val waiting: Int)

internal class WaitInLine(
    private val stateStore: EnvironmentStateStore,
    private val waitQueue: DeviceWaitQueue,
    private val liveness: ProcessLiveness,
    private val clock: () -> Instant,
    private val sleep: suspend (Duration) -> Unit,
) {
    fun liveQueue(queue: WaitQueueName): List<WaitTicket> {
        val tickets = waitQueue.read(queue)
        val live = tickets.filter { liveness.isAlive(it.process) }
        if (live.size != tickets.size) waitQueue.write(queue, live)
        return live
    }

    suspend fun waiting(queue: WaitQueueName): List<WaitTicket> = locked { liveQueue(queue) }

    suspend fun drop(queue: WaitQueueName, adminDirectory: String) = locked {
        val tickets = waitQueue.read(queue)
        val kept = tickets.filterNot { normalizePath(it.adminDirectory) == normalizePath(adminDirectory) }
        if (kept.size != tickets.size) waitQueue.write(queue, kept)
    }

    suspend fun <T, B : Any> await(
        queue: WaitQueueName,
        adminDirectory: String,
        worktreePath: String,
        timeout: Duration,
        attempt: suspend (ticketId: String?) -> Result<T>,
        blockedBy: (Throwable) -> B?,
        onWaiting: (InLine<B>) -> Instant?,
        timedOut: (waited: Duration, blocked: B) -> Throwable,
    ): Waited<T> {
        val started = clock()
        val first = attempt(null)
        if (first.exceptionOrNull()?.let(blockedBy) == null) return Waited(first.getOrThrow(), 0)
        val ticket = WaitTicket(
            UUID.randomUUID().toString(),
            normalizePath(adminDirectory),
            normalizePath(worktreePath),
            liveness.current(),
            started.toEpochMilli(),
        )
        locked { waitQueue.write(queue, liveQueue(queue) + ticket) }
        try {
            val deadline = started + timeout
            while (true) {
                val result = attempt(ticket.id)
                val blocked = result.exceptionOrNull()?.let(blockedBy)
                    ?: return Waited(result.getOrThrow(), Duration.between(started, clock()).seconds)
                val now = clock()
                if (!now.isBefore(deadline)) throw timedOut(Duration.between(started, now), blocked)
                val tickets = locked { liveQueue(queue) }
                val nextExpiry = onWaiting(InLine(blocked, now, tickets.indexOfFirst { it.id == ticket.id } + 1, tickets.size))
                sleep(nextPause(now, deadline, nextExpiry))
            }
        } finally {
            withContext(NonCancellable) {
                locked { waitQueue.write(queue, waitQueue.read(queue).filterNot { it.id == ticket.id }) }
            }
        }
    }

    private fun nextPause(now: Instant, deadline: Instant, nextExpiry: Instant?): Duration =
        listOfNotNull(WAIT_POLL_INTERVAL, nextExpiry?.let { Duration.between(now, it) + EXPIRY_MARGIN }, Duration.between(now, deadline))
            .min()
            .coerceAtLeast(MINIMUM_WAIT_PAUSE)

    private suspend fun <T> locked(block: () -> T): T = withContext(Dispatchers.IO) { stateStore.withLock(block) }

    companion object {
        val WAIT_POLL_INTERVAL: Duration = Duration.ofSeconds(10)
        private val EXPIRY_MARGIN: Duration = Duration.ofSeconds(1)
        private val MINIMUM_WAIT_PAUSE: Duration = Duration.ofSeconds(1)
    }
}

internal fun List<WaitTicket>.othersThan(adminDirectory: String): List<WaitTicket> =
    filter { normalizePath(it.adminDirectory) != normalizePath(adminDirectory) }

internal fun List<WaitTicket>.othersAheadOf(ticketId: String?, adminDirectory: String): Boolean =
    takeWhile { it.id != ticketId }.othersThan(adminDirectory).isNotEmpty()
