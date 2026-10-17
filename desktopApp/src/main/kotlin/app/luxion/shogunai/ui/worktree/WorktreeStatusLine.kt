package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.Divergence
import app.luxion.shogunai.domain.model.WorktreeStatus
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import java.time.Instant

@Composable
fun WorktreeStatusLine(state: WorktreeStatusState?, mainBranchName: String?) {
    val strings = LocalStrings.current
    val style = MaterialTheme.typography.bodySmall
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    when (state) {
        null -> Unit
        WorktreeStatusState.Loading -> CircularProgressIndicator(
            modifier = Modifier.padding(top = Spacing.xs).size(12.dp),
            strokeWidth = 1.5.dp,
        )
        WorktreeStatusState.Unavailable -> Text(strings.statusUnavailable, style = style, color = mutedColor)
        is WorktreeStatusState.Loaded -> Row(verticalAlignment = Alignment.CenterVertically) {
            val status = state.status
            if (status.isMerged && mainBranchName != null) {
                Text(
                    strings.mergedInto(mainBranchName),
                    style = style,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = Spacing.sm),
                )
            }
            Text(
                statusSegments(status, mainBranchName, Instant.now(), strings).joinToString(SEGMENT_SEPARATOR),
                style = style,
                color = mutedColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

fun statusSegments(status: WorktreeStatus, mainBranchName: String?, now: Instant, strings: Strings): List<String> =
    listOfNotNull(
        status.uncommittedChanges.takeIf { it > 0 }?.let(strings::uncommittedChanges),
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
