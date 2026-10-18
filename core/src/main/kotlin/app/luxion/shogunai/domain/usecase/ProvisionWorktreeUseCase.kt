package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.ProvisionedFile
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError

class ProvisionWorktreeUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
    private val fileManager: FileManager,
    private val provisioningSource: RepositoryProvisioningSource,
    private val environment: WorktreeEnvironmentPreparer = WorktreeEnvironmentPreparer.NONE,
) {
    suspend operator fun invoke(worktreePath: String): Result<List<ProvisionedFile>> = runCatching {
        val worktree = findLinkedWorktree(config, executor, worktreePath)
        val provisioned = checkProvisioningAndResolveFiles(config, executor, fileManager, provisioningSource)
        applyProvisionedFiles(config, fileManager, worktree.path, provisioned)
            .also { environment.prepare(config.baseRepositoryPath, worktree.path) }
            .also { runPostCreateCommands(executor, worktree.path, provisioned.postCreateCommands) }
    }
}

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
