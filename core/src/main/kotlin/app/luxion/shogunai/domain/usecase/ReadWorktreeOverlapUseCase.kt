package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.MainBranch
import app.luxion.shogunai.domain.model.OverlapSkipReason
import app.luxion.shogunai.domain.model.OverlapWorktree
import app.luxion.shogunai.domain.model.OverlappingFile
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.SkippedWorktree
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeOverlap
import app.luxion.shogunai.domain.model.runSuspendCatching
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class ReadWorktreeOverlapUseCase(
    private val config: ProjectConfig,
    private val executor: ShellCommandExecutor,
    private val provisioningSource: RepositoryProvisioningSource,
) {
    private val resolveMainBranch = ResolveMainBranchUseCase(config, executor)
    private val resolveRecordedBases = ResolveRecordedBasesUseCase(config, executor)

    suspend operator fun invoke(
        worktrees: List<Worktree>,
        target: Worktree? = null,
        includeIgnored: Boolean = false,
    ): Result<WorktreeOverlap> = runSuspendCatching {
        val ignored = if (includeIgnored) emptyList() else provisioningSource.read(config.baseRepositoryPath).overlapIgnore
        val pathspecs = listOf("--", ".") + ignored.map { "$EXCLUDE_GLOB$it" }
        val mainBranch = resolveMainBranch().getOrNull()
        val recordedBases = resolveRecordedBases()
        val permits = Semaphore(MAX_CONCURRENT_READS)
        val reads = coroutineScope {
            worktrees.filterNot { it.isBare }.map { worktree ->
                async {
                    val base = worktree.branch?.let(recordedBases::get) ?: mainBranch
                    permits.withPermit { changesOf(worktree, base, pathspecs) }
                }
            }.awaitAll()
        }
        val changed = reads.filterIsInstance<WorktreeChanges.Changed>()
        WorktreeOverlap(
            files = overlappingFiles(changed, target?.path),
            skipped = reads.filterIsInstance<WorktreeChanges.Skipped>().map { it.skipped },
        )
    }

    private fun overlappingFiles(changed: List<WorktreeChanges.Changed>, targetPath: String?): List<OverlappingFile> =
        changed.flatMap { change -> change.files.map { it to change.worktree } }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size > 1 }
            .filterValues { worktrees -> targetPath == null || worktrees.any { it.path == targetPath } }
            .map { (path, worktrees) -> OverlappingFile(path, worktrees) }
            .sortedBy { it.path }

    private suspend fun changesOf(worktree: Worktree, base: MainBranch?, pathspecs: List<String>): WorktreeChanges {
        val skip = { reason: OverlapSkipReason, detail: String? ->
            WorktreeChanges.Skipped(SkippedWorktree(worktree.path, worktree.branch, reason, detail))
        }
        if (worktree.isPrunable) return skip(OverlapSkipReason.PRUNABLE, null)
        if (base == null) return skip(OverlapSkipReason.NO_BASE, null)
        val mergeBase = git(worktree, "merge-base", base.ref, "HEAD")
        if (!mergeBase.isSuccess) return skip(OverlapSkipReason.READ_FAILED, failureDetail(mergeBase))
        val tracked = git(worktree, "diff", "--name-only", "--no-renames", "-z", mergeBase.stdout.trim(), *pathspecs.toTypedArray())
        if (!tracked.isSuccess) return skip(OverlapSkipReason.READ_FAILED, failureDetail(tracked))
        val untracked = git(worktree, "ls-files", "--others", "--exclude-standard", "-z", *pathspecs.toTypedArray())
        if (!untracked.isSuccess) return skip(OverlapSkipReason.READ_FAILED, failureDetail(untracked))
        val files = (nulSeparated(tracked.stdout) + nulSeparated(untracked.stdout)).toSortedSet()
        return WorktreeChanges.Changed(OverlapWorktree(worktree.path, worktree.branch, base.name), files)
    }

    private suspend fun git(worktree: Worktree, vararg args: String): CommandResult =
        executor.executeWithin(listOf("git", *args), CommandTimeouts.GIT_READ, workingDirectory = worktree.path)

    private sealed interface WorktreeChanges {
        data class Changed(val worktree: OverlapWorktree, val files: Set<String>) : WorktreeChanges
        data class Skipped(val skipped: SkippedWorktree) : WorktreeChanges
    }

    private companion object {
        const val MAX_CONCURRENT_READS = 8
        const val EXCLUDE_GLOB = ":(exclude,glob)"
    }
}

private fun nulSeparated(output: String): List<String> = output.split('\u0000').filter { it.isNotEmpty() }

private fun failureDetail(result: CommandResult): String =
    result.stderr.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: "exit code ${result.exitCode}"
