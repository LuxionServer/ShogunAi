package app.luxion.shogunai.domain.usecase

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
        resolveRemote(name) ?: resolveLocal(name)

    private suspend fun resolveRemote(name: String): MainBranch? =
        MainBranch(name, "$REMOTE/$name").takeIf { refExists("refs/remotes/$REMOTE/$name") }

    private suspend fun resolveLocal(name: String): MainBranch? =
        MainBranch(name, name).takeIf { refExists("refs/heads/$name") }

    private suspend fun refExists(ref: String): Boolean =
        git("rev-parse", "--verify", "--quiet", ref).isSuccess

    private suspend fun git(vararg args: String) =
        executor.execute(listOf("git", *args), workingDirectory = config.baseRepositoryPath)

    private companion object {
        const val REMOTE = "origin"
        val FALLBACK_BRANCHES = listOf("main", "master")
    }
}
