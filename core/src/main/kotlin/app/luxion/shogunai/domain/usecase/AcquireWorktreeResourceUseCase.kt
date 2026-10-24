package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.AndroidSdk
import app.luxion.shogunai.domain.io.AppleSimulators
import app.luxion.shogunai.domain.io.DeviceWaitQueue
import app.luxion.shogunai.domain.io.EnvironmentStateStore
import app.luxion.shogunai.domain.io.PortProbe
import app.luxion.shogunai.domain.io.ProcessLiveness
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.DeviceWait
import app.luxion.shogunai.domain.model.DeviceWaitProgress
import app.luxion.shogunai.domain.model.EMULATOR_PORT_KEY
import app.luxion.shogunai.domain.model.FIRST_EMULATOR_PORT
import app.luxion.shogunai.domain.model.IOS_SIMULATOR_KEY
import app.luxion.shogunai.domain.model.LAST_EMULATOR_PORT
import app.luxion.shogunai.domain.model.PoolHolder
import app.luxion.shogunai.domain.model.PoolRelease
import app.luxion.shogunai.domain.model.PoolHolderState
import app.luxion.shogunai.domain.model.PoolReleaseReason
import app.luxion.shogunai.domain.model.ReleaseRecord
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.SimulatorSettings
import app.luxion.shogunai.domain.model.WaitTicket
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class ResourceAcquisition(
    val acquiredNow: Boolean,
    val releases: List<PoolRelease>,
    val waitedSeconds: Long? = null,
)

data class DeviceStart<T>(val device: T, val acquisition: ResourceAcquisition)

class AcquireWorktreeResourceUseCase(
    private val provisioningSource: RepositoryProvisioningSource,
    private val resolveEnvironment: ResolveWorktreeEnvironmentUseCase,
    private val writeEnvironmentFile: WriteEnvironmentFileUseCase,
    private val stateStore: EnvironmentStateStore,
    private val portProbe: PortProbe,
    private val simulators: AppleSimulators,
    private val sdk: AndroidSdk,
    private val readPool: ReadResourcePoolUseCase,
    private val contributions: (SimulatorSettings) -> ProviderContributions,
    private val waitQueue: DeviceWaitQueue,
    private val liveness: ProcessLiveness,
    private val clock: () -> Instant = Instant::now,
    private val sleep: suspend (Duration) -> Unit = { delay(it.toMillis()) },
) {
    suspend fun emulator(repositoryPath: String, worktreePath: String, wait: DeviceWait? = null): Result<ResourceAcquisition> =
        acquire(ResourceKind.EMULATOR, repositoryPath, worktreePath, SimulatorSettings(), wait)

    suspend fun simulator(
        repositoryPath: String,
        worktreePath: String,
        requested: SimulatorSettings = SimulatorSettings(),
        wait: DeviceWait? = null,
    ): Result<ResourceAcquisition> = acquire(ResourceKind.IOS_SIMULATOR, repositoryPath, worktreePath, requested, wait)

    suspend fun waiting(kind: ResourceKind): List<WaitTicket> = withContext(Dispatchers.IO) {
        stateStore.withLock { liveQueue(kind) }
    }

    private suspend fun acquire(
        kind: ResourceKind,
        repositoryPath: String,
        worktreePath: String,
        requested: SimulatorSettings,
        wait: DeviceWait?,
    ): Result<ResourceAcquisition> {
        if (wait == null) return attempt(kind, repositoryPath, worktreePath, requested, ticketId = null)
        return runCatching { acquireWaiting(kind, repositoryPath, worktreePath, requested, wait) }
    }

    private suspend fun acquireWaiting(
        kind: ResourceKind,
        repositoryPath: String,
        worktreePath: String,
        requested: SimulatorSettings,
        wait: DeviceWait,
    ): ResourceAcquisition {
        val started = clock()
        val first = attempt(kind, repositoryPath, worktreePath, requested, ticketId = null)
        if (first.exceptionOrNull() !is WorktreeError.ResourcePoolFull) return first.getOrThrow().copy(waitedSeconds = 0)
        val ticket = WaitTicket(
            UUID.randomUUID().toString(),
            normalizePath(resolveEnvironment.directoriesOf(worktreePath).admin),
            normalizePath(worktreePath),
            liveness.current(),
            started.toEpochMilli(),
        )
        locked { waitQueue.write(kind, liveQueue(kind) + ticket) }
        try {
            val deadline = started + wait.timeout
            while (true) {
                val result = attempt(kind, repositoryPath, worktreePath, requested, ticket.id)
                val full = result.exceptionOrNull() as? WorktreeError.ResourcePoolFull
                    ?: return result.getOrThrow().copy(waitedSeconds = Duration.between(started, clock()).seconds)
                val now = clock()
                if (!now.isBefore(deadline)) {
                    throw WorktreeError.DeviceWaitTimedOut(kind, Duration.between(started, now), full.limit, full.holders)
                }
                val queue = locked { liveQueue(kind) }
                val nextExpiry = full.holders.mapNotNull { it.leaseExpiresAt }.filter { it.isAfter(now) }.minOrNull()
                wait.onProgress(
                    DeviceWaitProgress(
                        kind,
                        position = queue.indexOfFirst { it.id == ticket.id } + 1,
                        waiting = queue.size,
                        limit = full.limit,
                        holders = full.holders,
                        nextLeaseExpiry = nextExpiry,
                    ),
                )
                sleep(nextPause(now, deadline, nextExpiry))
            }
        } finally {
            withContext(NonCancellable) {
                locked { waitQueue.write(kind, waitQueue.read(kind).filterNot { it.id == ticket.id }) }
            }
        }
    }

    private fun nextPause(now: Instant, deadline: Instant, nextExpiry: Instant?): Duration =
        listOfNotNull(WAIT_POLL_INTERVAL, nextExpiry?.let { Duration.between(now, it) + LEASE_EXPIRY_MARGIN }, Duration.between(now, deadline))
            .min()
            .coerceAtLeast(MINIMUM_WAIT_PAUSE)

    private suspend fun <T> locked(block: () -> T): T = withContext(Dispatchers.IO) { stateStore.withLock(block) }

    private fun liveQueue(kind: ResourceKind): List<WaitTicket> {
        val queue = waitQueue.read(kind)
        val live = queue.filter { liveness.isAlive(it.process) }
        if (live.size != queue.size) waitQueue.write(kind, live)
        return live
    }

    private suspend fun attempt(
        kind: ResourceKind,
        repositoryPath: String,
        worktreePath: String,
        requested: SimulatorSettings,
        ticketId: String?,
    ): Result<ResourceAcquisition> = runCatching {
        val directories = resolveEnvironment.directoriesOf(worktreePath)
        val key = keyOf(kind)
        val outcome = withContext(Dispatchers.IO) {
            stateStore.withLock {
                runBlocking { acquireLocked(kind, key, repositoryPath, worktreePath, directories, requested, ticketId) }
            }
        }
        val victims = outcome.released.mapNotNull { it.worktreePath }
        (listOf(worktreePath).filter { outcome.acquiredNow || outcome.released.isNotEmpty() } + victims).distinct()
            .forEach { refreshEnvironmentFile(repositoryPath, it) }
        ResourceAcquisition(outcome.acquiredNow, outcome.released)
    }

    private suspend fun acquireLocked(
        kind: ResourceKind,
        key: String,
        repositoryPath: String,
        worktreePath: String,
        directories: WorktreeGitDirectories,
        requested: SimulatorSettings,
        ticketId: String?,
    ): LockedOutcome {
        var usage = readPool.of(kind, repositoryPath)
        val own = usage.holders.firstOrNull { it.adminDirectory?.let(::normalizePath) == normalizePath(directories.admin) }
        if (own != null && !isStale(kind, own)) {
            touch(directories.admin, key)
            return LockedOutcome(acquiredNow = false, released = emptyList())
        }
        val beneficiary = "${folderName(repositoryPath)} · ${folderName(worktreePath)}"
        if (own != null) {
            stateStore.release(directories.admin, key)
            usage = usage.copy(holders = usage.holders - own)
        }
        val queue = liveQueue(kind)
        val others = queue.filter { normalizePath(it.adminDirectory) != normalizePath(directories.admin) }
        if (queue.takeWhile { it.id != ticketId }.any { it in others }) {
            throw WorktreeError.ResourcePoolFull(kind, usage.effectiveLimit ?: usage.holders.size, usage.holders, others.size)
        }
        val released = mutableListOf<PoolRelease>()
        val limit = usage.effectiveLimit
        var remaining = usage.holders
        while (limit != null && remaining.size >= limit) {
            val victim = nextVictim(remaining) ?: throw WorktreeError.ResourcePoolFull(kind, limit, remaining, others.size)
            release(victim, key, repositoryPath, beneficiary)
            released += PoolRelease(kind, victim.worktreePath, PoolReleaseReason.MAKE_ROOM, victim.displayName)
            remaining = remaining - victim
        }
        while (true) {
            if (reserve(kind, key, repositoryPath, worktreePath, directories, requested)) break
            val victim = nextVictim(remaining) ?: throw WorktreeError.NoEmulatorPort(FIRST_EMULATOR_PORT, LAST_EMULATOR_PORT)
            release(victim, key, repositoryPath, beneficiary)
            released += PoolRelease(kind, victim.worktreePath, PoolReleaseReason.MAKE_ROOM, victim.displayName)
            remaining = remaining - victim
        }
        stateStore.writeReleases(directories.admin, stateStore.readReleases(directories.admin) - key)
        touch(directories.admin, key)
        return LockedOutcome(acquiredNow = true, released = released)
    }

    private suspend fun reserve(
        kind: ResourceKind,
        key: String,
        repositoryPath: String,
        worktreePath: String,
        directories: WorktreeGitDirectories,
        requested: SimulatorSettings,
    ): Boolean = when (kind) {
        ResourceKind.EMULATOR -> {
            val taken = stateStore.reservedPortsOnMachine(directories.common)
            val free = (FIRST_EMULATOR_PORT..LAST_EMULATOR_PORT step 2).filter { candidate ->
                candidate !in taken && portProbe.isFree(candidate) && portProbe.isFree(candidate + 1)
            }
            val previous = stateStore.readReleases(directories.admin)[key]?.previous?.let(::portOf)
            val quarantined = recentlyReleasedPorts(directories.common, key)
            val port = free.firstOrNull { it == previous } ?: free.firstOrNull { it !in quarantined } ?: free.firstOrNull()
            if (port != null) {
                stateStore.writePorts(directories.admin, stateStore.readPorts(directories.admin) + (key to port))
            }
            port != null
        }
        ResourceKind.IOS_SIMULATOR -> {
            val providers = provisioningSource.read(repositoryPath).providers
            val request = contributions(requested).forRepository(repositoryPath, providers)
                .flatMap { it.resources }
                .first { it.key == key }
            val udid = request.create(ResourceContext(folderName(repositoryPath), folderName(worktreePath)))
            stateStore.writeResources(directories.admin, stateStore.readResources(directories.admin) + (key to udid))
            true
        }
        ResourceKind.GRADLE_DAEMON -> throw IllegalArgumentException("Gradle daemons aren't acquired")
    }

    private suspend fun release(holder: PoolHolder, key: String, repositoryPath: String, beneficiary: String) {
        val admin = checkNotNull(holder.adminDirectory)
        if (holder.state == PoolHolderState.RUNNING) {
            val serial = checkNotNull(holder.reference)
            sdk.stopEmulator(sdk.locate(repositoryPath), serial)
        }
        if (holder.kind == ResourceKind.IOS_SIMULATOR) {
            val udid = holder.reference
            if (udid != null && simulators.devices().any { it.udid == udid }) {
                if (holder.state == PoolHolderState.BOOTED) simulators.shutdown(udid)
                simulators.delete(udid)
            }
        }
        stateStore.release(admin, key)
        stateStore.writeReleases(
            admin,
            stateStore.readReleases(admin) + (key to ReleaseRecord(clock().toEpochMilli(), beneficiary, holder.reference)),
        )
    }

    private fun recentlyReleasedPorts(commonDirectory: String, key: String): Set<Int> {
        val horizon = clock().toEpochMilli() - readPool.leaseDuration().toMillis()
        return stateStore.reservationsOnMachine(commonDirectory)
            .mapNotNull { it.releases[key] }
            .filter { it.releasedAt > horizon }
            .mapNotNull { it.previous?.let(::portOf) }
            .toSet()
    }

    private fun portOf(serial: String): Int? = serial.substringAfterLast('-').toIntOrNull()

    private suspend fun isStale(kind: ResourceKind, holder: PoolHolder): Boolean =
        kind == ResourceKind.IOS_SIMULATOR && simulators.devices().none { it.udid == holder.reference }

    private fun nextVictim(holders: List<PoolHolder>): PoolHolder? =
        holders.filter { it.releaseRank != null && it.adminDirectory != null }
            .minWithOrNull(compareBy<PoolHolder> { it.releaseRank }.thenBy { it.lastUsed != null }.thenBy { it.lastUsed })

    private fun touch(admin: String, key: String) {
        val now = clock().toEpochMilli()
        stateStore.writeLastUsed(admin, stateStore.readLastUsed(admin) + (key to now))
        stateStore.writeLeases(admin, stateStore.readLeases(admin) + (key to now))
    }

    private suspend fun refreshEnvironmentFile(repositoryPath: String, worktreePath: String) {
        runCatching {
            val environment = resolveEnvironment(repositoryPath, worktreePath, reserve = false).getOrThrow()
            writeEnvironmentFile(repositoryPath, worktreePath, environment).getOrThrow()
        }
    }

    private fun keyOf(kind: ResourceKind): String = when (kind) {
        ResourceKind.EMULATOR -> EMULATOR_PORT_KEY
        ResourceKind.IOS_SIMULATOR -> IOS_SIMULATOR_KEY
        ResourceKind.GRADLE_DAEMON -> throw IllegalArgumentException("Gradle daemons aren't acquired")
    }

    private fun folderName(path: String): String = normalizePath(path).substringAfterLast('/')

    private class LockedOutcome(val acquiredNow: Boolean, val released: List<PoolRelease>)

    companion object {
        val WAIT_POLL_INTERVAL: Duration = Duration.ofSeconds(10)
        private val LEASE_EXPIRY_MARGIN: Duration = Duration.ofSeconds(1)
        private val MINIMUM_WAIT_PAUSE: Duration = Duration.ofSeconds(1)
    }
}
