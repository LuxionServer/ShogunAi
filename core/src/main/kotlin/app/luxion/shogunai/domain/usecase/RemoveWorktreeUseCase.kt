package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError

data class WorktreeRemoval(val herdrFailure: WorktreeError? = null)

class RemoveWorktreeUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
    private val closeHerdrWorkspaces: CloseHerdrWorkspacesUseCase? = null,
) {
    suspend operator fun invoke(
        worktreePath: String,
        branchToDelete: String? = null,
        force: Boolean = false,
    ): Result<WorktreeRemoval> = runCatching {
        val workspaces = closeHerdrWorkspaces?.openWorkspaces(listOf(worktreePath)).orEmpty()
        val removeCommand = buildList {
            addAll(listOf("git", "worktree", "remove"))
            if (force) add("--force")
            add(worktreePath)
        }
        val remove = executor.execute(removeCommand, workingDirectory = config.baseRepositoryPath)
        if (!remove.isSuccess) {
            throw WorktreeError.GitCommandFailed(remove.command, remove.exitCode, remove.stderr)
        }

        val herdrFailure = closeHerdrWorkspaces?.close(workspaces, listOf(worktreePath))?.firstOrNull()?.error

        if (branchToDelete != null) {
            val deleteBranch = executor.execute(
                command = listOf("git", "branch", "-d", "--", branchToDelete),
                workingDirectory = config.baseRepositoryPath,
            )
            if (!deleteBranch.isSuccess) {
                throw WorktreeError.GitCommandFailed(
                    deleteBranch.command,
                    deleteBranch.exitCode,
                    deleteBranch.stderr,
                )
            }
        }
        WorktreeRemoval(herdrFailure)
    }
}
