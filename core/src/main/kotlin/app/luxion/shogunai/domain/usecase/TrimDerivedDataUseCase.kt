package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.DerivedDataSource
import app.luxion.shogunai.domain.model.AttributedDerivedData
import app.luxion.shogunai.domain.model.DerivedDataDeletionFailure
import app.luxion.shogunai.domain.model.DerivedDataFolder
import app.luxion.shogunai.domain.model.DerivedDataListing
import app.luxion.shogunai.domain.model.DerivedDataOwner
import app.luxion.shogunai.domain.model.DerivedDataQuotaMode
import app.luxion.shogunai.domain.model.DerivedDataTrim
import app.luxion.shogunai.domain.model.Worktree
import java.time.Duration
import java.time.Instant

class TrimDerivedDataUseCase(
    private val source: DerivedDataSource,
    private val list: ListDerivedDataUseCase,
    private val now: () -> Instant = Instant::now,
) {
    suspend operator fun invoke(
        quotaBytes: Long?,
        worktrees: List<Worktree>,
        activeWorktreePaths: Set<String>,
        dryRun: Boolean = false,
        measured: DerivedDataListing? = null,
        quotaMode: DerivedDataQuotaMode = DerivedDataQuotaMode.FIXED,
    ): Result<DerivedDataTrim> = runCatching {
        if (quotaBytes == null) return@runCatching DerivedDataTrim(null, 0L, 0L, dryRun = dryRun, quotaMode = quotaMode)
        val active = activeWorktreePaths.map(::normalizePath).toSet()
        val listing = measured
            ?: list(worktrees, includeCompilationCache = false, measure = DerivedDataMeasure.ALL).getOrThrow()
        val usageBefore = listing.folders.sumOf { it.folder.sizeBytes ?: 0L }
        val inUse = listing.folders.filter { entry ->
            val owner = entry.owner
            owner is DerivedDataOwner.Worktree && isInUse(owner, active, entry.folder.lastAccessed)
        }
        val candidates = candidatesOf(listing.folders, inUse)

        var usage = usageBefore
        val released = mutableListOf<AttributedDerivedData>()
        val failures = mutableListOf<DerivedDataDeletionFailure>()
        for (candidate in candidates) {
            if (usage <= quotaBytes) break
            if (!dryRun && !stillReleasable(candidate, worktrees, active)) continue
            try {
                if (!dryRun) source.delete(candidate.folder)
                released += candidate
                usage -= candidate.folder.sizeBytes ?: 0L
            } catch (error: Exception) {
                failures += DerivedDataDeletionFailure(candidate.folder.path, error.message ?: "delete failed")
            }
        }
        DerivedDataTrim(quotaBytes, usageBefore, usage, released, inUse, failures, dryRun, quotaMode)
    }

    private fun candidatesOf(folders: List<AttributedDerivedData>, inUse: List<AttributedDerivedData>): List<AttributedDerivedData> {
        val byAccess = compareBy<AttributedDerivedData, Instant?>(nullsFirst()) { it.folder.lastAccessed }
        val orphans = folders.filter { it.owner == DerivedDataOwner.Orphaned }.sortedWith(byAccess)
        val unused = folders.filter { it.owner is DerivedDataOwner.Worktree && it !in inUse }.sortedWith(byAccess)
        return orphans + unused
    }

    private suspend fun stillReleasable(candidate: AttributedDerivedData, worktrees: List<Worktree>, active: Set<String>): Boolean {
        val current = source.folders().firstOrNull { it.path == candidate.folder.path } ?: return false
        val candidateOwner = candidate.owner
        if (candidateOwner !is DerivedDataOwner.Worktree) return !source.projectExists(current.workspacePath)
        return ownerOf(current, worktrees) == candidateOwner && !isInUse(candidateOwner, active, current.lastAccessed)
    }

    private fun ownerOf(folder: DerivedDataFolder, worktrees: List<Worktree>): DerivedDataOwner? =
        if (!source.projectExists(folder.workspacePath)) null
        else owningWorktree(folder.workspacePath, worktrees)?.let { DerivedDataOwner.Worktree(it.path) }

    private fun isInUse(owner: DerivedDataOwner.Worktree, active: Set<String>, lastAccessed: Instant?): Boolean {
        if (normalizePath(owner.path) in active) return true
        return lastAccessed != null && Duration.between(lastAccessed, now()) < IN_USE_GRACE
    }

    private companion object {
        private val IN_USE_GRACE: Duration = Duration.ofHours(1)
    }
}
