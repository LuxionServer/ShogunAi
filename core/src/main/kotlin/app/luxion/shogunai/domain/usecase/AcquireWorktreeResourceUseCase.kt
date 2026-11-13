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
import app.luxion.shogunai.domain.model.PoolHolder
import app.luxion.shogunai.domain.model.PoolRelease
import app.luxion.shogunai.domain.model.PoolHolderState
import app.luxion.shogunai.domain.model.PoolReleaseReason
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.SimulatorSettings
import app.luxion.shogunai.domain.model.WaitTicket
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.normalizePath
import app.luxion.shogunai.domain.model.queueName
import app.luxion.shogunai.domain.model.runSuspendCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
        return runSuspendCatching { acquireWaiting(kind, repositoryPath, worktreePath, requested, wait) }
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

    private val planner = DeviceReservationPlanner(stateStore, portProbe, readPool, inLine, clock)

    private suspend fun attempt(
        kind: ResourceKind,
        repositoryPath: String,
        worktreePath: String,
        requested: SimulatorSettings,
        ticketId: String?,
    ): Result<ResourceAcquisition> = runSuspendCatching {
        val request = DeviceRequest(kind, repositoryPath, worktreePath, resolveEnvironment.directoriesOf(worktreePath), ticketId)
        val listing = readPool.listing(kind, repositoryPath)
        val reservation = locked { planner.reserve(request, listing, simulator = null) }
        val released = when (reservation) {
            DeviceReservation.AlreadyHeld -> null
            DeviceReservation.NeedsSimulator -> reserveNewSimulator(request, listing, requested)
            is DeviceReservation.Reserved -> releaseVictims(request, reservation, created = null)
        }
        val victims = released.orEmpty().mapNotNull { it.worktreePath }
        (listOf(worktreePath).filter { released != null } + victims).distinct()
            .forEach { refreshEnvironmentFile(repositoryPath, it) }
        ResourceAcquisition(released != null, released.orEmpty())
    }

    private suspend fun reserveNewSimulator(
        request: DeviceRequest,
        listing: DeviceListing,
        requested: SimulatorSettings,
    ): List<PoolRelease>? {
        val udid = createSimulator(request, requested)
        val reservation = try {
            locked { planner.reserve(request, listing, udid) }
        } catch (error: Throwable) {
            withContext(NonCancellable) { simulators.delete(udid) }
            throw error
        }
        if (reservation !is DeviceReservation.Reserved) {
            simulators.delete(udid)
            return null
        }
        return releaseVictims(request, reservation, created = udid)
    }

    private suspend fun createSimulator(request: DeviceRequest, requested: SimulatorSettings): String {
        val providers = provisioningSource.read(request.repositoryPath).providers
        val resource = contributions(requested).forRepository(request.repositoryPath, providers)
            .flatMap { it.resources }
            .first { it.key == request.key }
        return resource.create(ResourceContext(folderName(request.repositoryPath), folderName(request.worktreePath)))
    }

    private suspend fun releaseVictims(
        request: DeviceRequest,
        reserved: DeviceReservation.Reserved,
        created: String?,
    ): List<PoolRelease> {
        try {
            currentCoroutineContext().ensureActive()
            reserved.victims.forEach { releaseDevice(it, request.repositoryPath) }
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                locked { planner.undo(request, reserved) }
                created?.let { simulators.delete(it) }
            }
            throw error
        }
        return reserved.victims.map { PoolRelease(request.kind, it.worktreePath, PoolReleaseReason.MAKE_ROOM, it.displayName) }
    }

    private suspend fun releaseDevice(holder: PoolHolder, repositoryPath: String) {
        if (holder.state == PoolHolderState.RUNNING) {
            sdk.stopEmulator(sdk.locate(repositoryPath), holder.reference!!)
        }
        if (holder.kind == ResourceKind.IOS_SIMULATOR) {
            val udid = holder.reference!!
            if (simulators.devices().any { it.udid == udid }) {
                if (holder.state == PoolHolderState.BOOTED) simulators.shutdown(udid)
                simulators.delete(udid)
            }
        }
    }

    private suspend fun <T> locked(block: () -> T): T = withContext(NonCancellable + Dispatchers.IO) { stateStore.withLock(block) }

    private suspend fun refreshEnvironmentFile(repositoryPath: String, worktreePath: String) {
        runSuspendCatching {
            val environment = resolveEnvironment(repositoryPath, worktreePath, reserve = false).getOrThrow()
            writeEnvironmentFile(repositoryPath, worktreePath, environment).getOrThrow()
        }
    }

    private fun folderName(path: String): String = normalizePath(path).substringAfterLast('/')
}
