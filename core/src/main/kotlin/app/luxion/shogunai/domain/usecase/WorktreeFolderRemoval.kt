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
    private val stopHeadlessApp: StopHeadlessAppUseCase? = null,
    private val releaseDesktopTurn: ReleaseDesktopTurnUseCase? = null,
    private val closeOrcaTerminals: CloseOrcaTerminalsUseCase? = null,
) {
    suspend fun remove(path: String, force: Boolean, workspaceId: String?, hasOrcaTerminals: Boolean): HolderClosing {
        val headlessAppFailure = stopHeadlessApp?.beforeRemoving(path)
        releaseDesktopTurn?.onWorktreeRemoved(path)
        return removeFolder(path, force, workspaceId, hasOrcaTerminals).copy(headlessAppFailure = headlessAppFailure)
    }

    private suspend fun removeFolder(path: String, force: Boolean, workspaceId: String?, hasOrcaTerminals: Boolean): HolderClosing {
        var removeCommand = removeCommand(path, forces = if (force) 1 else 0)
        var first = git(removeCommand)
        if (force && isLockedWorktreeFailure(first.stderr)) {
            removeCommand = removeCommand(path, forces = 2)
            first = git(removeCommand)
        }
        if (first.isSuccess) return HolderClosing()
        if (isLockedWorktreeFailure(first.stderr)) throw worktreeLocked(path, first)
        if (isNotAWorkingTreeFailure(first.stderr) || isPathTooLongFailure(first.stderr)) {
            finishLeftover(path, first)
            return HolderClosing()
        }
        if (!isFolderInUseFailure(first.stderr)) throw first.toError()

        val herdrClosing = closeHerdrWorkspaces?.closeHolders(path, workspaceId) ?: HolderClosing()
        val orcaFailure = if (hasOrcaTerminals) closeOrcaTerminals?.closeOne(path) else null
        val closing = herdrClosing.copy(
            closedOrcaTerminals = hasOrcaTerminals && closeOrcaTerminals != null && orcaFailure == null,
            orcaFailure = orcaFailure,
        )
        val retry = git(removeCommand)
        when {
            retry.isSuccess -> Unit
            isNotAWorkingTreeFailure(retry.stderr) -> finishLeftover(path, first)
            isPathTooLongFailure(retry.stderr) -> finishLeftover(path, retry)
            isFolderInUseFailure(retry.stderr) -> throw folderInUse(path, retry, unregistered = !isRegistered(path))
            else -> throw retry.toError()
        }
        return closing
    }

    private fun removeCommand(path: String, forces: Int) =
        listOf("git", "worktree", "remove") + List(forces) { "--force" } + path

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

    private fun worktreeLocked(path: String, failure: CommandResult) =
        WorktreeError.WorktreeLocked(path, lockReason(failure.stderr), failure.command, failure.exitCode, failure.stderr)

    private suspend fun git(command: List<String>) = executor.execute(command, workingDirectory = config.baseRepositoryPath)

    private fun CommandResult.toError() = WorktreeError.GitCommandFailed(command, exitCode, stderr)
}
