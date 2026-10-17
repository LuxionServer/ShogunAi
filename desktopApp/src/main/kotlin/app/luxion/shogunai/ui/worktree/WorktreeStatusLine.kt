package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.luxion.shogunai.domain.model.Divergence
import app.luxion.shogunai.domain.model.WorktreeStatus
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.ProgressSize
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.StatusChip
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.theme.ShogunTheme
import java.time.Instant

@Composable
fun WorktreeStatusLine(state: WorktreeStatusState?, mainBranchName: String?) {
    val strings = LocalStrings.current
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    when (state) {
        null -> Unit
        WorktreeStatusState.Loading -> InlineProgress(modifier = Modifier.padding(top = Spacing.xs), size = ProgressSize.Small)
        WorktreeStatusState.Unavailable -> Text(strings.statusUnavailable, style = MaterialTheme.typography.bodySmall, color = mutedColor)
        is WorktreeStatusState.Loaded -> Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val status = state.status
            if (status.isMerged && mainBranchName != null) {
                StatusChip(WorktreeIndicator.MERGED.status(), strings.mergedInto(mainBranchName))
            }
            if (status.uncommittedChanges > 0) {
                StatusChip(WorktreeIndicator.UNCOMMITTED_CHANGES.status(), strings.uncommittedChanges(status.uncommittedChanges))
            }
            Text(
                statusSegments(status, mainBranchName, Instant.now(), strings).joinToString(SEGMENT_SEPARATOR),
                style = ShogunTheme.typography.dataSmall,
                color = mutedColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

fun statusSegments(status: WorktreeStatus, mainBranchName: String?, now: Instant, strings: Strings): List<String> =
    listOfNotNull(
        status.upstream?.arrows()?.let { "$it ${strings.upstream}" },
        status.mainBranch?.takeUnless { status.isMerged }?.arrows()?.let { arrows ->
            listOfNotNull(arrows, mainBranchName).joinToString(" ")
        },
        status.lastCommitAt?.let { relativeTime(it, now, strings) },
    )

private fun Divergence.arrows(): String? =
    listOfNotNull("↑$ahead".takeIf { ahead > 0 }, "↓$behind".takeIf { behind > 0 })
        .joinToString(" ")
        .ifEmpty { null }

private const val SEGMENT_SEPARATOR = " · "
