package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.OrcaClient
import app.luxion.shogunai.domain.model.AGENT_START_SECONDS
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.EnvironmentCommand
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.model.OrcaOpening
import app.luxion.shogunai.domain.model.OrcaWorkspace
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.projectDefaultsFor
import app.luxion.shogunai.domain.model.worktreeSessionName
import app.luxion.shogunai.domain.runSuspendCatching
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class OpenWorktreeInOrcaUseCase(
    private val config: ProjectConfig,
    private val orca: OrcaClient,
    private val resolveEnvironment: ResolveWorktreeEnvironmentUseCase,
    naming: ClaudeSessionNaming = ClaudeSessionNaming(),
    private val usesPowerShell: Boolean = HostOs.current().isWindows,
    private val samePath: (String, String) -> Boolean = ::sameDirectory,
    timeout: Duration = AGENT_START_SECONDS.seconds,
    pollInterval: Duration = 500.milliseconds,
    private val runningEmulator: suspend (String) -> String? = { null },
    private val runningSimulator: suspend (String) -> String? = { null },
    private val heldPhysicalDevice: suspend (String) -> String? = { null },
) {
    private val launcher = OrcaAgentLauncher(orca, naming, samePath, timeout, pollInterval)

    suspend operator fun invoke(
        worktree: Worktree,
        agent: AgentLaunchConfig = config.agentLaunchConfig,
        sessionName: String = worktreeSessionName(projectDefaultsFor(config.baseRepositoryPath).name, worktree),
    ): Result<OrcaOpening> = runSuspendCatching {
        launcher.ensureRunning()
        val repo = launcher.registeredRepo(config.baseRepositoryPath)
        if (orca.worktrees(repo.id).none { samePath(it.path, worktree.path) }) orca.showExternalWorktrees(repo.id)
        val workspace = OrcaWorkspace.Worktree(worktree.path)
        val devices = showRunningDevice(workspace, worktree.path)
        launcher.focusLiveAgent(workspace, worktree.path, sessionName)?.let { return@runSuspendCatching devices.applyTo(it) }

        val variables = resolveEnvironment(config.baseRepositoryPath, worktree.path).getOrThrow().variables
        launcher.start(workspace, worktree.path, agent.resolvedCommand(), agent.displayName, sessionName) { named ->
            if (usesPowerShell) EnvironmentCommand.powershell(variables, named) else EnvironmentCommand.posix(variables, named)
        }.let(devices::applyTo)
    }

    private suspend fun showRunningDevice(workspace: OrcaWorkspace, worktreePath: String): ShownDevices {
        val serial = runSuspendCatching { runningEmulator(worktreePath) }.getOrNull()
        val udid = runSuspendCatching { runningSimulator(worktreePath) }.getOrNull()
        val physical = runSuspendCatching { heldPhysicalDevice(worktreePath) }.getOrNull()
        return when {
            udid != null && (serial != null || physical != null) -> ShownDevices(skipped = true)
            physical != null -> ShownDevices(physicalDevice = attached(workspace, physical))
            serial != null -> ShownDevices(emulator = attached(workspace, serial))
            udid != null -> ShownDevices(simulator = attached(workspace, udid))
            else -> ShownDevices()
        }
    }

    private suspend fun attached(workspace: OrcaWorkspace, device: String): String? =
        runSuspendCatching { orca.attachEmulator(workspace, device) }.map { device }.getOrNull()

    private data class ShownDevices(
        val emulator: String? = null,
        val simulator: String? = null,
        val skipped: Boolean = false,
        val physicalDevice: String? = null,
    ) {
        fun applyTo(opening: OrcaOpening): OrcaOpening =
            opening.copy(emulator = emulator, simulator = simulator, devicesSkipped = skipped, physicalDevice = physicalDevice)
    }
}
