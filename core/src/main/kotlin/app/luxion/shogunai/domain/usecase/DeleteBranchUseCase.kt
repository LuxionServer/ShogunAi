package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError

class DeleteBranchUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
) {
    suspend operator fun invoke(branch: String): Result<BranchDeletion> = runCatching {
        if (isOptionLikeRef(branch)) throw WorktreeError.InvalidRef(branch)
        forceDeleteBranch(executor, config, branch)
    }
}
