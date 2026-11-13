package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.BuildTurnStore
import app.luxion.shogunai.domain.io.DeviceWaitQueue
import app.luxion.shogunai.domain.io.EnvironmentStateStore
import app.luxion.shogunai.domain.io.ForegroundCommandRunner
import app.luxion.shogunai.domain.io.ProcessLiveness
import app.luxion.shogunai.domain.model.BuildSlot
import app.luxion.shogunai.domain.model.BuildSlotAcquisition
import app.luxion.shogunai.domain.model.BuildTurnError
import app.luxion.shogunai.domain.model.BuildTurnState
import app.luxion.shogunai.domain.model.BuildWait
import app.luxion.shogunai.domain.model.BuildWaitProgress
import app.luxion.shogunai.domain.model.WaitQueueName
import app.luxion.shogunai.domain.model.normalizePath
import app.luxion.shogunai.domain.model.runSuspendCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.util.UUID

class BuildTurnLedger(
    private val store: BuildTurnStore,
    private val stateStore: EnvironmentStateStore,
    waitQueue: DeviceWaitQueue,
    private val liveness: ProcessLiveness,
    private val adminDirectoryOf: suspend (worktreePath: String) -> String,
    internal val limit: () -> Int?,
    private val clock: () -> Instant,
    sleep: suspend (Duration) -> Unit,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    internal val inLine = WaitInLine(stateStore, waitQueue, liveness, clock, sleep)

    internal suspend fun adminOf(worktreePath: String): String = normalizePath(adminDirectoryOf(worktreePath))

    internal fun liveSlots(): List<BuildSlot> {
        val slots = store.read()
        val live = slots.filter { liveness.isAlive(it.process) }
        if (live.size != slots.size) store.write(live)
        return live
    }

    internal fun add(admin: String, worktree: String, command: List<String>): BuildSlot {
        val slot = BuildSlot(newId(), admin, worktree, command.joinToString(" "), clock().toEpochMilli(), liveness.current())
        store.write(liveSlots() + slot)
        return slot
    }

    internal fun remove(slotId: String) {
        val slots = store.read()
        val kept = slots.filterNot { it.id == slotId }
        if (kept.size != slots.size) store.write(kept)
    }

    internal suspend fun <T> locked(block: () -> T): T = withContext(Dispatchers.IO) { stateStore.withLock(block) }
}

class AcquireBuildSlotUseCase(private val ledger: BuildTurnLedger) {

    suspend operator fun invoke(worktreePath: String, command: List<String>, wait: BuildWait = BuildWait()): Result<BuildSlotAcquisition> =
        runSuspendCatching {
            val admin = ledger.adminOf(worktreePath)
            val worktree = normalizePath(worktreePath)
            val waited = ledger.inLine.await(
                queue = WaitQueueName.BUILD,
                adminDirectory = admin,
                worktreePath = worktree,
                timeout = wait.timeout,
                attempt = { ticketId -> attempt(admin, worktree, command, ticketId) },
                blockedBy = { it as? BuildTurnError.SlotsTaken },
                onWaiting = { line ->
                    wait.onProgress(BuildWaitProgress(line.position, line.waiting, line.blocked.limit, line.blocked.running))
                    null
                },
                timedOut = { waitedFor, taken -> BuildTurnError.WaitTimedOut(waitedFor, taken.limit, taken.running) },
            )
            BuildSlotAcquisition(waited.value, waited.waitedSeconds)
        }

    private suspend fun attempt(admin: String, worktree: String, command: List<String>, ticketId: String?): Result<BuildSlot> =
        runSuspendCatching {
            ledger.locked {
                val limit = ledger.limit()
                val running = ledger.liveSlots()
                val queue = ledger.inLine.liveQueue(WaitQueueName.BUILD)
                if (limit != null && (running.size >= limit || queue.othersAheadOf(ticketId, admin))) {
                    throw BuildTurnError.SlotsTaken(limit, running, queue.othersThan(admin).size)
                }
                ledger.add(admin, worktree, command)
            }
        }
}

class ReleaseBuildSlotUseCase(private val ledger: BuildTurnLedger) {

    suspend operator fun invoke(slotId: String): Result<Unit> = runSuspendCatching { ledger.locked { ledger.remove(slotId) } }
}

class ReadBuildTurnUseCase(private val ledger: BuildTurnLedger) {

    suspend operator fun invoke(): Result<BuildTurnState> = runSuspendCatching {
        ledger.locked { BuildTurnState(ledger.limit(), ledger.liveSlots(), ledger.inLine.liveQueue(WaitQueueName.BUILD)) }
    }
}

data class BuildRun(val exitCode: Int?, val waitedSeconds: Long)

class RunInBuildTurnUseCase(
    private val acquire: AcquireBuildSlotUseCase,
    private val release: ReleaseBuildSlotUseCase,
    private val runner: ForegroundCommandRunner,
) {
    suspend operator fun invoke(
        worktreePath: String,
        workingDirectory: String,
        command: List<String>,
        wait: BuildWait = BuildWait(),
    ): Result<BuildRun> = runSuspendCatching {
        val acquisition = acquire(worktreePath, command, wait).getOrThrow()
        try {
            BuildRun(runner.run(command, workingDirectory), acquisition.waitedSeconds)
        } finally {
            withContext(NonCancellable) { release(acquisition.slot.id) }
        }
    }
}
