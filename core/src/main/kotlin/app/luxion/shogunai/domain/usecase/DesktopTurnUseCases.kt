package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.DesktopTurnStore
import app.luxion.shogunai.domain.io.DeviceWaitQueue
import app.luxion.shogunai.domain.io.EnvironmentStateStore
import app.luxion.shogunai.domain.io.ProcessLiveness
import app.luxion.shogunai.domain.model.DesktopTurn
import app.luxion.shogunai.domain.model.DesktopTurnAcquisition
import app.luxion.shogunai.domain.model.DesktopTurnRelease
import app.luxion.shogunai.domain.model.DesktopTurnState
import app.luxion.shogunai.domain.model.DesktopWait
import app.luxion.shogunai.domain.model.DesktopWaitProgress
import app.luxion.shogunai.domain.model.WaitQueueName
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

class DesktopTurnLedger(
    private val store: DesktopTurnStore,
    private val stateStore: EnvironmentStateStore,
    private val waitQueue: DeviceWaitQueue,
    private val liveness: ProcessLiveness,
    private val adminDirectoryOf: suspend (worktreePath: String) -> String,
    private val clock: () -> Instant = Instant::now,
    private val sleep: suspend (Duration) -> Unit = { delay(it.toMillis()) },
) {
    internal val inLine = WaitInLine(stateStore, waitQueue, liveness, clock, sleep)

    internal fun now(): Instant = clock()

    internal suspend fun adminOf(worktreePath: String): String = normalizePath(adminDirectoryOf(worktreePath))

    internal fun currentTurn(now: Instant = clock()): DesktopTurn? = store.read()?.takeUnless { it.isExpiredAt(now) }

    internal fun write(turn: DesktopTurn?) = store.write(turn)

    internal suspend fun <T> locked(block: () -> T): T = withContext(Dispatchers.IO) { stateStore.withLock(block) }
}

class AcquireDesktopTurnUseCase(
    private val ledger: DesktopTurnLedger,
    private val duration: () -> Duration,
) {
    suspend operator fun invoke(worktreePath: String, wait: DesktopWait? = null): Result<DesktopTurnAcquisition> = runCatching {
        val admin = ledger.adminOf(worktreePath)
        val worktree = normalizePath(worktreePath)
        if (wait == null) return@runCatching attempt(admin, worktree, ticketId = null).getOrThrow()
        val waited = ledger.inLine.await(
            queue = WaitQueueName.DESKTOP,
            adminDirectory = admin,
            worktreePath = worktree,
            timeout = wait.timeout,
            attempt = { ticketId -> attempt(admin, worktree, ticketId) },
            blockedBy = { it as? WorktreeError.DesktopTurnTaken },
            onWaiting = { line ->
                val holder = line.blocked.holder
                wait.onProgress(DesktopWaitProgress(line.position, line.waiting, holder))
                holder?.let { Instant.ofEpochMilli(it.expiresAt) }
            },
            timedOut = { waitedFor, taken -> WorktreeError.DesktopTurnWaitTimedOut(waitedFor, taken.holder) },
        )
        waited.value.copy(waitedSeconds = waited.waitedSeconds)
    }

    private suspend fun attempt(admin: String, worktree: String, ticketId: String?): Result<DesktopTurnAcquisition> = runCatching {
        ledger.locked {
            val now = ledger.now()
            val current = ledger.currentTurn(now)
            val queue = ledger.inLine.liveQueue(WaitQueueName.DESKTOP)
            val expiresAt = (now + duration()).toEpochMilli()
            when {
                current != null && normalizePath(current.adminDirectory) == admin -> {
                    val renewed = current.copy(expiresAt = expiresAt)
                    ledger.write(renewed)
                    DesktopTurnAcquisition(renewed, renewed = true, waitedSeconds = null, waiting = queue.othersThan(admin))
                }
                current != null -> throw WorktreeError.DesktopTurnTaken(current, queue.othersThan(admin).size)
                queue.othersAheadOf(ticketId, admin) -> throw WorktreeError.DesktopTurnTaken(null, queue.othersThan(admin).size)
                else -> {
                    val turn = DesktopTurn(admin, worktree, now.toEpochMilli(), expiresAt)
                    ledger.write(turn)
                    DesktopTurnAcquisition(turn, renewed = false, waitedSeconds = null, waiting = queue.othersThan(admin))
                }
            }
        }
    }
}

class ReleaseDesktopTurnUseCase(private val ledger: DesktopTurnLedger) {

    suspend operator fun invoke(worktreePath: String): Result<DesktopTurnRelease> = runCatching {
        val admin = ledger.adminOf(worktreePath)
        ledger.locked {
            val current = ledger.currentTurn()
            when {
                current == null -> DesktopTurnRelease.NobodyHolds
                normalizePath(current.adminDirectory) != admin -> throw WorktreeError.DesktopTurnHeldByOther(current)
                else -> release(current)
            }
        }
    }

    suspend fun ifHeldBy(worktreePath: String): Result<DesktopTurnRelease> = runCatching {
        val admin = ledger.adminOf(worktreePath)
        ledger.locked { releaseIfHeldBy(admin) }
    }

    suspend fun force(): Result<DesktopTurnRelease> = runCatching {
        ledger.locked { ledger.currentTurn()?.let(::release) ?: DesktopTurnRelease.NobodyHolds }
    }

    suspend fun onWorktreeRemoved(worktreePath: String) {
        val admin = runCatching { ledger.adminOf(worktreePath) }.getOrNull() ?: return
        runCatching {
            ledger.locked { releaseIfHeldBy(admin) }
            ledger.inLine.drop(WaitQueueName.DESKTOP, admin)
        }
    }

    private fun releaseIfHeldBy(admin: String): DesktopTurnRelease {
        val current = ledger.currentTurn()
        return if (current != null && normalizePath(current.adminDirectory) == admin) release(current) else DesktopTurnRelease.NobodyHolds
    }

    private fun release(turn: DesktopTurn): DesktopTurnRelease {
        ledger.write(null)
        return DesktopTurnRelease.Released(turn)
    }
}

class ReadDesktopTurnUseCase(private val ledger: DesktopTurnLedger) {

    suspend operator fun invoke(): Result<DesktopTurnState> = runCatching {
        ledger.locked { DesktopTurnState(ledger.currentTurn(), ledger.inLine.liveQueue(WaitQueueName.DESKTOP)) }
    }
}
