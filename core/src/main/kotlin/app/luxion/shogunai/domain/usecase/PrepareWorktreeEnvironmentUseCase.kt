package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.WorktreeEnvironment
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.joinPath

fun interface WorktreeEnvironmentPreparer {
    suspend fun prepare(repositoryPath: String, worktreePath: String)

    companion object {
        val NONE = WorktreeEnvironmentPreparer { _, _ -> }
    }
}

class PrepareWorktreeEnvironmentUseCase(
    private val resolve: ResolveWorktreeEnvironmentUseCase,
    private val writeEnvironmentFile: WriteEnvironmentFileUseCase,
) : WorktreeEnvironmentPreparer {

    override suspend fun prepare(repositoryPath: String, worktreePath: String) {
        val environment = resolve(repositoryPath, worktreePath).getOrThrow()
        writeEnvironmentFile(repositoryPath, worktreePath, environment).getOrThrow()
    }
}

class WriteEnvironmentFileUseCase(
    private val executor: ShellCommandExecutor,
    private val fileManager: FileManager,
    private val provisioningSource: RepositoryProvisioningSource,
) {
    suspend operator fun invoke(
        repositoryPath: String,
        worktreePath: String,
        environment: WorktreeEnvironment,
    ): Result<String?> = runCatching {
        val envFile = provisioningSource.read(repositoryPath).environment.envFile ?: return@runCatching null
        val tracked = executor.execute(
            command = listOf("git", "ls-files", "--error-unmatch", "--", envFile),
            workingDirectory = worktreePath,
        )
        if (tracked.isSuccess) throw WorktreeError.EnvironmentFileTracked(envFile)
        val path = joinPath(worktreePath, envFile)
        try {
            fileManager.writeText(path, formatEnvironment(environment, EnvironmentFormat.DOTENV))
        } catch (error: Exception) {
            throw WorktreeError.EnvironmentFileWriteFailed(path, error)
        }
        path
    }
}
