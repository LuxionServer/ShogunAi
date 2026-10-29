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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class OpenWorktreeInOrcaUseCase(
    private val config: ProjectConfig,
    private val orca: OrcaClient,
    private val resolveEnvironment: ResolveWorktreeEnvironmentUseCase,
    naming: ClaudeSessionNaming = ClaudeSessionNaming(),
    private val usesPowerShell: Boolean = HostOs.current() == HostOs.WINDOWS,
    private val samePath: (String, String) -> Boolean = ::sameDirectory,
    timeout: Duration = AGENT_START_SECONDS.seconds,
    pollInterval: Duration = 500.milliseconds,
    private val runningEmulator: suspend (String) -> String? = { null },
) {
    private val launcher = OrcaAgentLauncher(orca, naming, samePath, timeout, pollInterval)

    suspend operator fun invoke(
        worktree: Worktree,
        agent: AgentLaunchConfig = config.agentLaunchConfig,
        sessionName: String = worktreeSessionName(projectDefaultsFor(config.baseRepositoryPath).name, worktree),
    ): Result<OrcaOpening> = runCatching {
        launcher.ensureRunning()
        val repo = launcher.registeredRepo(config.baseRepositoryPath)
        if (orca.worktrees(repo.id).none { samePath(it.path, worktree.path) }) orca.showExternalWorktrees(repo.id)
        val workspace = OrcaWorkspace.Worktree(worktree.path)
        val emulator = showRunningEmulator(workspace, worktree.path)
        launcher.focusLiveAgent(workspace, worktree.path, sessionName)?.let { return@runCatching it.copy(emulator = emulator) }

        val variables = resolveEnvironment(config.baseRepositoryPath, worktree.path).getOrThrow().variables
        launcher.start(workspace, worktree.path, agent.resolvedCommand(), agent.displayName, sessionName) { named ->
            if (usesPowerShell) EnvironmentCommand.powershell(variables, named) else EnvironmentCommand.posix(variables, named)
        }.copy(emulator = emulator)
    }

    private suspend fun showRunningEmulator(workspace: OrcaWorkspace, worktreePath: String): String? {
        val serial = runCatching { runningEmulator(worktreePath) }.getOrNull() ?: return null
        return runCatching { orca.attachEmulator(workspace, serial) }.map { serial }.getOrNull()
    }
}
