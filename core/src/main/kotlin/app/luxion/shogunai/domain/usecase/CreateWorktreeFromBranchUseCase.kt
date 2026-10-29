package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.RepositoryApproval
import app.luxion.shogunai.domain.io.ProvisioningRecordStore
import app.luxion.shogunai.domain.io.RepositoryTrust
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.REMOTE_PREFIX
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError

class CreateWorktreeFromBranchUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
    private val fileManager: FileManager,
    private val provisioningSource: RepositoryProvisioningSource,
    private val environment: WorktreeEnvironmentPreparer = WorktreeEnvironmentPreparer.NONE,
    private val trust: RepositoryTrust = RepositoryTrust.NONE,
    private val approval: RepositoryApproval = RepositoryApproval.SKIP,
    private val records: ProvisioningRecordStore = ProvisioningRecordStore.NONE,
) {
    suspend operator fun invoke(branch: String): Result<Worktree> =
        runCatching {
            val branchName = branch.trim()
            require(branchName.isNotEmpty()) { "Branch must not be blank" }
            if (isOptionLikeRef(branchName)) throw WorktreeError.InvalidRef(branchName)

            if (!fileManager.exists(config.baseRepositoryPath)) {
                throw WorktreeError.BaseRepositoryNotFound(config.baseRepositoryPath)
            }

            requireFreeWorktreePath(branchName)
            val source = resolveSource(branchName) ?: throw WorktreeError.BranchNotFound(branchName)
            val worktreePath = requireFreeWorktreePath(source.localName)

            val provisioned = checkLocalFilesAndResolve(config, executor, fileManager, provisioningSource)

            addWorktreeAndCopySecrets(
                config = config,
                executor = executor,
                fileManager = fileManager,
                worktreePath = worktreePath,
                branch = source.localName,
                addArgs = source.addArgs(worktreePath),
                createdBranch = source.localName.takeIf { source.isRemote },
                provisioned = provisioned,
                environment = environment,
                trust = trust,
                approval = approval,
                records = records,
            )
        }

    private fun requireFreeWorktreePath(branch: String): String {
        val worktreePath = config.worktreePathForBranch(branch)
        if (fileManager.exists(worktreePath)) {
            throw WorktreeError.WorktreeAlreadyExists(worktreePath)
        }
        return worktreePath
    }

    private suspend fun resolveSource(name: String): BranchSource? = when {
        refExists("refs/heads/$name") -> BranchSource(name, isRemote = false)
        name.startsWith(REMOTE_PREFIX) && refExists("refs/remotes/$name") ->
            BranchSource(name.removePrefix(REMOTE_PREFIX), isRemote = true)
        refExists("refs/remotes/$REMOTE_PREFIX$name") -> BranchSource(name, isRemote = true)
        else -> null
    }

    private suspend fun refExists(ref: String): Boolean =
        executor.execute(
            command = listOf("git", "show-ref", "--verify", "--quiet", ref),
            workingDirectory = config.baseRepositoryPath,
        ).isSuccess

    private class BranchSource(val localName: String, val isRemote: Boolean) {
        fun addArgs(worktreePath: String): List<String> =
            if (isRemote) {
                listOf("git", "worktree", "add", "--track", "-b", localName, worktreePath, "$REMOTE_PREFIX$localName")
            } else {
                listOf("git", "worktree", "add", worktreePath, localName)
            }
    }
}
