package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.OrcaClient
import app.luxion.shogunai.domain.model.AGENT_START_SECONDS
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.EnvironmentCommand
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.model.OrcaOpening
import app.luxion.shogunai.domain.model.OrcaOutcome
import app.luxion.shogunai.domain.model.OrcaRepo
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.projectDefaultsFor
import app.luxion.shogunai.domain.model.worktreeSessionName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class OpenWorktreeInOrcaUseCase(
    private val config: ProjectConfig,
    private val orca: OrcaClient,
    private val resolveEnvironment: ResolveWorktreeEnvironmentUseCase,
    private val naming: ClaudeSessionNaming = ClaudeSessionNaming(),
    private val usesPowerShell: Boolean = HostOs.current() == HostOs.WINDOWS,
    private val samePath: (String, String) -> Boolean = ::sameDirectory,
    private val timeout: Duration = AGENT_START_SECONDS.seconds,
    private val pollInterval: Duration = 500.milliseconds,
) {
    suspend operator fun invoke(
        worktree: Worktree,
        agent: AgentLaunchConfig = config.agentLaunchConfig,
        sessionName: String = worktreeSessionName(projectDefaultsFor(config.baseRepositoryPath).name, worktree),
    ): Result<OrcaOpening> = runCatching {
        ensureRunning()
        val repo = registeredRepo()
        if (orca.worktrees(repo.id).none { samePath(it.path, worktree.path) }) orca.showExternalWorktrees(repo.id)
        focusLiveAgent(worktree.path, sessionName)?.let { return@runCatching it }

        val variables = resolveEnvironment(config.baseRepositoryPath, worktree.path).getOrThrow().variables
        val command = agent.resolvedCommand()
        val name = naming.freeName(command, sessionName)
        val named = naming.named(command, name)
        val line = if (usesPowerShell) EnvironmentCommand.powershell(variables, named) else EnvironmentCommand.posix(variables, named)
        val terminal = orca.createTerminal(worktree.path, name ?: agent.displayName, line, focus = true)
        withTimeoutOrNull(timeout) {
            while (orca.terminalAgent(terminal.handle) == null) delay(pollInterval)
        } ?: throw WorktreeError.OrcaAgentNotStarted(terminal.handle, line)
        raiseWindow()
        val confirmed = name?.let { naming.awaitSession(it, worktree.path) }
        OrcaOpening(OrcaOutcome.STARTED_AGENT, terminal.handle, agent.displayName, confirmed)
    }

    private suspend fun raiseWindow() {
        try {
            orca.bringToFront()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return
        }
    }

    private suspend fun ensureRunning() {
        if (orca.status().running) return
        orca.start()
        if (!orca.status().running) throw WorktreeError.OrcaNotStarted
    }

    private suspend fun registeredRepo(): OrcaRepo =
        orca.repos().firstOrNull { samePath(it.path, config.baseRepositoryPath) }
            ?: orca.addRepo(config.baseRepositoryPath)

    private suspend fun focusLiveAgent(path: String, sessionName: String): OrcaOpening? {
        val (terminal, agent) = orca.terminals(path).firstNotNullOfOrNull { terminal ->
            orca.terminalAgent(terminal.handle)?.let { terminal to it }
        } ?: return null
        orca.switchTo(terminal.handle)
        raiseWindow()
        val name = if (agent == CLAUDE) naming.focusedSessionName(path, sessionName) else null
        return OrcaOpening(OrcaOutcome.FOCUSED, terminal.handle, agent, name)
    }

    private companion object {
        const val CLAUDE = "claude"
    }
}
