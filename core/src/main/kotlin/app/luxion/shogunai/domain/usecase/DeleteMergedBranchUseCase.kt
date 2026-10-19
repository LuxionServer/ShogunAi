package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError

class DeleteMergedBranchUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
) {
    suspend operator fun invoke(branch: String): Result<Boolean> = runCatching {
        if (isOptionLikeRef(branch)) throw WorktreeError.InvalidRef(branch)
        executor.execute(
            command = listOf("git", "branch", "-d", "--", branch),
            workingDirectory = config.baseRepositoryPath,
        ).isSuccess
    }
}
