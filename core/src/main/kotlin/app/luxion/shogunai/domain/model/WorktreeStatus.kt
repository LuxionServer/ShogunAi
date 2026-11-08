package app.luxion.shogunai.domain.model

import java.time.Instant

data class MainBranch(val name: String, val ref: String)

data class Divergence(val ahead: Int, val behind: Int)

data class WorktreeStatus(
    val uncommittedChanges: Int,
    val upstream: Divergence?,
    val mainBranch: Divergence?,
    val lastCommitAt: Instant?,
    val createdAt: Instant? = null,
    val neverCommitted: Boolean = false,
    val base: Divergence? = mainBranch,
    val baseName: String? = null,
    val mainBranchHoldsChanges: Boolean = false,
    val baseHoldsChanges: Boolean = mainBranchHoldsChanges,
) {
    val isNotStarted: Boolean
        get() = neverCommitted

    val hasOwnCommits: Boolean
        get() = if (base != null) base.ahead > 0 else !isNotStarted

    val isMergedIntoBase: Boolean
        get() = !neverCommitted && (base.readsAsMerged() || baseHoldsChanges)

    val isMergedIntoMainBranch: Boolean
        get() = !neverCommitted && (mainBranch.readsAsMerged() || mainBranchHoldsChanges)

    val isMerged: Boolean
        get() = isMergedIntoBase || isMergedIntoMainBranch
}

internal fun Divergence?.readsAsMerged(): Boolean =
    this != null && ahead == 0 && behind > 0
