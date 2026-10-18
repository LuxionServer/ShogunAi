package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError

class CreateWorktreeFromBranchUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
    private val fileManager: FileManager,
    private val provisioningSource: RepositoryProvisioningSource,
    private val environment: WorktreeEnvironmentPreparer = WorktreeEnvironmentPreparer.NONE,
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

            val provisioned = checkProvisioningAndResolveFiles(config, executor, fileManager, provisioningSource)

            addWorktreeAndCopySecrets(
                config = config,
                executor = executor,
                fileManager = fileManager,
                worktreePath = worktreePath,
                branch = branchName,
                addArgs = listOf("git", "worktree", "add", worktreePath, branchName),
                branchToDeleteOnLfsFailure = null,
                provisioned = provisioned,
                environment = environment,
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
