package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.MainBranch
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.WorktreeStatus

data class CleanupReport(
    val removed: List<String>,
    val keptBranches: List<String>,
    val failures: List<CleanupFailure>,
    val herdrFailures: List<CleanupFailure> = emptyList(),
)

data class CleanupFailure(val path: String, val error: WorktreeError)

fun selectCleanupCandidates(worktrees: List<Worktree>, statuses: Map<String, WorktreeStatus>): List<Worktree> =
    worktrees.filter { worktree ->
        !worktree.isMain && !worktree.isLocked && (worktree.isPrunable || statuses[worktree.path].isCleanAndMerged())
    }

private fun WorktreeStatus?.isCleanAndMerged(): Boolean =
    this != null && isMerged && uncommittedChanges == 0

class CleanUpWorktreesUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
    private val closeHerdrWorkspaces: CloseHerdrWorkspacesUseCase? = null,
) {
    suspend operator fun invoke(
        candidates: List<Worktree>,
        mainBranch: MainBranch?,
        deleteBranches: Boolean,
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
                if (!deleteIfContained(branch, mainBranch)) keptBranches += branch
            }
        }

        CleanupReport(
            removed = removedWorktrees.map { it.path },
            keptBranches = keptBranches,
            failures = failures,
            herdrFailures = herdrFailures,
        )
    }

    private suspend fun deleteIfContained(branch: String, mainBranch: MainBranch?): Boolean {
        if (mainBranch == null) return false
        if (!git("merge-base", "--is-ancestor", branch, mainBranch.ref).isSuccess) return false
        return git("branch", "-D", "--", branch).isSuccess
    }

    private suspend fun git(vararg args: String) =
        executor.execute(listOf("git", *args), workingDirectory = config.baseRepositoryPath)

    private fun CommandResult.toError() =
        WorktreeError.GitCommandFailed(command, exitCode, stderr)
}
