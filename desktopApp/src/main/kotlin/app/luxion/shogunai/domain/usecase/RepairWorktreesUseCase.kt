package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.joinPath

/**
 * Re-links the base repository and its worktrees after either of them has been
 * moved on disk, via `git worktree repair`.
 *
 * Git stores absolute paths in both directions (`<worktree>/.git` points to the
 * base repository, and the base repository's `.git/worktrees/<name>/gitdir`
 * points to the worktree), so moving any of them leaves the links broken.
 * Running `git worktree repair` from the moved base repository fixes the
 * worktree → base links; worktrees that moved too (because they lived under
 * the previous [ProjectConfig.worktreesRoot]) are passed explicitly with their
 * new path so Git can also fix the base → worktree links.
 */
class RepairWorktreesUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
    private val fileManager: FileManager,
) {
    /**
     * @param previousWorktreesRoot The worktrees root before the move, used to
     *   locate worktrees that moved along with it.
     */
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

        // The first entry is always the base repository itself.
        val movedWorktreePaths = parseWorktreeList(list.stdout).drop(1)
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

    /** New path of [oldPath] if it was under [previousWorktreesRoot], or `null` otherwise. */
    private fun relocatedPath(oldPath: String, previousWorktreesRoot: String): String? {
        val previousRoot = previousWorktreesRoot.trimEnd('/')
        if (previousRoot.isEmpty() || previousRoot == config.worktreesRoot.trimEnd('/')) return null
        if (!oldPath.startsWith("$previousRoot/")) return null
        return joinPath(config.worktreesRoot, oldPath.removePrefix("$previousRoot/"))
    }
}
