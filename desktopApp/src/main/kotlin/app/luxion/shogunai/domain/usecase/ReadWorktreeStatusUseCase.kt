package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.Divergence
import app.luxion.shogunai.domain.model.MainBranch
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.WorktreeStatus
import java.time.Instant

class ReadWorktreeStatusUseCase(
    private val executor: ShellCommandExecutor,
) {
    suspend operator fun invoke(worktree: Worktree, mainBranch: MainBranch?): Result<WorktreeStatus> = runCatching {
        WorktreeStatus(
            uncommittedChanges = uncommittedChanges(worktree),
            upstream = if (worktree.branch != null) divergenceFrom(worktree, "@{upstream}") else null,
            mainBranch = mainBranch
                ?.takeIf { it.name != worktree.branch }
                ?.let { divergenceFrom(worktree, it.ref) },
            lastCommitAt = lastCommitAt(worktree),
        )
    }

    private suspend fun uncommittedChanges(worktree: Worktree): Int {
        val result = git(worktree, "status", "--porcelain")
        if (!result.isSuccess) {
            throw WorktreeError.GitCommandFailed(result.command, result.exitCode, result.stderr)
        }
        return result.stdout.lineSequence().count { it.isNotBlank() }
    }

    private suspend fun divergenceFrom(worktree: Worktree, ref: String): Divergence? {
        val result = git(worktree, "rev-list", "--left-right", "--count", "$ref...HEAD")
        if (!result.isSuccess) return null
        return parseLeftRightCount(result.stdout)
    }

    private suspend fun lastCommitAt(worktree: Worktree): Instant? {
        val result = git(worktree, "log", "-1", "--format=%ct")
        if (!result.isSuccess) return null
        return result.stdout.trim().toLongOrNull()?.let(Instant::ofEpochSecond)
    }

    private suspend fun git(worktree: Worktree, vararg args: String) =
        executor.execute(listOf("git", *args), workingDirectory = worktree.path)
}

internal fun parseLeftRightCount(output: String): Divergence? {
    val counts = output.trim().split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }
    if (counts.size != 2) return null
    val (behind, ahead) = counts
    return Divergence(ahead = ahead, behind = behind)
}
