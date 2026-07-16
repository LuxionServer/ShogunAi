package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError

/**
 * Lists local branches eligible to become a new worktree: local branches
 * that aren't already checked out anywhere (including the base repository).
 */
class ListEligibleBranchesUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
) {
    suspend operator fun invoke(): Result<List<String>> = runCatching {
        val branches = executor.execute(
            command = listOf("git", "for-each-ref", "--format=%(refname:short)", "refs/heads"),
            workingDirectory = config.baseRepositoryPath,
        )
        if (!branches.isSuccess) {
            throw WorktreeError.GitCommandFailed(branches.command, branches.exitCode, branches.stderr)
        }

        val worktrees = executor.execute(
            command = listOf("git", "worktree", "list", "--porcelain"),
            workingDirectory = config.baseRepositoryPath,
        )
        if (!worktrees.isSuccess) {
            throw WorktreeError.GitCommandFailed(worktrees.command, worktrees.exitCode, worktrees.stderr)
        }

        val checkedOutBranches = parseWorktreeList(worktrees.stdout).mapNotNull { it.branch }.toSet()

        branches.stdout.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it in checkedOutBranches }
            .toList()
    }
}
