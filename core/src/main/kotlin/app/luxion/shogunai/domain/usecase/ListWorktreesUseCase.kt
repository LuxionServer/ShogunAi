package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError

class ListWorktreesUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
    private val fileManager: FileManager? = null,
) {
    suspend operator fun invoke(): Result<List<Worktree>> = runCatching {
        if (fileManager?.exists(config.baseRepositoryPath) == false) {
            throw WorktreeError.BaseRepositoryNotFound(config.baseRepositoryPath)
        }
        val result = executor.executeWithin(
            command = listOf("git", "worktree", "list", "--porcelain"),
            timeout = CommandTimeouts.GIT_READ,
            workingDirectory = config.baseRepositoryPath,
        )
        if (!result.isSuccess) {
            throw WorktreeError.GitCommandFailed(result.command, result.exitCode, result.stderr)
        }
        parseWorktreeList(result.stdout).mapIndexed { index, worktree ->
            when {
                sameDirectory(worktree.path, config.baseRepositoryPath) -> worktree.copy(isMain = true)
                index == 0 && normalizePath(worktree.path).contains("/.git/modules/") -> worktree.copy(path = config.baseRepositoryPath, isMain = true)
                else -> worktree
            }
        }
    }
}

internal fun parseWorktreeList(porcelain: String): List<Worktree> {
    val worktrees = mutableListOf<Worktree>()
    var path: String? = null
    var head: String? = null
    var branch: String? = null
    var bare = false
    var locked = false
    var prunable = false

    fun flush() {
        val currentPath = path ?: return
        worktrees += Worktree(
            path = currentPath,
            branch = branch,
            head = head,
            isBare = bare,
            isLocked = locked,
            isPrunable = prunable,
        )
        path = null
        head = null
        branch = null
        bare = false
        locked = false
        prunable = false
    }

    porcelain.lineSequence().forEach { line ->
        when {
            line.startsWith("worktree ") -> {
                flush()
                path = line.removePrefix("worktree ").trim()
            }
            line.startsWith("HEAD ") -> head = line.removePrefix("HEAD ").trim()
            line.startsWith("branch ") ->
                branch = line.removePrefix("branch ").removePrefix("refs/heads/").trim()
            line == "bare" -> bare = true
            line == "detached" -> branch = null
            line == "locked" || line.startsWith("locked ") -> locked = true
            line == "prunable" || line.startsWith("prunable ") -> prunable = true
        }
    }
    flush()
    return worktrees
}
