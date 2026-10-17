package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import app.luxion.shogunai.domain.model.EMULATOR_PORT_KEY
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeOrigin
import app.luxion.shogunai.domain.model.portKeyLabel
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.StatusChip
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.theme.ShogunTheme

@Composable
fun WorktreeBranchLine(worktree: Worktree, origin: WorktreeOrigin?, agents: List<RunningAgent>) {
    val strings = LocalStrings.current
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(worktree.branch ?: strings.detachedHead, style = ShogunTheme.typography.dataSmall)
        if (worktree.isLocked) {
            StatusChip(WorktreeIndicator.LOCKED.status(), strings.locked)
        }
        origin?.let {
            Text(it.label(strings), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        agents.forEach { agent ->
            StatusChip(WorktreeIndicator.AGENT_RUNNING.status(), agent.name)
        }
    }
}

@Composable
fun WorktreeOverviewLine(size: Long?, isInactive: Boolean, ports: Map<String, Int> = emptyMap()) {
    val portsText = portsLabel(ports)
    if (size == null && !isInactive && portsText.isEmpty()) return
    val strings = LocalStrings.current
    val style = ShogunTheme.typography.dataSmall
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        size?.let { Text(formatSize(it), style = style, color = mutedColor) }
        if (portsText.isNotEmpty()) {
            Text(portsText, style = style, color = mutedColor)
        }
        if (isInactive) {
            StatusChip(WorktreeIndicator.INACTIVE.status(), strings.inactive)
        }
    }
}

fun portsLabel(ports: Map<String, Int>): String =
    ports.entries.filterNot { it.key == EMULATOR_PORT_KEY }.sortedBy { it.value }
        .joinToString(" · ") { (key, port) -> "${portKeyLabel(key)} $port" }

private fun WorktreeOrigin.label(strings: Strings): String = when (this) {
    WorktreeOrigin.SHOGUNAI -> strings.originShogunAi
    WorktreeOrigin.CLAUDE_CODE -> strings.originClaudeCode
    WorktreeOrigin.OTHER -> strings.originOther
}
