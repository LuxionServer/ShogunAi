package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeOrigin
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.theme.ShogunTheme

@Composable
fun WorktreeBranchLine(worktree: Worktree, origin: WorktreeOrigin?, agents: List<RunningAgent>) {
    val strings = LocalStrings.current
    val style = MaterialTheme.typography.bodySmall
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(worktree.branch ?: strings.detachedHead, style = style)
        if (worktree.isLocked) {
            Text(strings.locked, style = style, color = MaterialTheme.colorScheme.tertiary, modifier = labelPadding)
        }
        origin?.let {
            Text(it.label(strings), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = labelPadding)
        }
        agents.forEach { agent ->
            Text("● ${agent.name}", style = style, color = ShogunTheme.extendedColors.success, modifier = labelPadding)
        }
    }
}

@Composable
fun WorktreeOverviewLine(size: Long?, isInactive: Boolean) {
    if (size == null && !isInactive) return
    val strings = LocalStrings.current
    val style = MaterialTheme.typography.bodySmall
    Row(verticalAlignment = Alignment.CenterVertically) {
        size?.let { Text(formatSize(it), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (isInactive) {
            Text(
                strings.inactive,
                style = style,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = if (size != null) labelPadding else Modifier,
            )
        }
    }
}

private val labelPadding = Modifier.padding(start = Spacing.sm)

private fun WorktreeOrigin.label(strings: Strings): String = when (this) {
    WorktreeOrigin.SHOGUNAI -> strings.originShogunAi
    WorktreeOrigin.CLAUDE_CODE -> strings.originClaudeCode
    WorktreeOrigin.OTHER -> strings.originOther
}
