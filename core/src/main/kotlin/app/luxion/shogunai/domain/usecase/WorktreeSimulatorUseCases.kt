package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.AppleSimulators
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.DeviceWait
import app.luxion.shogunai.domain.model.IOS_SIMULATOR_KEY
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.SIMULATOR_NAME_PREFIX
import app.luxion.shogunai.domain.model.SimulatorDevice
import app.luxion.shogunai.domain.model.SimulatorSettings
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.WorktreeSimulator
import kotlinx.coroutines.delay

class StartWorktreeSimulatorUseCase(
    private val provisioningSource: RepositoryProvisioningSource,
    private val simulators: AppleSimulators,
    private val acquire: AcquireWorktreeResourceUseCase,
    private val resolveEnvironment: ResolveWorktreeEnvironmentUseCase,
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val pollIntervalMs: Long = POLL_INTERVAL_MS,
    private val timeoutMs: Long = BOOT_TIMEOUT_MS,
) {
    suspend operator fun invoke(
        config: ProjectConfig,
        worktreePath: String,
        requested: SimulatorSettings = SimulatorSettings(),
        wait: DeviceWait? = null,
    ): Result<DeviceStart<WorktreeSimulator>> = runCatching {
        requireIosSimulatorProvider(provisioningSource, simulators, config)
        val acquisition = acquire.simulator(config.baseRepositoryPath, worktreePath, requested, wait).getOrThrow()
        val udid = resolveEnvironment(config.baseRepositoryPath, worktreePath, reserve = false).getOrThrow()
            .resources.getValue(IOS_SIMULATOR_KEY)
        val device = simulators.devices().first { it.udid == udid }
        if (device.state == SimulatorState.SHUTDOWN) simulators.boot(udid)
        var waited = 0L
        while (simulators.devices().firstOrNull { it.udid == udid }?.state != SimulatorState.BOOTED) {
            if (waited >= timeoutMs) throw WorktreeError.SimulatorBootTimeout(device.name, timeoutMs / 1000)
            pause(pollIntervalMs)
            waited += pollIntervalMs
        }
        runCatching { simulators.show(udid) }
        DeviceStart(WorktreeSimulator(udid, device.name, SimulatorState.BOOTED), acquisition)
    }

    companion object {
        const val POLL_INTERVAL_MS = 1_000L
        const val BOOT_TIMEOUT_MS = 120_000L
    }
}

class StopWorktreeSimulatorUseCase(
    private val provisioningSource: RepositoryProvisioningSource,
    private val simulators: AppleSimulators,
    private val resolveEnvironment: ResolveWorktreeEnvironmentUseCase,
) {
    suspend operator fun invoke(config: ProjectConfig, worktreePath: String): Result<WorktreeSimulator?> = runCatching {
        requireIosSimulatorProvider(provisioningSource, simulators, config)
        val udid = resolveEnvironment(config.baseRepositoryPath, worktreePath, reserve = false).getOrThrow()
            .resources[IOS_SIMULATOR_KEY] ?: return@runCatching null
        val device = simulators.devices().firstOrNull { it.udid == udid } ?: return@runCatching null
        if (device.state != SimulatorState.SHUTDOWN) simulators.shutdown(udid)
        WorktreeSimulator(udid, device.name, SimulatorState.SHUTDOWN)
    }

    suspend fun shutDown(udid: String) {
        if (simulators.devices().any { it.udid == udid && it.state != SimulatorState.SHUTDOWN }) simulators.shutdown(udid)
    }
}

class ReadSimulatorStatesUseCase(
    private val provisioningSource: RepositoryProvisioningSource,
    private val simulators: AppleSimulators,
    private val resolveEnvironment: ResolveWorktreeEnvironmentUseCase,
) {
    suspend operator fun invoke(config: ProjectConfig, worktrees: List<Worktree>): Result<Map<String, WorktreeSimulator>> =
        runCatching {
            if (!declaresIosSimulator(provisioningSource, simulators, config)) return@runCatching emptyMap()
            val udids = worktrees.filterNot { it.isPrunable }.mapNotNull { worktree ->
                resolveEnvironment(config.baseRepositoryPath, worktree.path, reserve = false).getOrNull()
                    ?.resources?.get(IOS_SIMULATOR_KEY)
                    ?.let { worktree.path to it }
            }.toMap()
            if (udids.isEmpty()) return@runCatching emptyMap()
            val devices = simulators.devices().associateBy { it.udid }
            udids.mapNotNull { (path, udid) ->
                devices[udid]?.let { path to WorktreeSimulator(udid, it.name, it.state) }
            }.toMap()
        }
}

class DeleteSimulatorsUseCase(
    private val provisioningSource: RepositoryProvisioningSource,
    private val simulators: AppleSimulators,
    private val resolveEnvironment: ResolveWorktreeEnvironmentUseCase,
) {
    suspend fun orphans(config: ProjectConfig): Result<List<SimulatorDevice>> = runCatching {
        if (!declaresIosSimulator(provisioningSource, simulators, config)) return@runCatching emptyList()
        val recorded = resolveEnvironment.recordedOnMachine(config.baseRepositoryPath, IOS_SIMULATOR_KEY).getOrThrow()
        simulators.devices().filter { it.name.startsWith(SIMULATOR_NAME_PREFIX) && it.udid !in recorded }
    }

    suspend operator fun invoke(udids: List<String>): Result<List<String>> = runCatching {
        if (udids.isEmpty()) return@runCatching emptyList()
        val existing = simulators.devices().map { it.udid }.toSet()
        udids.distinct().filter { it in existing }.onEach { udid ->
            simulators.shutdown(udid)
            simulators.delete(udid)
        }
    }
}

private fun declaresIosSimulator(
    provisioningSource: RepositoryProvisioningSource,
    simulators: AppleSimulators,
    config: ProjectConfig,
): Boolean = simulators.isSupported() && provisioningSource.read(config.baseRepositoryPath).providers.iosSimulator != null

private fun requireIosSimulatorProvider(
    provisioningSource: RepositoryProvisioningSource,
    simulators: AppleSimulators,
    config: ProjectConfig,
) {
    if (provisioningSource.read(config.baseRepositoryPath).providers.iosSimulator == null) {
        throw WorktreeError.IosSimulatorProviderNotDeclared
    }
    if (!simulators.isSupported()) throw WorktreeError.IosSimulatorsNotSupported
}
