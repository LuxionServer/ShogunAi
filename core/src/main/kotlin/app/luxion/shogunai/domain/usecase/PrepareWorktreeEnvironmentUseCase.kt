package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.FlutterPubGet
import app.luxion.shogunai.domain.model.PodsPreparation
import app.luxion.shogunai.domain.model.WorktreeEnvironment
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.joinPath

data class PreparedWorktree(val pods: PodsPreparation = PodsPreparation(), val flutter: FlutterPubGet? = null)

fun interface WorktreeEnvironmentPreparer {
    suspend fun prepare(repositoryPath: String, worktreePath: String): PreparedWorktree

    companion object {
        val NONE = WorktreeEnvironmentPreparer { _, _ -> PreparedWorktree() }
    }
}

class PrepareWorktreeEnvironmentUseCase(
    private val resolve: ResolveWorktreeEnvironmentUseCase,
    private val writeEnvironmentFile: WriteEnvironmentFileUseCase,
    private val pods: CocoaPodsPreparer = CocoaPodsPreparer.NONE,
    private val flutterPackages: FlutterPackagesPreparer = FlutterPackagesPreparer.NONE,
) : WorktreeEnvironmentPreparer {

    override suspend fun prepare(repositoryPath: String, worktreePath: String): PreparedWorktree {
        val environment = resolve(repositoryPath, worktreePath).getOrThrow()
        writeEnvironmentFile(repositoryPath, worktreePath, environment).getOrThrow()
        return PreparedWorktree(
            pods = pods.prepare(repositoryPath, worktreePath),
            flutter = flutterPackages.prepare(repositoryPath, worktreePath),
        )
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
        if (!environment.declarationApproved) return@runCatching null
        val envFile = provisioningSource.read(repositoryPath).environment.envFile ?: return@runCatching null
        val tracked = executor.execute(
            command = listOf("git", "ls-files", "--error-unmatch", "--", envFile),
            workingDirectory = worktreePath,
        )
        if (tracked.isSuccess) throw WorktreeError.EnvironmentFileTracked(envFile)
        val path = joinPath(worktreePath, envFile)
        if (!fileManager.isInside(worktreePath, path)) throw WorktreeError.EnvironmentFileOutsideWorktree(envFile)
        try {
            fileManager.writeText(path, formatEnvironment(environment, EnvironmentFormat.DOTENV))
        } catch (error: Exception) {
            throw WorktreeError.EnvironmentFileWriteFailed(path, error)
        }
        path
    }
}
