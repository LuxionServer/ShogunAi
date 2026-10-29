package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.MainBranch
import app.luxion.shogunai.domain.model.ProjectConfig

class ResolveMainBranchUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
) {
    suspend operator fun invoke(): Result<MainBranch?> = runCatching {
        val configured = config.mainBranch
        if (configured != null) {
            resolve(configured)
        } else {
            remoteDefaultBranch()?.let { resolve(it) }
                ?: FALLBACK_BRANCHES.firstNotNullOfOrNull { resolveLocal(it) }
        }
    }

    private suspend fun remoteDefaultBranch(): String? {
        val result = git("symbolic-ref", "--quiet", "--short", "refs/remotes/$REMOTE/HEAD")
        if (!result.isSuccess) return null
        return result.stdout.trim().removePrefix("$REMOTE/").ifEmpty { null }
    }

    private suspend fun resolve(name: String): MainBranch? =
        resolveBranchRef(executor, config.baseRepositoryPath, name)

    private suspend fun resolveLocal(name: String): MainBranch? =
        localBranchRef(executor, config.baseRepositoryPath, name)

    private suspend fun git(vararg args: String) =
        executor.executeWithin(listOf("git", *args), CommandTimeouts.GIT_READ, workingDirectory = config.baseRepositoryPath)

    private companion object {
        private val FALLBACK_BRANCHES = listOf("main", "master")
    }
}

internal suspend fun resolveBranchRef(executor: ShellCommandExecutor, directory: String, name: String): MainBranch? =
    MainBranch(name, "$REMOTE/$name").takeIf { refExists(executor, directory, "refs/remotes/$REMOTE/$name") }
        ?: localBranchRef(executor, directory, name)

private suspend fun localBranchRef(executor: ShellCommandExecutor, directory: String, name: String): MainBranch? =
    MainBranch(name, name).takeIf { refExists(executor, directory, "refs/heads/$name") }

private suspend fun refExists(executor: ShellCommandExecutor, directory: String, ref: String): Boolean =
    executor.executeWithin(listOf("git", "rev-parse", "--verify", "--quiet", ref), CommandTimeouts.GIT_READ, workingDirectory = directory).isSuccess

private const val REMOTE = "origin"
