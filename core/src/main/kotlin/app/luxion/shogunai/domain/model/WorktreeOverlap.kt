package app.luxion.shogunai.domain.model

data class OverlapWorktree(val path: String, val branch: String?, val base: String)

data class OverlappingFile(val path: String, val worktrees: List<OverlapWorktree>)

enum class OverlapSkipReason { PRUNABLE, NO_BASE, READ_FAILED }

data class SkippedWorktree(
    val path: String,
    val branch: String?,
    val reason: OverlapSkipReason,
    val detail: String? = null,
)

data class WorktreeOverlap(
    val files: List<OverlappingFile>,
    val skipped: List<SkippedWorktree>,
)
