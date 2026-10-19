package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.WorktreeError

class ResolveStartPointUseCase(
    private val executor: ShellCommandExecutor,
) {
    suspend operator fun invoke(directory: String, ref: String): Result<String> = runCatching {
        if (isOptionLikeRef(ref)) throw WorktreeError.InvalidRef(ref)
        val result = executor.execute(
            command = listOf("git", "rev-parse", "--verify", "--quiet", "$ref^{commit}"),
            workingDirectory = directory,
        )
        val commit = result.stdout.trim()
        if (!result.isSuccess || commit.isEmpty()) {
            throw WorktreeError.StartPointNotFound(ref)
        }
        commit
    }
}
