package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.BranchOption
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.REMOTE_PREFIX
import app.luxion.shogunai.domain.model.WorktreeError

class ListBranchesUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
) {
    suspend operator fun invoke(): Result<List<BranchOption>> = runCatching {
        val refs = executor.executeWithin(
            command = listOf(
                "git", "for-each-ref", "--format=%(refname)", "refs/heads", "refs/remotes/$REMOTE_PREFIX",
            ),
            timeout = CommandTimeouts.GIT_READ,
            workingDirectory = config.baseRepositoryPath,
        )
        if (!refs.isSuccess) {
            throw WorktreeError.GitCommandFailed(refs.command, refs.exitCode, refs.stderr)
        }

        val worktrees = executor.executeWithin(
            command = listOf("git", "worktree", "list", "--porcelain"),
            timeout = CommandTimeouts.GIT_READ,
            workingDirectory = config.baseRepositoryPath,
        )
        if (!worktrees.isSuccess) {
            throw WorktreeError.GitCommandFailed(worktrees.command, worktrees.exitCode, worktrees.stderr)
        }

        val checkedOutBranches = parseWorktreeList(worktrees.stdout).mapNotNull { it.branch }.toSet()

        val names = refs.stdout.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val local = names
            .filter { it.startsWith(LOCAL_REF_PREFIX) }
            .map { it.removePrefix(LOCAL_REF_PREFIX) }
            .map { BranchOption(name = it, isCheckedOut = it in checkedOutBranches) }
        val remote = names
            .filter { it.startsWith(REMOTE_REF_PREFIX) }
            .map { it.removePrefix(REMOTE_REF_PREFIX) }
            .filter { it != "HEAD" }
            .map { BranchOption(name = "$REMOTE_PREFIX$it", isCheckedOut = false, isRemote = true) }
        local + remote
    }

    private companion object {
        const val LOCAL_REF_PREFIX = "refs/heads/"
        const val REMOTE_REF_PREFIX = "refs/remotes/$REMOTE_PREFIX"
    }
}
