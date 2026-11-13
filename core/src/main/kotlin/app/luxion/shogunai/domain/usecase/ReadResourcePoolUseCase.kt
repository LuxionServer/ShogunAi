package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.AndroidSdk
import app.luxion.shogunai.domain.io.AppleSimulators
import app.luxion.shogunai.domain.io.EnvironmentStateStore
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.WorktreeReservations
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.DaemonKind
import app.luxion.shogunai.domain.model.DaemonOwner
import app.luxion.shogunai.domain.model.DaemonProcess
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.EMULATOR_PORT_KEY
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.IOS_SIMULATOR_KEY
import app.luxion.shogunai.domain.model.PoolHolder
import app.luxion.shogunai.domain.model.PoolHolderState
import app.luxion.shogunai.domain.model.PoolUsage
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.effectiveLimit
import app.luxion.shogunai.domain.model.normalizePath
import app.luxion.shogunai.domain.model.releaseRank
import app.luxion.shogunai.domain.model.emulatorSerial
import app.luxion.shogunai.domain.model.runSuspendCatching
import java.time.Duration
import java.time.Instant

data class DeviceListing(
    val kind: ResourceKind,
    val commonDirectory: String,
    val states: Map<String, PoolHolderState>,
    val recordedBeforeListing: Set<String> = emptySet(),
) {
    fun isMissing(reference: String): Boolean = reference in recordedBeforeListing && reference !in states
}

class ReadResourcePoolUseCase(
    private val resolveEnvironment: ResolveWorktreeEnvironmentUseCase,
    private val stateStore: EnvironmentStateStore,
    private val fileManager: FileManager,
    private val sdk: AndroidSdk,
    private val simulators: AppleSimulators,
    private val limits: ResolveResourceLimitsUseCase,
    private val listGradleDaemons: ListGradleDaemonsUseCase? = null,
    private val agentActivity: (String) -> AgentState? = { null },
    private val clock: () -> Instant = Instant::now,
) {
    fun leaseDuration(): Duration = limits.leaseDuration()

    suspend fun emulators(repositoryPath: String): PoolUsage =
        usage(listing(ResourceKind.EMULATOR, repositoryPath) { emptyMap() })

    suspend fun simulators(repositoryPath: String): PoolUsage =
        usage(listing(ResourceKind.IOS_SIMULATOR, repositoryPath) { emptyMap() })

    suspend fun listing(kind: ResourceKind, repositoryPath: String): DeviceListing =
        listing(kind, repositoryPath) { throw it }

    fun usage(listing: DeviceListing): PoolUsage {
        val kind = listing.kind
        val key = keyOf(kind)
        val holders = stateStore.reservationsOnMachine(listing.commonDirectory).mapNotNull { reservation ->
            val reference = referenceOf(kind, reservation) ?: return@mapNotNull null
            holder(
                kind = kind,
                reservation = reservation,
                key = key,
                common = listing.commonDirectory,
                state = listing.states[reference] ?: idleState(kind),
                reference = reference,
            )
        }
        return usage(kind, holders)
    }

    private suspend fun listing(
        kind: ResourceKind,
        repositoryPath: String,
        onFailure: (Throwable) -> Map<String, PoolHolderState>,
    ): DeviceListing {
        val common = resolveEnvironment.directoriesOf(repositoryPath).common
        val recorded = if (kind == ResourceKind.IOS_SIMULATOR) stateStore.recordedResourcesOnMachine(common, IOS_SIMULATOR_KEY) else emptySet()
        val states = runSuspendCatching { deviceStates(kind, repositoryPath) }.getOrElse(onFailure)
        return DeviceListing(kind, common, states, recorded)
    }

    private suspend fun deviceStates(kind: ResourceKind, repositoryPath: String): Map<String, PoolHolderState> =
        if (kind == ResourceKind.EMULATOR) {
            sdk.deviceStates(sdk.locate(repositoryPath)).mapValues { (_, state) ->
                when (state) {
                    EmulatorState.STOPPED -> PoolHolderState.STOPPED
                    EmulatorState.BOOTING -> PoolHolderState.BOOTING
                    EmulatorState.RUNNING -> PoolHolderState.RUNNING
                }
            }
        } else if (simulators.isSupported()) {
            simulators.devices().associate { device ->
                device.udid to when (device.state) {
                    SimulatorState.SHUTDOWN -> PoolHolderState.SHUTDOWN
                    SimulatorState.BOOTING -> PoolHolderState.BOOTING
                    SimulatorState.BOOTED -> PoolHolderState.BOOTED
                }
            }
        } else {
            emptyMap()
        }

    private fun referenceOf(kind: ResourceKind, reservation: WorktreeReservations): String? =
        if (kind == ResourceKind.EMULATOR) {
            reservation.ports[EMULATOR_PORT_KEY]?.let(::emulatorSerial)
        } else {
            reservation.resources[IOS_SIMULATOR_KEY]
        }

    private fun idleState(kind: ResourceKind): PoolHolderState =
        if (kind == ResourceKind.EMULATOR) PoolHolderState.STOPPED else PoolHolderState.SHUTDOWN

    private fun keyOf(kind: ResourceKind): String = if (kind == ResourceKind.EMULATOR) EMULATOR_PORT_KEY else IOS_SIMULATOR_KEY

    suspend fun gradleDaemons(worktrees: List<Worktree>?, listed: List<DaemonProcess>? = null): PoolUsage {
        val daemons = listGradleDaemons?.invoke(worktrees, listed = listed)?.getOrDefault(emptyList()).orEmpty()
            .filter { it.process.kind == DaemonKind.GRADLE }
        val holders = daemons.map { daemon ->
            val state = daemon.process.state
            PoolHolder(
                kind = ResourceKind.GRADLE_DAEMON,
                worktreePath = (daemon.owner as? DaemonOwner.Worktree)?.path,
                adminDirectory = null,
                state = when (state) {
                    DaemonState.Busy -> PoolHolderState.BUSY
                    is DaemonState.Idle -> PoolHolderState.IDLE
                    DaemonState.Unknown, DaemonState.Untracked -> PoolHolderState.UNKNOWN
                },
                idle = state is DaemonState.Idle,
                lastUsed = (state as? DaemonState.Idle)?.since,
                reference = daemon.pid.toString(),
            )
        }
        return usage(ResourceKind.GRADLE_DAEMON, holders)
    }

    suspend fun of(
        kind: ResourceKind,
        repositoryPath: String,
        worktrees: List<Worktree>? = null,
        listedGradleDaemons: List<DaemonProcess>? = null,
    ): PoolUsage = when (kind) {
        ResourceKind.EMULATOR -> emulators(repositoryPath)
        ResourceKind.IOS_SIMULATOR -> simulators(repositoryPath)
        ResourceKind.GRADLE_DAEMON -> gradleDaemons(worktrees, listedGradleDaemons)
    }

    private fun holder(
        kind: ResourceKind,
        reservation: WorktreeReservations,
        key: String,
        common: String,
        state: PoolHolderState,
        reference: String,
    ): PoolHolder {
        val worktreePath = worktreePathOf(reservation.adminDirectory, common)
        val leaseExpiresAt = reservation.leases[key]?.let { Instant.ofEpochMilli(it).plus(leaseDuration()) }
        val agent = worktreePath?.let(agentActivity)
        return PoolHolder(
            kind = kind,
            worktreePath = worktreePath,
            adminDirectory = reservation.adminDirectory,
            state = state,
            idle = state == PoolHolderState.STOPPED || state == PoolHolderState.SHUTDOWN,
            lastUsed = reservation.lastUsed[key]?.let(Instant::ofEpochMilli),
            reference = reference,
            leaseExpiresAt = leaseExpiresAt,
            agentState = agent,
            releaseRank = releaseRank(
                stopped = state == PoolHolderState.STOPPED || state == PoolHolderState.SHUTDOWN,
                busy = state == PoolHolderState.BOOTING,
                leaseExpiresAt = leaseExpiresAt,
                agent = agent,
                now = clock(),
            ),
        )
    }

    private fun usage(kind: ResourceKind, holders: List<PoolHolder>): PoolUsage {
        val limit = limits().forKind(kind)
        return PoolUsage(kind, limit, effectiveLimit(kind, limit), holders)
    }

    private fun worktreePathOf(adminDirectory: String, commonDirectory: String): String? {
        if (normalizePath(adminDirectory) == normalizePath(commonDirectory)) {
            return normalizePath(commonDirectory).removeSuffix("/.git").takeIf { it.isNotEmpty() }
        }
        return fileManager.readText("$adminDirectory/gitdir")?.let { normalizePath(it.trim()).removeSuffix("/.git") }
    }
}
