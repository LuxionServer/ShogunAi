package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError

/**
 * Creates a worktree for a branch that already exists locally, instead of
 * creating a new one, so work already started outside a worktree can be
 * moved into its own.
 *
 * Shares the `git worktree add` + secret-copy + rollback logic with
 * [CreateWorktreeUseCase] via [addWorktreeAndCopySecrets]. Unlike that use
 * case, the branch pre-exists, so it must never be deleted on rollback.
 */
class CreateWorktreeFromBranchUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
    private val fileManager: FileManager,
) {
    suspend operator fun invoke(branch: String): Result<Worktree> =
        runCatching {
            val branchName = branch.trim()
            require(branchName.isNotEmpty()) { "Branch must not be blank" }

            if (!fileManager.exists(config.baseRepositoryPath)) {
                throw WorktreeError.BaseRepositoryNotFound(config.baseRepositoryPath)
            }

            val worktreePath = config.worktreePathForBranch(branchName)
            if (fileManager.exists(worktreePath)) {
                throw WorktreeError.WorktreeAlreadyExists(worktreePath)
            }

            if (!localBranchExists(branchName)) {
                throw WorktreeError.BranchNotFound(branchName)
            }

            val missingSecrets = config.secretFiles.filterNot { fileManager.exists(config.baseSecretPath(it)) }
            if (missingSecrets.isNotEmpty()) {
                throw WorktreeError.SecretFileNotFound(missingSecrets, config.baseRepositoryPath)
            }

            addWorktreeAndCopySecrets(
                config = config,
                executor = executor,
                fileManager = fileManager,
                worktreePath = worktreePath,
                branch = branchName,
                addArgs = listOf("git", "worktree", "add", worktreePath, branchName),
                branchToDeleteOnLfsFailure = null,
            )
        }

    private suspend fun localBranchExists(branch: String): Boolean {
        val result = executor.execute(
            command = listOf("git", "branch", "--list", branch),
            workingDirectory = config.baseRepositoryPath,
        )
        return result.isSuccess && result.stdout.isNotBlank()
    }
}
