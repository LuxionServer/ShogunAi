package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.ProjectConfig

class DeleteMergedBranchUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
) {
    suspend operator fun invoke(branch: String): Result<Boolean> = runCatching {
        executor.execute(
            command = listOf("git", "branch", "-d", branch),
            workingDirectory = config.baseRepositoryPath,
        ).isSuccess
    }
}
