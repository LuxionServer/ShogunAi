package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.model.Worktree

data class WorktreeResourcePlan(
    val emulatorSerial: String? = null,
    val simulatorUdid: String? = null,
    val idleDaemonPids: List<Long> = emptyList(),
    val failures: List<String> = emptyList(),
)

data class ReleasedResources(
    val stoppedEmulators: List<String> = emptyList(),
    val stoppedSimulators: List<String> = emptyList(),
    val stoppedDaemons: List<Long> = emptyList(),
    val failures: List<String> = emptyList(),
)

class ReleaseWorktreeResourcesUseCase(
    private val provisioningSource: RepositoryProvisioningSource,
    private val readEmulatorStates: ReadEmulatorStatesUseCase,
    private val stopEmulator: StopWorktreeEmulatorUseCase,
    private val readSimulatorStates: ReadSimulatorStatesUseCase,
    private val stopSimulator: StopWorktreeSimulatorUseCase,
    private val listDaemons: ListGradleDaemonsUseCase,
    private val stopDaemons: StopGradleDaemonsUseCase,
) {
    suspend fun plan(config: ProjectConfig, worktree: Worktree, allWorktrees: List<Worktree>): WorktreeResourcePlan {
        if (worktree.isPrunable) return WorktreeResourcePlan()
        val providers = runCatching { provisioningSource.read(config.baseRepositoryPath).providers }
            .getOrElse { return WorktreeResourcePlan(failures = listOf("couldn't read the repository's providers: ${it.message}")) }
        val failures = mutableListOf<String>()

        val emulatorSerial = if (providers.emulator == null) {
            null
        } else {
            readEmulatorStates(config, listOf(worktree))
                .onFailure { failures += "couldn't read the emulator: ${it.message}" }
                .getOrNull()?.get(worktree.path)?.takeIf { it.state != EmulatorState.STOPPED }?.serial
        }

        val simulatorUdid = if (providers.iosSimulator == null) {
            null
        } else {
            readSimulatorStates(config, listOf(worktree))
                .onFailure { failures += "couldn't read the simulator: ${it.message}" }
                .getOrNull()?.get(worktree.path)?.takeIf { it.state != SimulatorState.SHUTDOWN }?.udid
        }

        val idleDaemonPids = if (providers.gradle == null) {
            emptyList()
        } else {
            listDaemons(allWorktrees)
                .onFailure { failures += "couldn't list Gradle daemons: ${it.message}" }
                .getOrNull()
                ?.let { idleDaemonsOfRemovedWorktrees(it, allWorktrees, listOf(worktree.path)) }
                .orEmpty()
        }

        return WorktreeResourcePlan(emulatorSerial, simulatorUdid, idleDaemonPids, failures)
    }

    suspend fun release(config: ProjectConfig, plan: WorktreeResourcePlan): ReleasedResources {
        val failures = plan.failures.toMutableList()

        val emulators = plan.emulatorSerial?.let { serial ->
            runCatching { stopEmulator.stopSerials(config, listOf(serial)).map { it.serial } }
                .onFailure { failures += "couldn't stop the emulator $serial: ${it.message}" }
                .getOrDefault(emptyList())
        }.orEmpty()

        val simulators = plan.simulatorUdid?.let { udid ->
            runCatching { stopSimulator.shutDown(udid) }.map { listOf(udid) }
                .onFailure { failures += "couldn't stop the simulator $udid: ${it.message}" }
                .getOrDefault(emptyList())
        }.orEmpty()

        val daemons = if (plan.idleDaemonPids.isEmpty()) {
            emptyList()
        } else {
            stopDaemons(plan.idleDaemonPids)
                .onFailure { failures += "couldn't stop Gradle daemons: ${it.message}" }
                .getOrNull()?.stopped?.map { it.pid }
                .orEmpty()
        }

        return ReleasedResources(emulators, simulators, daemons, failures)
    }
}
