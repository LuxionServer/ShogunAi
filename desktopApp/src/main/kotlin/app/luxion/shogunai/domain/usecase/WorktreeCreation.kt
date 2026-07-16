package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.joinPath

/**
 * Runs `git worktree add` and copies the project's secret files into the new
 * worktree, rolling back on failure. Shared by [CreateWorktreeUseCase] (new
 * branch) and [CreateWorktreeFromBranchUseCase] (existing branch): both differ
 * only in the `git worktree add` argv and in whether Git created the branch
 * itself (and therefore whether it should be deleted on rollback).
 *
 * @param branchToDeleteOnLfsFailure Branch to `git branch -D` if the Git LFS
 *   post-checkout hook fails, or `null` if the branch already existed before
 *   this call (an existing branch must never be deleted on rollback).
 */
internal suspend fun addWorktreeAndCopySecrets(
    config: ProjectConfig,
    executor: ShellCommandExecutor,
    fileManager: FileManager,
    worktreePath: String,
    branch: String,
    addArgs: List<String>,
    branchToDeleteOnLfsFailure: String?,
): Worktree {
    val add = executor.execute(command = addArgs, workingDirectory = config.baseRepositoryPath)
    if (!add.isSuccess) {
        val alreadyCheckedOutPath = BRANCH_ALREADY_CHECKED_OUT.find(add.stderr)?.groupValues?.get(1)
        if (alreadyCheckedOutPath != null || add.stderr.contains("is already checked out")) {
            throw WorktreeError.BranchAlreadyCheckedOut(branch, alreadyCheckedOutPath)
        }

        if (GIT_LFS_MISSING_MARKERS.any { add.stderr.contains(it) }) {
            // Git LFS's post-checkout hook already left the worktree (and, for a
            // new branch, the branch itself) created on disk before failing: we
            // roll back so a retry doesn't collide with either.
            executor.execute(
                command = listOf("git", "worktree", "remove", "--force", worktreePath),
                workingDirectory = config.baseRepositoryPath,
            )
            branchToDeleteOnLfsFailure?.let {
                executor.execute(
                    command = listOf("git", "branch", "-D", it),
                    workingDirectory = config.baseRepositoryPath,
                )
            }
            throw WorktreeError.GitLfsNotFound
        }
        throw WorktreeError.GitCommandFailed(add.command, add.exitCode, add.stderr)
    }

    copySecretsOrRollback(config, executor, fileManager, worktreePath)

    return Worktree(path = worktreePath, branch = branch)
}

private suspend fun copySecretsOrRollback(
    config: ProjectConfig,
    executor: ShellCommandExecutor,
    fileManager: FileManager,
    worktreePath: String,
) {
    try {
        config.secretFiles.forEach { fileName ->
            fileManager.copy(
                source = config.baseSecretPath(fileName),
                destination = joinPath(worktreePath, fileName),
            )
        }
    } catch (copyError: Exception) {
        // The worktree already exists but is incomplete: we remove it to avoid
        // leaving half-done environments. We ignore the rollback's result.
        executor.execute(
            command = listOf("git", "worktree", "remove", "--force", worktreePath),
            workingDirectory = config.baseRepositoryPath,
        )
        throw WorktreeError.SecretCopyFailed(copyError)
    }
}

/**
 * Git reports missing `git-lfs` in two different ways depending on whether
 * the checked-out commit brings LFS content or not:
 *  - No new LFS content: the checkout succeeds and the `post-checkout` hook
 *    fails when checking the PATH.
 *  - New LFS content: the checkout itself fails invoking the smudge filter,
 *    before the hook gets to run.
 */
internal val GIT_LFS_MISSING_MARKERS = listOf(
    "git-lfs' was not found on your path",
    "git-lfs filter-process: git-lfs: command not found",
)

/** Matches Git's `fatal: '<branch>' is already checked out at '<path>'`. */
private val BRANCH_ALREADY_CHECKED_OUT = Regex("is already checked out at '([^']*)'")
