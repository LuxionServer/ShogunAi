package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor

internal suspend fun holdsChangesOf(
    executor: ShellCommandExecutor,
    workingDirectory: String,
    branch: String,
    target: String,
): Boolean {
    if (unmatchedCommits(executor, workingDirectory, branch, target) == 0) return true
    val forkPoint = objectId(executor.executeWithin(listOf("git", "merge-base", target, branch), CommandTimeouts.GIT_READ, workingDirectory))
        ?: return false
    val squashed = objectId(
        executor.executeWithEnvironment(
            listOf("git", "commit-tree", "$branch^{tree}", "-p", forkPoint, "-m", "squash"),
            SQUASH_COMMIT_ENVIRONMENT,
            workingDirectory,
        ),
    ) ?: return false
    return unmatchedCommits(executor, workingDirectory, squashed, target) == 0
}

private suspend fun unmatchedCommits(executor: ShellCommandExecutor, workingDirectory: String, branch: String, target: String): Int? {
    val result = executor.executeWithin(
        listOf("git", "rev-list", "--cherry-pick", "--right-only", "--count", "$target...$branch"),
        CommandTimeouts.GIT_READ,
        workingDirectory,
    )
    return if (result.isSuccess) result.stdout.trim().toIntOrNull() else null
}

private fun objectId(result: CommandResult): String? =
    result.stdout.trim().takeIf { result.isSuccess && it.isNotEmpty() }

private const val SQUASH_IDENTITY = "shogun"
private const val SQUASH_DATE = "@0 +0000"

internal val SQUASH_COMMIT_ENVIRONMENT = mapOf(
    "GIT_AUTHOR_NAME" to SQUASH_IDENTITY,
    "GIT_AUTHOR_EMAIL" to SQUASH_IDENTITY,
    "GIT_AUTHOR_DATE" to SQUASH_DATE,
    "GIT_COMMITTER_NAME" to SQUASH_IDENTITY,
    "GIT_COMMITTER_EMAIL" to SQUASH_IDENTITY,
    "GIT_COMMITTER_DATE" to SQUASH_DATE,
)
