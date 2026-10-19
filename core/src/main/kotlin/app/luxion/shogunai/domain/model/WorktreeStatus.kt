package app.luxion.shogunai.domain.model

import java.time.Instant

data class MainBranch(val name: String, val ref: String)

data class Divergence(val ahead: Int, val behind: Int)

data class WorktreeStatus(
    val uncommittedChanges: Int,
    val upstream: Divergence?,
    val mainBranch: Divergence?,
    val lastCommitAt: Instant?,
    val neverCommitted: Boolean = false,
) {
    val isNotStarted: Boolean
        get() = neverCommitted

    val isMerged: Boolean
        get() = !neverCommitted && mainBranch != null && mainBranch.ahead == 0 && mainBranch.behind > 0
}
