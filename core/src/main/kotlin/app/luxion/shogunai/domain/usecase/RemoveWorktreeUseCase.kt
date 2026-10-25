package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError

data class WorktreeRemoval(
    val herdrFailure: WorktreeError? = null,
    val branch: BranchDeletion? = null,
    val headlessAppFailure: WorktreeError? = null,
)

class RemoveWorktreeUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
    private val closeHerdrWorkspaces: CloseHerdrWorkspacesUseCase? = null,
    private val fileManager: FileManager? = null,
    private val stopHeadlessApp: StopHeadlessAppUseCase? = null,
) {
    private val folderRemoval = WorktreeFolderRemoval(config, executor, fileManager, closeHerdrWorkspaces, stopHeadlessApp)

    suspend operator fun invoke(
        worktreePath: String,
        branchToDelete: String? = null,
        force: Boolean = false,
        forceBranchDeletion: Boolean = false,
    ): Result<WorktreeRemoval> = runCatching {
        val workspaces = closeHerdrWorkspaces?.openWorkspaces(listOf(worktreePath)).orEmpty()
        val closing = folderRemoval.remove(worktreePath, force, workspaces[worktreePath])
        val remaining = if (closing.closedWorkspace) emptyMap() else workspaces
        val herdrFailure = closeHerdrWorkspaces?.close(remaining, listOf(worktreePath))?.firstOrNull()?.error
            ?: closing.failure

        val branch = branchToDelete?.let { deleteBranch(it, forceBranchDeletion) }
        WorktreeRemoval(herdrFailure, branch, closing.headlessAppFailure)
    }

    private suspend fun deleteBranch(branch: String, forced: Boolean): BranchDeletion {
        if (isOptionLikeRef(branch)) return BranchDeletion.KeptFailed(WorktreeError.InvalidRef(branch).message.orEmpty())
        if (forced) return forceDeleteBranch(executor, config, branch)
        val mainBranch = ResolveMainBranchUseCase(config, executor)().getOrNull()
        return deleteBranchIfContained(executor, config, branch, mainBranch)
    }
}
