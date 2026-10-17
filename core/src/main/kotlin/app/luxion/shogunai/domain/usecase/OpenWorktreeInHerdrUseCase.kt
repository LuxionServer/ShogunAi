package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.HerdrClient
import app.luxion.shogunai.domain.model.HerdrOpening
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree

class OpenWorktreeInHerdrUseCase(
    private val config: ProjectConfig,
    private val herdr: HerdrClient,
    private val resolveEnvironment: ResolveWorktreeEnvironmentUseCase,
) {
    suspend operator fun invoke(worktree: Worktree): Result<HerdrOpening> = runCatching {
        val label = normalizePath(worktree.path).substringAfterLast('/')
        val opening = herdr.openWorktree(config.baseRepositoryPath, worktree.path, label)
        if (opening.alreadyOpen) return@runCatching HerdrOpening.FOCUSED
        val environment = resolveEnvironment(config.baseRepositoryPath, worktree.path).getOrThrow()
        val pane = if (environment.variables.isEmpty()) {
            opening.rootPaneId
        } else {
            herdr.splitPane(opening.rootPaneId, worktree.path, environment.variables)
                .also { herdr.closePane(opening.rootPaneId) }
        }
        herdr.runInPane(pane, config.agentLaunchConfig.resolvedCommand())
        HerdrOpening.OPENED
    }
}
