package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.joinPath

/**
 * Creates a worktree and leaves it ready to build the Android project.
 *
 * Orchestrates, in sequence:
 *  1. Upfront validations (base repo exists, destination free, secrets present).
 *  2. `git worktree add <path> -b <branch>`.
 *  3. Copying the secret files into the new directory.
 *
 * Validations happen *before* touching Git so we don't leave a half-done
 * worktree we couldn't later complete. If the copy fails after creating the
 * worktree, it's rolled back with a `git worktree remove --force` (best-effort).
 */
class CreateWorktreeUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
    private val fileManager: FileManager,
) {
    suspend operator fun invoke(taskId: String, branchType: BranchType): Result<Worktree> =
        runCatching {
            val id = taskId.trim()
            require(id.isNotEmpty()) { "Task id must not be blank" }

            if (!fileManager.exists(config.baseRepositoryPath)) {
                throw WorktreeError.BaseRepositoryNotFound(config.baseRepositoryPath)
            }

            val worktreePath = config.worktreePathFor(id)
            if (fileManager.exists(worktreePath)) {
                throw WorktreeError.WorktreeAlreadyExists(worktreePath)
            }

            val missingSecrets = config.secretFiles.filterNot { fileManager.exists(config.baseSecretPath(it)) }
            if (missingSecrets.isNotEmpty()) {
                throw WorktreeError.SecretFileNotFound(missingSecrets, config.baseRepositoryPath)
            }

            val branch = "${branchType.prefix}/$id"
            val add = executor.execute(
                command = listOf("git", "worktree", "add", worktreePath, "-b", branch),
                workingDirectory = config.baseRepositoryPath,
            )
            if (!add.isSuccess) {
                if (GIT_LFS_MISSING_MARKERS.any { add.stderr.contains(it) }) {
                    // Git LFS's post-checkout hook already left the worktree and branch
                    // created on disk before failing: we roll back both so a retry
                    // doesn't collide with either the existing directory or branch.
                    executor.execute(
                        command = listOf("git", "worktree", "remove", "--force", worktreePath),
                        workingDirectory = config.baseRepositoryPath,
                    )
                    executor.execute(
                        command = listOf("git", "branch", "-D", branch),
                        workingDirectory = config.baseRepositoryPath,
                    )
                    throw WorktreeError.GitLfsNotFound
                }
                throw WorktreeError.GitCommandFailed(add.command, add.exitCode, add.stderr)
            }

            copySecretsOrRollback(worktreePath)

            Worktree(path = worktreePath, branch = branch)
        }

    private suspend fun copySecretsOrRollback(worktreePath: String) {
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

    private companion object {
        /**
         * Git reports missing `git-lfs` in two different ways depending on
         * whether the checked-out commit brings LFS content or not:
         *  - No new LFS content: the checkout succeeds and the `post-checkout`
         *    hook fails when checking the PATH.
         *  - New LFS content: the checkout itself fails invoking the smudge
         *    filter, before the hook gets to run.
         */
        val GIT_LFS_MISSING_MARKERS = listOf(
            "git-lfs' was not found on your path",
            "git-lfs filter-process: git-lfs: command not found",
        )
    }
}
