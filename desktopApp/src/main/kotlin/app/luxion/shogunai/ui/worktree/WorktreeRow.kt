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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.ui.components.WithTooltip
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.InteractiveShape
import app.luxion.shogunai.ui.components.ProgressSize
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.StatusChip
import app.luxion.shogunai.ui.components.interactive
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.theme.ShogunTheme

@Composable
fun WorktreeRow(
    model: WorktreeRowModel,
    selected: Boolean,
    isCopied: Boolean,
    isMain: Boolean,
    projectName: String? = null,
    onSelect: () -> Unit,
    onCopyLaunchCommand: () -> Unit,
    onCopyPath: () -> Unit,
    onOpenInHerdr: () -> Unit,
    onRemove: () -> Unit,
) {
    val strings = LocalStrings.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val prunable = model.indicator == RowIndicator.FOLDER_MISSING
    ContextMenuArea(
        items = {
            buildList {
                add(ContextMenuItem(strings.copyLaunchCommand, onClick = onCopyLaunchCommand))
                if (!prunable) {
                    add(ContextMenuItem(strings.openInHerdr, onClick = onOpenInHerdr))
                }
                add(ContextMenuItem(strings.copyPath, onClick = onCopyPath))
                if (!isMain) {
                    add(ContextMenuItem(strings.deleteWorktree, onClick = onRemove))
                }
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .hoverable(interactionSource)
                .interactive(onClick = onSelect)
                .background(
                    color = if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                    shape = InteractiveShape,
                )
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(IndicatorDotSize)
                        .background(
                            color = model.indicator?.let { ShogunTheme.status.of(it.status).color } ?: Color.Transparent,
                            shape = CircleShape,
                        ),
                )
                Text(
                    rowLabel(model, projectName),
                    style = ShogunTheme.typography.data,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                model.chips.forEach { chip ->
                    StatusChip(chip.status, chip.label)
                }
                if (model.isStatusLoading) {
                    InlineProgress(size = ProgressSize.Small)
                }
            }
            Text(
                model.lastCommitLabel.orEmpty(),
                style = ShogunTheme.typography.dataSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = Spacing.sm),
            )
            Box(modifier = Modifier.size(RowActionSlotSize), contentAlignment = Alignment.Center) {
                if (hovered || isCopied) {
                    WithTooltip(label = strings.copyLaunchCommand) {
                        Icon(
                            imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = strings.copyLaunchCommand,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .interactive(shape = CircleShape, onClick = onCopyLaunchCommand)
                                .padding(Spacing.xs)
                                .size(RowActionIconSize),
                        )
                    }
                }
            }
        }
    }
}

private val IndicatorDotSize = 8.dp
private val RowActionSlotSize = 28.dp
private val RowActionIconSize = 16.dp

@Composable
private fun rowLabel(model: WorktreeRowModel, projectName: String?): AnnotatedString {
    val secondary = ShogunTheme.typography.dataSmall.toSpanStyle()
        .copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    return buildAnnotatedString {
        append(model.branchLabel)
        withStyle(secondary) {
            append("  ")
            append(listOfNotNull(model.folderName, model.originLabel, projectName).joinToString(" · "))
        }
    }
}
