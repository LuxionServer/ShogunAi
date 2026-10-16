package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError

class ProvisionWorktreeUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
    private val fileManager: FileManager,
    private val provisioningSource: RepositoryProvisioningSource,
) {
    suspend operator fun invoke(worktreePath: String): Result<List<String>> = runCatching {
        val target = normalizePath(worktreePath)
        val worktree = ListWorktreesUseCase(config, executor)().getOrThrow()
            .firstOrNull { normalizePath(it.path) == target }
            ?: throw WorktreeError.NotAWorktree(worktreePath)
        if (worktree.isMain) throw WorktreeError.NotAWorktree(worktreePath)

        val repositoryFiles = checkProvisioningAndResolveFiles(config, executor, fileManager, provisioningSource)
        copyProvisionedFiles(config, fileManager, worktree.path, repositoryFiles)
    }
}
