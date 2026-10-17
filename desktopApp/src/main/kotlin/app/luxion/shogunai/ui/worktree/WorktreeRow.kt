package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.WorktreeOrigin
import app.luxion.shogunai.ui.components.IconAction
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.InteractiveShape
import app.luxion.shogunai.ui.components.ProgressSize
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.StatusChip
import app.luxion.shogunai.ui.components.interactive
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.theme.ShogunTheme
import app.luxion.shogunai.ui.theme.Status
import java.time.Instant

private val IndicatorSize = 8.dp
private val RowActionSize = 32.dp
private val LastCommitWidth = 104.dp

@Composable
fun WorktreeRow(
    model: WorktreeRowModel,
    selected: Boolean,
    isCopied: Boolean,
    onSelect: () -> Unit,
    onCopyLaunchCommand: () -> Unit,
    onCopyPath: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val selectedColor = MaterialTheme.colorScheme.secondaryContainer
    ContextMenuArea(
        items = {
            buildList {
                add(ContextMenuItem(strings.copyLaunchCommand, onCopyLaunchCommand))
                add(ContextMenuItem(strings.copyPath, onCopyPath))
                if (!model.isMain) add(ContextMenuItem(strings.deleteWorktree, onRemove))
            }
        },
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .hoverable(hoverSource)
                .clip(InteractiveShape)
                .background(if (selected) selectedColor else Color.Transparent)
                .interactive(onClick = onSelect)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowIndicatorDot(model.indicator)
            Text(
                model.branch ?: strings.detachedHead,
                style = ShogunTheme.typography.data,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                secondaryText(model, strings),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            RowChips(model, strings)
            Text(
                model.lastCommitAt?.let { relativeTime(it, Instant.now(), strings) }.orEmpty(),
                style = ShogunTheme.typography.dataSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.width(LastCommitWidth),
            )
            Box(modifier = Modifier.size(RowActionSize), contentAlignment = Alignment.Center) {
                if (hovered || isCopied) {
                    IconAction(
                        icon = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        label = if (isCopied) strings.copied else strings.copyLaunchCommand,
                        onClick = onCopyLaunchCommand,
                        modifier = Modifier.size(RowActionSize),
                    )
                }
            }
        }
    }
}

@Composable
private fun RowChips(model: WorktreeRowModel, strings: Strings) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        if (model.isMain) StatusChip(Status.NEUTRAL, strings.mainWorktree)
        if (model.isFolderMissing) StatusChip(WorktreeIndicator.FOLDER_MISSING.status(), strings.folderMissing)
        model.chips.forEach { chip -> StatusChip(chip.kind.status(), chip.label(strings)) }
        when (model.statusState) {
            WorktreeStatusState.Loading -> InlineProgress(size = ProgressSize.Small)
            WorktreeStatusState.Unavailable -> if (!model.isFolderMissing) {
                Text(strings.statusUnavailable, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> Unit
        }
    }
}

@Composable
private fun RowIndicatorDot(indicator: RowIndicator) {
    val color = when (indicator) {
        RowIndicator.MISSING -> ShogunTheme.status.danger.color
        RowIndicator.AGENT_RUNNING -> ShogunTheme.status.info.color
        RowIndicator.UNCOMMITTED_CHANGES -> ShogunTheme.status.warning.color
        RowIndicator.MERGED -> ShogunTheme.status.success.color
        RowIndicator.INACTIVE -> MaterialTheme.colorScheme.outline
        RowIndicator.CLEAN -> Color.Transparent
    }
    Box(modifier = Modifier.size(IndicatorSize).clip(CircleShape).background(color))
}

private fun secondaryText(model: WorktreeRowModel, strings: Strings): String =
    listOfNotNull(model.folderName, model.origin?.label(strings)).joinToString(" · ")

fun WorktreeOrigin.label(strings: Strings): String = when (this) {
    WorktreeOrigin.SHOGUNAI -> strings.originShogunAi
    WorktreeOrigin.CLAUDE_CODE -> strings.originClaudeCode
    WorktreeOrigin.HERDR -> strings.originHerdr
    WorktreeOrigin.OTHER -> strings.originOther
}

private fun RowChipKind.status(): Status = when (this) {
    RowChipKind.AGENT -> Status.INFO
    RowChipKind.CHANGES -> Status.WARNING
    RowChipKind.DIVERGENCE -> Status.NEUTRAL
    RowChipKind.MERGED -> Status.SUCCESS
    RowChipKind.INACTIVE -> Status.WARNING
    RowChipKind.LOCKED -> Status.NEUTRAL
    RowChipKind.DEVICE -> Status.INFO
}

private fun RowChip.label(strings: Strings): String = when (kind) {
    RowChipKind.AGENT, RowChipKind.CHANGES, RowChipKind.DIVERGENCE -> text.orEmpty()
    RowChipKind.MERGED -> strings.mergedChip
    RowChipKind.INACTIVE -> strings.inactive
    RowChipKind.LOCKED -> strings.locked
    RowChipKind.DEVICE -> strings.deviceChip
}
