package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.ui.i18n.Strings

fun viewSummaryLine(
    worktreeCount: Int,
    totalSize: Long,
    cleanupCandidateCount: Int,
    isLoading: Boolean,
    strings: Strings,
): String? {
    if (isLoading) return null
    return listOfNotNull(
        strings.worktreesSummary(worktreeCount, formatSize(totalSize)),
        cleanupCandidateCount.takeIf { it > 0 }?.let(strings::cleanupCandidatesSummary),
    ).joinToString(" · ")
}
