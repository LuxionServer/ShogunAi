package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.ProvisioningRecordStore
import app.luxion.shogunai.domain.io.RepositoryApproval
import app.luxion.shogunai.domain.io.RepositoryTrust
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.PodsPreparation
import app.luxion.shogunai.domain.model.ProvisionedFile
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError

class ProvisionWorktreeUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
    private val fileManager: FileManager,
    private val provisioningSource: RepositoryProvisioningSource,
    private val environment: WorktreeEnvironmentPreparer = WorktreeEnvironmentPreparer.NONE,
    private val trust: RepositoryTrust = RepositoryTrust.NONE,
    private val approval: RepositoryApproval = RepositoryApproval.SKIP,
    private val records: ProvisioningRecordStore = ProvisioningRecordStore.NONE,
) {
    suspend operator fun invoke(worktreePath: String): Result<ProvisionResult> = runCatching {
        val worktree = findLinkedWorktree(config, executor, worktreePath)
        val provisioned = checkProvisioningAndResolveFiles(config, executor, fileManager, provisioningSource)
        val applied = applyProvisionedFiles(config, fileManager, records, worktree.path, provisioned)
        val approved = settleApproval(config, trust, approval, provisioned.approvalSubject)
        val prepared = environment.prepare(config.baseRepositoryPath, worktree.path)
        val skipped = runPostCreateCommandsIfApproved(executor, approved, worktree.path, provisioned.approvalSubject)
        ProvisionResult(
            files = applied.files,
            skippedPostCreateCommands = skipped.commands,
            skippedVariables = skipped.variables,
            removed = applied.removed,
            skippedLinks = provisioned.skippedLinks,
            pods = prepared.pods,
        )
    }
}

data class ProvisionResult(
    val files: List<ProvisionedFile>,
    val skippedPostCreateCommands: List<List<String>> = emptyList(),
    val skippedVariables: List<String> = emptyList(),
    val removed: List<String> = emptyList(),
    val skippedLinks: List<String> = emptyList(),
    val pods: PodsPreparation = PodsPreparation(),
)

internal suspend fun findLinkedWorktree(
    config: ProjectConfig,
    executor: ShellCommandExecutor,
    worktreePath: String,
): Worktree {
    val target = normalizePath(worktreePath)
    val worktree = ListWorktreesUseCase(config, executor)().getOrThrow()
        .firstOrNull { normalizePath(it.path) == target }
        ?: throw WorktreeError.NotAWorktree(worktreePath)
    if (worktree.isMain) throw WorktreeError.NotAWorktree(worktreePath)
    return worktree
}
