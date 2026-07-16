package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError

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
            addWorktreeAndCopySecrets(
                config = config,
                executor = executor,
                fileManager = fileManager,
                worktreePath = worktreePath,
                branch = branch,
                addArgs = listOf("git", "worktree", "add", worktreePath, "-b", branch),
                branchToDeleteOnLfsFailure = branch,
            )
        }
}
