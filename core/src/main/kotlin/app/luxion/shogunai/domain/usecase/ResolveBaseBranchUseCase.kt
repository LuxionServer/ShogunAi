package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor

class ResolveBaseBranchUseCase(
    private val executor: ShellCommandExecutor,
) {
    suspend operator fun invoke(directory: String, ref: String): String? {
        if (isOptionLikeRef(ref)) return null
        val result = executor.execute(
            command = listOf("git", "rev-parse", "--verify", "--quiet", "--symbolic-full-name", ref),
            workingDirectory = directory,
        )
        if (!result.isSuccess) return null
        return branchOfFullRef(result.stdout.trim())
    }
}

internal fun branchOfFullRef(fullRef: String): String? {
    val branch = when {
        fullRef.startsWith(LOCAL_BRANCHES) -> fullRef.removePrefix(LOCAL_BRANCHES)
        fullRef.startsWith(REMOTE_BRANCHES) -> fullRef.removePrefix(REMOTE_BRANCHES).substringAfter('/', "")
        else -> ""
    }
    return branch.takeIf { it.isNotEmpty() && it != "HEAD" }
}

private const val LOCAL_BRANCHES = "refs/heads/"
private const val REMOTE_BRANCHES = "refs/remotes/"
