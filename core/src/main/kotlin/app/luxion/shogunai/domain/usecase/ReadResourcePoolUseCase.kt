package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.AndroidSdk
import app.luxion.shogunai.domain.io.AppleSimulators
import app.luxion.shogunai.domain.io.EnvironmentStateStore
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.model.DaemonKind
import app.luxion.shogunai.domain.model.DaemonOwner
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
import java.time.Instant

class ReadResourcePoolUseCase(
    private val resolveEnvironment: ResolveWorktreeEnvironmentUseCase,
    private val stateStore: EnvironmentStateStore,
    private val fileManager: FileManager,
    private val sdk: AndroidSdk,
    private val simulators: AppleSimulators,
    private val limits: ResolveResourceLimitsUseCase,
    private val listGradleDaemons: ListGradleDaemonsUseCase? = null,
) {
    suspend fun emulators(repositoryPath: String): PoolUsage {
        val common = resolveEnvironment.directoriesOf(repositoryPath).common
        val states = runCatching { sdk.deviceStates(sdk.locate(repositoryPath)) }.getOrDefault(emptyMap())
        val holders = stateStore.reservationsOnMachine(common).mapNotNull { reservation ->
            val port = reservation.ports[EMULATOR_PORT_KEY] ?: return@mapNotNull null
            val serial = emulatorSerial(port)
            val state = states[serial] ?: EmulatorState.STOPPED
            PoolHolder(
                kind = ResourceKind.EMULATOR,
                worktreePath = worktreePathOf(reservation.adminDirectory, common),
                adminDirectory = reservation.adminDirectory,
                state = when (state) {
                    EmulatorState.STOPPED -> PoolHolderState.STOPPED
                    EmulatorState.BOOTING -> PoolHolderState.BOOTING
                    EmulatorState.RUNNING -> PoolHolderState.RUNNING
                },
                idle = state == EmulatorState.STOPPED,
                lastUsed = reservation.lastUsed[EMULATOR_PORT_KEY]?.let(Instant::ofEpochMilli),
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
            PoolHolder(
                kind = ResourceKind.IOS_SIMULATOR,
                worktreePath = worktreePathOf(reservation.adminDirectory, common),
                adminDirectory = reservation.adminDirectory,
                state = when (state) {
                    SimulatorState.SHUTDOWN -> PoolHolderState.SHUTDOWN
                    SimulatorState.BOOTING -> PoolHolderState.BOOTING
                    SimulatorState.BOOTED -> PoolHolderState.BOOTED
                },
                idle = state == SimulatorState.SHUTDOWN,
                lastUsed = reservation.lastUsed[IOS_SIMULATOR_KEY]?.let(Instant::ofEpochMilli),
                reference = udid,
            )
        }
        return usage(ResourceKind.IOS_SIMULATOR, holders)
    }

    suspend fun gradleDaemons(worktrees: List<Worktree>?): PoolUsage {
        val daemons = listGradleDaemons?.invoke(worktrees)?.getOrDefault(emptyList()).orEmpty()
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

    suspend fun of(kind: ResourceKind, repositoryPath: String, worktrees: List<Worktree>? = null): PoolUsage = when (kind) {
        ResourceKind.EMULATOR -> emulators(repositoryPath)
        ResourceKind.IOS_SIMULATOR -> simulators(repositoryPath)
        ResourceKind.GRADLE_DAEMON -> gradleDaemons(worktrees)
    }

    private fun usage(kind: ResourceKind, holders: List<PoolHolder>): PoolUsage {
        val limit = limits().forKind(kind)
        return PoolUsage(kind, limit, effectiveLimit(kind, limit), holders)
    }

    private fun worktreePathOf(adminDirectory: String, commonDirectory: String): String? {
        if (normalizePath(adminDirectory) == normalizePath(commonDirectory)) {
            return normalizePath(commonDirectory).removeSuffix("/.git").takeIf { it.isNotEmpty() }
        }
        return fileManager.readText("$adminDirectory/gitdir")?.trim()?.let(::normalizePath)?.removeSuffix("/.git")
    }
}
