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
import app.luxion.shogunai.domain.model.emulatorSerial
import java.time.Duration
import java.time.Instant

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


    suspend fun emulators(repositoryPath: String): PoolUsage {
        val common = resolveEnvironment.directoriesOf(repositoryPath).common
        val states = runCatching { sdk.deviceStates(sdk.locate(repositoryPath)) }.getOrDefault(emptyMap())
        val holders = stateStore.reservationsOnMachine(common).mapNotNull { reservation ->
            val port = reservation.ports[EMULATOR_PORT_KEY] ?: return@mapNotNull null
            val serial = emulatorSerial(port)
            val state = states[serial] ?: EmulatorState.STOPPED
            val holderState = when (state) {
                EmulatorState.STOPPED -> PoolHolderState.STOPPED
                EmulatorState.BOOTING -> PoolHolderState.BOOTING
                EmulatorState.RUNNING -> PoolHolderState.RUNNING
            }
            holder(
                kind = ResourceKind.EMULATOR,
                reservation = reservation,
                key = EMULATOR_PORT_KEY,
                common = common,
                state = holderState,
                reference = serial,
            )
        }
        return usage(ResourceKind.EMULATOR, holders)
    }

    suspend fun simulators(repositoryPath: String): PoolUsage {
        val common = resolveEnvironment.directoriesOf(repositoryPath).common
        val devices = if (simulators.isSupported()) runCatching { simulators.devices() }.getOrDefault(emptyList()).associateBy { it.udid } else emptyMap()
        val holders = stateStore.reservationsOnMachine(common).mapNotNull { reservation ->
            val udid = reservation.resources[IOS_SIMULATOR_KEY] ?: return@mapNotNull null
            val state = devices[udid]?.state ?: SimulatorState.SHUTDOWN
            val holderState = when (state) {
                SimulatorState.SHUTDOWN -> PoolHolderState.SHUTDOWN
                SimulatorState.BOOTING -> PoolHolderState.BOOTING
                SimulatorState.BOOTED -> PoolHolderState.BOOTED
            }
            holder(
                kind = ResourceKind.IOS_SIMULATOR,
                reservation = reservation,
                key = IOS_SIMULATOR_KEY,
                common = common,
                state = holderState,
                reference = udid,
            )
        }
        return usage(ResourceKind.IOS_SIMULATOR, holders)
    }

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
            releaseRank = releaseRank(state, leaseExpiresAt, agent),
        )
    }

    private fun releaseRank(state: PoolHolderState, leaseExpiresAt: Instant?, agent: AgentState?): Int? {
        if (agent == AgentState.WORKING || state == PoolHolderState.BOOTING) return null
        if (leaseExpiresAt != null && leaseExpiresAt.isAfter(clock())) return null
        if (agent == AgentState.BLOCKED) return RANK_BLOCKED
        val stopped = state == PoolHolderState.STOPPED || state == PoolHolderState.SHUTDOWN
        return when {
            !stopped -> RANK_RUNNING
            agent == null -> RANK_NO_SESSION
            else -> RANK_DONE
        }
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

    private companion object {
        const val RANK_NO_SESSION = 1
        const val RANK_DONE = 2
        const val RANK_RUNNING = 3
        const val RANK_BLOCKED = 4
    }
}
