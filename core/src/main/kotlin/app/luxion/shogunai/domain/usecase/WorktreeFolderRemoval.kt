package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError
import java.io.IOException

internal class WorktreeFolderRemoval(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
    private val fileManager: FileManager?,
    private val closeHerdrWorkspaces: CloseHerdrWorkspacesUseCase?,
) {
    suspend fun remove(path: String, force: Boolean, workspaceId: String?): HolderClosing {
        val removeCommand = buildList {
            addAll(listOf("git", "worktree", "remove"))
            if (force) add("--force")
            add(path)
        }
        val first = git(removeCommand)
        if (first.isSuccess) return HolderClosing()
        if (isNotAWorkingTreeFailure(first.stderr)) {
            finishLeftover(path, first)
            return HolderClosing()
        }
        if (!isFolderInUseFailure(first.stderr)) throw first.toError()

        val closing = closeHerdrWorkspaces?.closeHolders(path, workspaceId) ?: HolderClosing()
        val retry = git(removeCommand)
        when {
            retry.isSuccess -> Unit
            isNotAWorkingTreeFailure(retry.stderr) -> finishLeftover(path, first)
            isFolderInUseFailure(retry.stderr) -> throw folderInUse(path, retry, unregistered = !isRegistered(path))
            else -> throw retry.toError()
        }
        return closing
    }

    private suspend fun finishLeftover(path: String, failure: CommandResult) {
        val files = fileManager ?: throw failure.toError()
        git(listOf("git", "worktree", "prune"))
        if (!files.exists(path)) return
        if (sameDirectory(path, config.baseRepositoryPath) || files.isDirectory("${path.trimEnd('/', '\\')}/.git")) throw failure.toError()
        try {
            files.deleteRecursively(path)
        } catch (error: IOException) {
            throw folderInUse(path, failure, unregistered = true)
        }
    }

    private suspend fun isRegistered(path: String): Boolean =
        ListWorktreesUseCase(config, executor)().getOrNull()?.any { sameDirectory(it.path, path) } ?: true

    private fun folderInUse(path: String, failure: CommandResult, unregistered: Boolean) =
        WorktreeError.WorktreeFolderInUse(path, unregistered, failure.command, failure.exitCode, failure.stderr)

    private suspend fun git(command: List<String>) = executor.execute(command, workingDirectory = config.baseRepositoryPath)

    private fun CommandResult.toError() = WorktreeError.GitCommandFailed(command, exitCode, stderr)
}
