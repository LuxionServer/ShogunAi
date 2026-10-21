package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.model.Divergence
import app.luxion.shogunai.domain.model.MainBranch
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.WorktreeStatus
import java.time.Instant

class ReadWorktreeStatusUseCase(
    private val executor: ShellCommandExecutor,
    private val fileManager: FileManager,
) {
    suspend operator fun invoke(worktree: Worktree, mainBranch: MainBranch?): Result<WorktreeStatus> = runCatching {
        val uncommittedChanges = uncommittedChanges(worktree)
        val upstream = if (worktree.branch != null) divergenceFrom(worktree, "@{upstream}") else null
        val mainBranchDivergence = mainBranch
            ?.takeIf { it.name != worktree.branch }
            ?.let { divergenceFrom(worktree, it.ref) }
        val wouldReadAsMerged = mainBranchDivergence != null &&
            mainBranchDivergence.ahead == 0 &&
            mainBranchDivergence.behind > 0
        WorktreeStatus(
            uncommittedChanges = uncommittedChanges,
            upstream = upstream,
            mainBranch = mainBranchDivergence,
            lastCommitAt = lastCommitAt(worktree),
            createdAt = createdAt(worktree),
            neverCommitted = wouldReadAsMerged && upstream == null && worktree.branch != null &&
                branchNeverCommitted(worktree, worktree.branch),
        )
    }

    private suspend fun branchNeverCommitted(worktree: Worktree, branch: String): Boolean {
        val reflog = git(worktree, "reflog", "show", "--format=%H", "refs/heads/$branch")
        if (!reflog.isSuccess) return false
        val entries = reflog.stdout.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (entries.size != 1) return false
        val head = git(worktree, "rev-parse", "HEAD")
        return head.isSuccess && head.stdout.trim() == entries.single()
    }

    private suspend fun uncommittedChanges(worktree: Worktree): Int {
        val result = git(worktree, "--no-optional-locks", "status", "--porcelain")
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

    private fun createdAt(worktree: Worktree): Instant? {
        if (worktree.isMain) return null
        val pointer = fileManager.readText("${worktree.path.trimEnd('/')}/.git") ?: return null
        val administrativeFolder = pointer.lineSequence()
            .firstOrNull { it.startsWith(GITDIR_PREFIX) }
            ?.removePrefix(GITDIR_PREFIX)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        return fileManager.lastModifiedMillis("${administrativeFolder.trimEnd('/')}/commondir")
            ?.let(Instant::ofEpochMilli)
    }

    private suspend fun git(worktree: Worktree, vararg args: String) =
        executor.executeWithin(listOf("git", *args), CommandTimeouts.GIT_READ, workingDirectory = worktree.path)
}

private const val GITDIR_PREFIX = "gitdir:"

internal fun parseLeftRightCount(output: String): Divergence? {
    val counts = output.trim().split(Regex("\\s+")).mapNotNull { it.toIntOrNull() }
    if (counts.size != 2) return null
    val (behind, ahead) = counts
    return Divergence(ahead = ahead, behind = behind)
}
