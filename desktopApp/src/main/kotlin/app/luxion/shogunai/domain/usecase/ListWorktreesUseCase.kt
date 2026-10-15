package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError

class ListWorktreesUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
) {
    suspend operator fun invoke(): Result<List<Worktree>> = runCatching {
        val result = executor.execute(
            command = listOf("git", "worktree", "list", "--porcelain"),
            workingDirectory = config.baseRepositoryPath,
        )
        if (!result.isSuccess) {
            throw WorktreeError.GitCommandFailed(result.command, result.exitCode, result.stderr)
        }
        parseWorktreeList(result.stdout)
            .map { it.copy(isMain = it.path == config.baseRepositoryPath) }
    }
}

internal fun parseWorktreeList(porcelain: String): List<Worktree> {
    val worktrees = mutableListOf<Worktree>()
    var path: String? = null
    var head: String? = null
    var branch: String? = null
    var bare = false

    fun flush() {
        val currentPath = path ?: return
        worktrees += Worktree(path = currentPath, branch = branch, head = head, isBare = bare)
        path = null
        head = null
        branch = null
        bare = false
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
        }
    }
    flush()
    return worktrees
}
