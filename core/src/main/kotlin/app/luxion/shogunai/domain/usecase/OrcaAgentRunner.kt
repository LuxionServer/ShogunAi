package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.AgentRunner
import app.luxion.shogunai.domain.io.OrcaClient
import app.luxion.shogunai.domain.model.AGENT_START_SECONDS
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.GroupRunnerOpening
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.OrcaOpening
import app.luxion.shogunai.domain.model.OrcaOutcome
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RunnerId
import app.luxion.shogunai.domain.model.RunnerOpening
import app.luxion.shogunai.domain.model.RunnerOutcome
import app.luxion.shogunai.domain.model.Worktree
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class OrcaAgentRunner(
    private val orca: OrcaClient,
    private val resolveEnvironment: ResolveWorktreeEnvironmentUseCase,
    private val naming: ClaudeSessionNaming = ClaudeSessionNaming(),
    private val usesPowerShell: Boolean = HostOs.current().isWindows,
    private val samePath: (String, String) -> Boolean = ::sameDirectory,
    private val timeout: Duration = AGENT_START_SECONDS.seconds,
    private val pollInterval: Duration = 500.milliseconds,
    private val runningEmulator: suspend (ProjectConfig, String) -> String? = { _, _ -> null },
    private val runningSimulator: suspend (ProjectConfig, String) -> String? = { _, _ -> null },
) : AgentRunner {

    override val id: RunnerId = RunnerId.ORCA

    override fun isInstalled(): Boolean = orca.isInstalled()

    override suspend fun openWorktree(config: ProjectConfig, worktree: Worktree, agent: AgentLaunchConfig): Result<RunnerOpening> =
        OpenWorktreeInOrcaUseCase(
            config,
            orca,
            resolveEnvironment,
            naming,
            usesPowerShell,
            samePath,
            timeout,
            pollInterval,
            runningEmulator = { path -> runningEmulator(config, path) },
            runningSimulator = { path -> runningSimulator(config, path) },
        )(worktree, agent)
            .map { it.toRunnerOpening() }

    override suspend fun openGroup(group: WorktreeGroup): Result<List<GroupRunnerOpening>> =
        OpenGroupInOrcaUseCase(orca, resolveEnvironment, naming, usesPowerShell, samePath, timeout, pollInterval, runningEmulator, runningSimulator)(group).map { openings ->
            openings.map { opening -> GroupRunnerOpening(opening.alias, opening.path, opening.result.map { it.toRunnerOpening() }) }
        }

    override suspend fun openMultirepoAgent(multirepo: Multirepo): Result<RunnerOpening> =
        OpenMultirepoAgentInOrcaUseCase(orca, naming, samePath, timeout, pollInterval)(multirepo).map { it.toRunnerOpening() }
}

fun OrcaOpening.toRunnerOpening(): RunnerOpening =
    RunnerOpening(RunnerId.ORCA, outcome.toRunnerOutcome(), terminalHandle, agent, sessionName, emulator, simulator, devicesSkipped)

fun OrcaOutcome.toRunnerOutcome(): RunnerOutcome = when (this) {
    OrcaOutcome.STARTED_AGENT -> RunnerOutcome.STARTED_AGENT
    OrcaOutcome.FOCUSED -> RunnerOutcome.FOCUSED
}
