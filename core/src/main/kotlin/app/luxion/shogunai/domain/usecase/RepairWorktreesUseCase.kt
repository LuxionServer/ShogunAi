package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.joinPath

class RepairWorktreesUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
    private val fileManager: FileManager,
) {
    suspend operator fun invoke(previousWorktreesRoot: String): Result<Unit> = runCatching {
        if (!fileManager.exists(config.baseRepositoryPath)) {
            throw WorktreeError.BaseRepositoryNotFound(config.baseRepositoryPath)
        }

        val list = executor.execute(
            command = listOf("git", "worktree", "list", "--porcelain"),
            workingDirectory = config.baseRepositoryPath,
        )
        if (!list.isSuccess) {
            throw WorktreeError.GitCommandFailed(list.command, list.exitCode, list.stderr)
        }

        val movedWorktreePaths = parseWorktreeList(list.stdout).withoutBaseRepositoryEntry()
            .filterNot { fileManager.exists(it.path) }
            .mapNotNull { relocatedPath(it.path, previousWorktreesRoot) }
            .filter { fileManager.exists(it) }

        val repair = executor.execute(
            command = listOf("git", "worktree", "repair") + movedWorktreePaths,
            workingDirectory = config.baseRepositoryPath,
        )
        if (!repair.isSuccess) {
            throw WorktreeError.GitCommandFailed(repair.command, repair.exitCode, repair.stderr)
        }
    }

    private fun List<Worktree>.withoutBaseRepositoryEntry(): List<Worktree> = drop(1)

    private fun relocatedPath(oldPath: String, previousWorktreesRoot: String): String? {
        val previousRoot = previousWorktreesRoot.trimEnd('/')
        if (previousRoot.isEmpty() || previousRoot == config.worktreesRoot.trimEnd('/')) return null
        if (!oldPath.startsWith("$previousRoot/")) return null
        return joinPath(config.worktreesRoot, oldPath.removePrefix("$previousRoot/"))
    }
}
