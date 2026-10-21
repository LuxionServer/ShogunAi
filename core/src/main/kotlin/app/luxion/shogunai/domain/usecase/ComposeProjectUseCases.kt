package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.ComposeProjectSource
import app.luxion.shogunai.domain.model.ComposeDownFailure
import app.luxion.shogunai.domain.model.ComposeDownReport
import app.luxion.shogunai.domain.model.ComposeOwner
import app.luxion.shogunai.domain.model.ComposeProject
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeComposeProject

fun worktreeComposeProjectName(repositoryPath: String, worktreePath: String): String =
    composeProjectName(normalizePath(repositoryPath).substringAfterLast('/'), normalizePath(worktreePath).substringAfterLast('/'))

class ListComposeProjectsUseCase(private val source: ComposeProjectSource) {

    suspend operator fun invoke(
        repositoryPath: String,
        worktrees: List<Worktree>,
        worktreesRoot: String?,
    ): Result<List<WorktreeComposeProject>> = runCatching {
        val byName = worktrees.associateBy { worktreeComposeProjectName(repositoryPath, it.path) }
        val repositoryPrefix = composeProjectName(normalizePath(repositoryPath).substringAfterLast('/'), "").trimEnd('-', '_') + "-"
        source.list().mapNotNull { project ->
            val worktree = byName[project.name]
            when {
                worktree != null -> WorktreeComposeProject(project, ComposeOwner.Worktree(worktree.path))
                isOrphanOf(project, repositoryPrefix, repositoryPath, worktreesRoot, worktrees) ->
                    WorktreeComposeProject(project, ComposeOwner.RemovedWorktree)
                else -> null
            }
        }
    }

    private fun isOrphanOf(
        project: ComposeProject,
        repositoryPrefix: String,
        repositoryPath: String,
        worktreesRoot: String?,
        worktrees: List<Worktree>,
    ): Boolean {
        if (!project.name.startsWith(repositoryPrefix)) return false
        val roots = listOfNotNull(repositoryPath, worktreesRoot).map(::normalizePath)
        val existing = worktrees.map { normalizePath(it.path) }
        return project.configFiles.map(::normalizePath).any { file ->
            roots.any { isInside(file, it) } && existing.none { isInside(file, it) }
        }
    }
}

class TakeDownComposeProjectsUseCase(private val source: ComposeProjectSource) {

    suspend operator fun invoke(names: List<String>, removeVolumes: Boolean = false): ComposeDownReport {
        val takenDown = mutableListOf<String>()
        val failures = mutableListOf<ComposeDownFailure>()
        names.distinct().forEach { name ->
            source.down(name, removeVolumes)
                .onSuccess { takenDown += name }
                .onFailure { failures += ComposeDownFailure(name, it.message.orEmpty()) }
        }
        return ComposeDownReport(takenDown, failures)
    }
}
