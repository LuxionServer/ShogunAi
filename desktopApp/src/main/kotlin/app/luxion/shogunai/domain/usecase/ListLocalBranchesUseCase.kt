package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.BranchOption
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError

/**
 * Lists local branches, flagging the ones already checked out somewhere
 * (including the base repository) since those can't become a new worktree.
 */
class ListLocalBranchesUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
) {
    suspend operator fun invoke(): Result<List<BranchOption>> = runCatching {
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
            .map { BranchOption(name = it, isCheckedOut = it in checkedOutBranches) }
            .toList()
    }
}
