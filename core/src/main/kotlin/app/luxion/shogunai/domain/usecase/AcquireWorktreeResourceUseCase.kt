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
import app.luxion.shogunai.domain.model.queueName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

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

    private val inLine = WaitInLine(stateStore, waitQueue, liveness, clock, sleep)

    suspend fun waiting(kind: ResourceKind): List<WaitTicket> = inLine.waiting(kind.queueName)

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
        val waited = inLine.await(
            queue = kind.queueName,
            adminDirectory = resolveEnvironment.directoriesOf(worktreePath).admin,
            worktreePath = worktreePath,
            timeout = wait.timeout,
            attempt = { ticketId -> attempt(kind, repositoryPath, worktreePath, requested, ticketId) },
            blockedBy = { it as? WorktreeError.ResourcePoolFull },
            onWaiting = { line ->
                val full = line.blocked
                val nextExpiry = full.holders.mapNotNull { it.leaseExpiresAt }.filter { it.isAfter(line.now) }.minOrNull()
                wait.onProgress(DeviceWaitProgress(kind, line.position, line.waiting, full.limit, full.holders, nextExpiry))
                nextExpiry
            },
            timedOut = { duration, full -> WorktreeError.DeviceWaitTimedOut(kind, duration, full.limit, full.holders) },
        )
        return waited.value.copy(waitedSeconds = waited.waitedSeconds)
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
        val queue = inLine.liveQueue(kind.queueName)
        val others = queue.othersThan(directories.admin)
        if (queue.othersAheadOf(ticketId, directories.admin)) {
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
    ): Boolean =
        if (kind == ResourceKind.EMULATOR) {
            reserveEmulatorPort(key, directories)
        } else {
            reserveSimulator(key, repositoryPath, worktreePath, directories, requested)
        }

    private fun reserveEmulatorPort(key: String, directories: WorktreeGitDirectories): Boolean {
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
        return port != null
    }

    private suspend fun reserveSimulator(
        key: String,
        repositoryPath: String,
        worktreePath: String,
        directories: WorktreeGitDirectories,
        requested: SimulatorSettings,
    ): Boolean {
        val providers = provisioningSource.read(repositoryPath).providers
        val request = contributions(requested).forRepository(repositoryPath, providers)
            .flatMap { it.resources }
            .first { it.key == key }
        val udid = request.create(ResourceContext(folderName(repositoryPath), folderName(worktreePath)))
        stateStore.writeResources(directories.admin, stateStore.readResources(directories.admin) + (key to udid))
        return true
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

    private fun keyOf(kind: ResourceKind): String = if (kind == ResourceKind.EMULATOR) EMULATOR_PORT_KEY else IOS_SIMULATOR_KEY

    private fun folderName(path: String): String = normalizePath(path).substringAfterLast('/')

    private class LockedOutcome(val acquiredNow: Boolean, val released: List<PoolRelease>)
}
