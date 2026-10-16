package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.WorktreeError

class ResolveRepositoryUseCase(
    private val executor: ShellCommandExecutor,
) {
    suspend operator fun invoke(directory: String): Result<String> = runCatching {
        val result = executor.execute(
            command = listOf("git", "rev-parse", "--path-format=absolute", "--git-common-dir"),
            workingDirectory = directory,
        )
        val commonDir = normalizePath(result.stdout.trim())
        if (!result.isSuccess || !commonDir.endsWith("/.git")) {
            throw WorktreeError.RepositoryNotFound(directory)
        }
        commonDir.removeSuffix("/.git")
    }
}
