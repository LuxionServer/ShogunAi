package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.ComposeProjectSource
import app.luxion.shogunai.domain.model.ComposeDownReport
import app.luxion.shogunai.domain.model.MainBranch
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.WorktreeStatus

data class CleanupReport(
    val removed: List<String>,
    val keptBranches: List<String>,
    val failures: List<CleanupFailure>,
    val herdrFailures: List<CleanupFailure> = emptyList(),
    val composeDown: ComposeDownReport? = null,
    val composeUnavailable: String? = null,
)

data class CleanupFailure(val path: String, val error: WorktreeError)

data class CleanupSelection(
    val candidates: List<Worktree>,
    val keptForRunningAgent: Map<Worktree, List<RunningAgent>> = emptyMap(),
)

fun selectCleanup(
    worktrees: List<Worktree>,
    statuses: Map<String, WorktreeStatus>,
    runningAgents: Map<String, List<RunningAgent>>? = null,
): CleanupSelection {
    val qualifying = worktrees.filter { worktree ->
        !worktree.isMain && !worktree.isLocked && (worktree.isPrunable || statuses[worktree.path].isCleanAndMerged())
    }
    val (kept, candidates) = qualifying.partition { !it.isPrunable && runningAgents?.get(it.path).orEmpty().isNotEmpty() }
    return CleanupSelection(candidates, kept.associateWith { runningAgents?.get(it.path).orEmpty() })
}

fun selectCleanupCandidates(
    worktrees: List<Worktree>,
    statuses: Map<String, WorktreeStatus>,
    runningAgents: Map<String, List<RunningAgent>>? = null,
): List<Worktree> = selectCleanup(worktrees, statuses, runningAgents).candidates

private fun WorktreeStatus?.isCleanAndMerged(): Boolean =
    this != null && isMerged && uncommittedChanges == 0

class CleanUpWorktreesUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
    private val closeHerdrWorkspaces: CloseHerdrWorkspacesUseCase? = null,
    private val composeProjects: ComposeProjectSource? = null,
) {
    suspend operator fun invoke(
        candidates: List<Worktree>,
        mainBranch: MainBranch?,
        deleteBranches: Boolean,
        downComposeProjects: Boolean = false,
    ): Result<CleanupReport> = runCatching {
        val workspaces = closeHerdrWorkspaces?.openWorkspaces(candidates.map { it.path }).orEmpty()
        val keptBranches = mutableListOf<String>()
        val failures = mutableListOf<CleanupFailure>()

        val removedWorktrees = mutableListOf<Worktree>()

        candidates.filterNot { it.isPrunable }.forEach { worktree ->
            val removal = git("worktree", "remove", worktree.path)
            if (removal.isSuccess) {
                removedWorktrees += worktree
            } else {
                failures += CleanupFailure(worktree.path, removal.toError())
            }
        }

        val missing = candidates.filter { it.isPrunable }
        if (missing.isNotEmpty()) {
            val prune = git("worktree", "prune")
            if (prune.isSuccess) {
                removedWorktrees += missing
            } else {
                failures += missing.map { CleanupFailure(it.path, prune.toError()) }
            }
        }

        val herdrFailures = closeHerdrWorkspaces?.close(workspaces, removedWorktrees.map { it.path }).orEmpty()

        if (deleteBranches) {
            removedWorktrees.mapNotNull { it.branch }.forEach { branch ->
                if (deleteBranchIfContained(executor, config, branch, mainBranch) != BranchDeletion.Deleted) keptBranches += branch
            }
        }

        val compose = if (downComposeProjects) takeDownComposeProjects(removedWorktrees) else null

        CleanupReport(
            removed = removedWorktrees.map { it.path },
            keptBranches = keptBranches,
            failures = failures,
            herdrFailures = herdrFailures,
            composeDown = compose?.getOrNull(),
            composeUnavailable = compose?.exceptionOrNull()?.let { (it as? WorktreeError.DockerUnavailable)?.reason ?: it.message.orEmpty() },
        )
    }

    private suspend fun takeDownComposeProjects(removed: List<Worktree>): Result<ComposeDownReport>? {
        val source = composeProjects ?: return null
        if (removed.isEmpty()) return null
        return runCatching {
            val names = removed.map { worktreeComposeProjectName(config.baseRepositoryPath, it.path) }.toSet()
            val existing = source.list().map { it.name }.filter { it in names }
            TakeDownComposeProjectsUseCase(source)(existing)
        }
    }

    private suspend fun git(vararg args: String) =
        executor.execute(listOf("git", *args), workingDirectory = config.baseRepositoryPath)

    private fun CommandResult.toError() =
        WorktreeError.GitCommandFailed(command, exitCode, stderr)
}
