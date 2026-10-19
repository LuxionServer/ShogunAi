package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError

class FetchRemoteUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
) {
    suspend operator fun invoke(): Result<Unit> = runCatching {
        val fetch = executor.executeWithin(
            command = listOf("git", "fetch", "--prune", "origin"),
            timeout = CommandTimeouts.NETWORK,
            workingDirectory = config.baseRepositoryPath,
        )
        if (!fetch.isSuccess) {
            throw WorktreeError.GitCommandFailed(fetch.command, fetch.exitCode, fetch.stderr)
        }
    }

    suspend fun hasOrigin(): Boolean = runCatching {
        executor.executeWithin(
            command = listOf("git", "remote", "get-url", "origin"),
            timeout = CommandTimeouts.GIT_READ,
            workingDirectory = config.baseRepositoryPath,
        ).isSuccess
    }.getOrDefault(false)
}
