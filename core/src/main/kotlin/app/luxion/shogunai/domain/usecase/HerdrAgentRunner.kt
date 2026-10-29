package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.AgentRunner
import app.luxion.shogunai.domain.io.HerdrClient
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.GroupRunnerOpening
import app.luxion.shogunai.domain.model.HerdrOpening
import app.luxion.shogunai.domain.model.HerdrOutcome
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RunnerId
import app.luxion.shogunai.domain.model.RunnerOpening
import app.luxion.shogunai.domain.model.RunnerOutcome
import app.luxion.shogunai.domain.model.Worktree

class HerdrAgentRunner(
    private val herdr: HerdrClient,
    private val resolveEnvironment: ResolveWorktreeEnvironmentUseCase,
    private val launcher: HerdrAgentLauncher = HerdrAgentLauncher(herdr),
) : AgentRunner {

    override val id: RunnerId = RunnerId.HERDR

    override fun isInstalled(): Boolean = herdr.isInstalled()

    override suspend fun openWorktree(config: ProjectConfig, worktree: Worktree, agent: AgentLaunchConfig): Result<RunnerOpening> =
        OpenWorktreeInHerdrUseCase(config, herdr, resolveEnvironment, launcher)(worktree, agent).map { it.toRunnerOpening() }

    override suspend fun openGroup(group: WorktreeGroup): Result<List<GroupRunnerOpening>> =
        Result.success(
            OpenGroupInHerdrUseCase(herdr, resolveEnvironment, launcher)(group).map { opening ->
                GroupRunnerOpening(opening.alias, opening.path, opening.result.map { it.toRunnerOpening() })
            },
        )

    override suspend fun openMultirepoAgent(multirepo: Multirepo): Result<RunnerOpening> =
        OpenMultirepoAgentInHerdrUseCase(herdr, launcher)(multirepo).map { it.toRunnerOpening() }
}

fun HerdrOpening.toRunnerOpening(): RunnerOpening =
    RunnerOpening(RunnerId.HERDR, outcome.toRunnerOutcome(), paneId, agent, sessionName)

fun HerdrOutcome.toRunnerOutcome(): RunnerOutcome = when (this) {
    HerdrOutcome.OPENED -> RunnerOutcome.OPENED
    HerdrOutcome.STARTED_AGENT -> RunnerOutcome.STARTED_AGENT
    HerdrOutcome.FOCUSED -> RunnerOutcome.FOCUSED
}
