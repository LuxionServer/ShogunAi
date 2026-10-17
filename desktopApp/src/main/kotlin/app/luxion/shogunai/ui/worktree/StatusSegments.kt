package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.model.WorktreeStatus
import app.luxion.shogunai.ui.i18n.Strings
import java.time.Instant

fun statusSegments(status: WorktreeStatus, mainBranchName: String?, now: Instant, strings: Strings): List<String> =
    listOfNotNull(
        status.upstream?.arrows()?.let { "$it ${strings.upstream}" },
        status.mainBranch?.takeUnless { status.isMerged }?.arrows()?.let { arrows ->
            listOfNotNull(arrows, mainBranchName).joinToString(" ")
        },
        status.lastCommitAt?.let { relativeTime(it, now, strings) },
    )
