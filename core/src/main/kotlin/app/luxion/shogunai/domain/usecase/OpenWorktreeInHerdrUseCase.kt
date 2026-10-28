package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.HerdrClient
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.HerdrOpening
import app.luxion.shogunai.domain.model.HerdrOutcome
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.projectDefaultsFor
import app.luxion.shogunai.domain.model.worktreeSessionName

class OpenWorktreeInHerdrUseCase(
    private val config: ProjectConfig,
    private val herdr: HerdrClient,
    private val resolveEnvironment: ResolveWorktreeEnvironmentUseCase,
    private val launcher: HerdrAgentLauncher = HerdrAgentLauncher(herdr),
) {
    suspend operator fun invoke(
        worktree: Worktree,
        agent: AgentLaunchConfig = config.agentLaunchConfig,
        sessionName: String = worktreeSessionName(projectDefaultsFor(config.baseRepositoryPath).name, worktree),
    ): Result<HerdrOpening> = runCatching {
        val label = normalizePath(worktree.path).substringAfterLast('/')
        val opening = herdr.openWorktree(config.baseRepositoryPath, worktree.path, label)
        if (opening.alreadyOpen) {
            launcher.focusLiveAgent(opening.workspaceId, worktree.path, sessionName)?.let { return@runCatching it }
        }
        val environment = resolveEnvironment(config.baseRepositoryPath, worktree.path).getOrThrow()
        val pane = when {
            opening.alreadyOpen -> herdr.splitPane(opening.rootPaneId, worktree.path, environment.variables)
            environment.variables.isEmpty() -> opening.rootPaneId
            else -> herdr.splitPane(opening.rootPaneId, worktree.path, environment.variables)
                .also { herdr.closePane(opening.rootPaneId) }
        }
        val outcome = if (opening.alreadyOpen) HerdrOutcome.STARTED_AGENT else HerdrOutcome.OPENED
        launcher.launch(opening.workspaceId, pane, agent.resolvedCommand(), agent.displayName, outcome, sessionName, worktree.path)
    }
}
