package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopMac
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.ui.components.ActionSlot
import app.luxion.shogunai.ui.components.AgentIndicator
import app.luxion.shogunai.ui.components.ColumnSpec
import app.luxion.shogunai.ui.components.CompactBehavior
import app.luxion.shogunai.ui.components.DataCell
import app.luxion.shogunai.ui.components.DataRow
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.MenuEntry
import app.luxion.shogunai.ui.components.ProgressSize
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.StatusChip
import app.luxion.shogunai.ui.components.WithTooltip
import app.luxion.shogunai.ui.components.interactive
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.theme.ShogunTheme
import app.luxion.shogunai.ui.theme.Status
import androidx.compose.foundation.border

@Composable
fun WorktreeRow(
    model: WorktreeRowModel,
    selected: Boolean,
    isCopied: Boolean,
    compact: Boolean = false,
    showExecution: Boolean = true,
    aliasWidth: Dp = DefaultAliasColumnWidth,
    onSelect: () -> Unit,
    onCopyLaunchCommand: () -> Unit,
    contextMenu: () -> List<MenuEntry> = { emptyList() },
    onOpenGroupTag: () -> Unit = {},
    onFocusAgent: () -> Unit = {},
) {
    val strings = LocalStrings.current
    WorktreeContextMenu(entries = contextMenu) {
        DataRow(
            cells = listOfNotNull(
                model.alias?.let { alias -> DataCell(ColumnSpec(width = aliasWidth)) { AliasCellView(alias) } },
                DataCell(BadgeColumn) { StateBadgeCell(model.badge) },
                if (showExecution) DataCell(ExecutionColumn, Modifier.testTag(ExecutionTag)) { compact -> ExecutionCellView(model.execution, compact, onFocusAgent) } else null,
                DataCell(NameColumn) { NameCellView(model.name, onOpenGroupTag) },
                DataCell(GitColumn, Modifier.testTag(GitTag)) { GitCellView(model.git) },
                DataCell(TimeColumn, Modifier.testTag(TimeTag)) { compact -> TimeCellView(model.time, compact) },
            ),
            compact = compact,
            selected = selected,
            onSelect = onSelect,
            actions = ActionSlot(width = RowActionSlotSize, pinned = isCopied) {
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
            },
        )
    }
}

private val BadgeSize = 20.dp
private val BadgeIconSize = 12.dp
private val BadgeOutlineWidth = 1.dp
private val RowActionSlotSize = 28.dp
private val RowActionIconSize = 16.dp
private val LineHeight = 18.dp
private val CellIconSize = 14.dp

val DefaultAliasColumnWidth = 72.dp
private val MinAliasColumnWidth = 48.dp
private val MaxAliasColumnWidth = 128.dp

fun fitAliasColumnWidth(longest: Dp): Dp = longest.coerceIn(MinAliasColumnWidth, MaxAliasColumnWidth)

@Composable
fun aliasColumnWidth(aliases: List<String>): Dp {
    val measurer = rememberTextMeasurer()
    val style = ShogunTheme.typography.data
    val density = LocalDensity.current
    val distinct = aliases.distinct()
    if (distinct.isEmpty()) return DefaultAliasColumnWidth
    val longest = remember(distinct, style, density) {
        distinct.maxOf { measurer.measure(it, style, maxLines = 1).size.width }
    }
    return fitAliasColumnWidth(with(density) { longest.toDp() } + AliasColumnSlack)
}

private val AliasColumnSlack = 2.dp
private val BadgeColumn = ColumnSpec(width = BadgeSize)
private val ExecutionColumn = ColumnSpec(width = 176.dp, compact = CompactBehavior.Shrink(44.dp))
private val NameColumn = ColumnSpec()
private val GitColumn = ColumnSpec(width = 76.dp, compact = CompactBehavior.Shrink(68.dp))
private val TimeColumn = ColumnSpec(width = 172.dp, compact = CompactBehavior.Shrink(96.dp))

internal const val BranchTag = "row-branch"
internal const val GitTag = "row-git"
internal const val TimeTag = "row-time"
internal const val ExecutionTag = "row-execution"
internal const val AliasTag = "row-alias"
internal const val GroupTagTag = "row-group-tag"
internal const val SecondLineTag = "row-second-line"
internal const val LockedTag = "row-locked"

@Composable
private fun AliasCellView(alias: String) {
    WithTooltip(label = alias) {
        Text(
            alias,
            style = ShogunTheme.typography.data,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(AliasTag),
        )
    }
}

@Composable
internal fun StateBadgeKind.color(): Color = ShogunTheme.status.of(status).color

@Composable
private fun StateBadgeKind.onColor(): Color = if (outlined) color() else ShogunTheme.status.of(status).onColor

internal fun StateBadgeKind.icon(): ImageVector = when (this) {
    StateBadgeKind.NEEDS_YOU -> Icons.Default.PriorityHigh
    StateBadgeKind.AGENT_WORKING -> Icons.Default.PlayArrow
    StateBadgeKind.NEW -> Icons.Default.Add
    StateBadgeKind.STOPPED -> Icons.Default.Pause
    StateBadgeKind.MERGED -> Icons.Default.Check
    StateBadgeKind.INACTIVE -> Icons.Default.Bedtime
    StateBadgeKind.FOLDER_MISSING -> Icons.Default.FolderOff
    StateBadgeKind.OTHER -> Icons.Default.Remove
}

@Composable
private fun StateBadgeCell(badge: StateBadge) {
    WithTooltip(label = badge.label) {
        Box(
            modifier = Modifier.size(BadgeSize).then(
                if (badge.kind.outlined) {
                    Modifier.border(BadgeOutlineWidth, badge.kind.color(), CircleShape)
                } else {
                    Modifier.background(badge.kind.color(), CircleShape)
                },
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = badge.kind.icon(),
                contentDescription = badge.label,
                tint = badge.kind.onColor(),
                modifier = Modifier.size(BadgeIconSize),
            )
        }
    }
}

@Composable
private fun CellLine(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.height(LineHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) { content() }
}

@Composable
private fun ExecutionCellView(cell: ExecutionCell, compact: Boolean, onFocusAgent: () -> Unit) {
    Column {
        CellLine {
            cell.agent?.let { agent -> AgentIndicator(agent, compact = compact, onClick = onFocusAgent.takeIf { agent.focusable }) }
        }
        CellLine {
            cell.device?.let { DeviceLineView(it, compact) }
        }
    }
}

@Composable
private fun DeviceLineView(device: DeviceLine, compact: Boolean) {
    val color = ShogunTheme.status.of(device.status).color
    WithTooltip(label = device.tooltip) {
        CellLine {
            Icon(
                imageVector = device.kind.icon,
                contentDescription = device.label,
                tint = color,
                modifier = Modifier.size(CellIconSize),
            )
            if (!compact) {
                Text(
                    device.label,
                    style = ShogunTheme.typography.dataSmall,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (device.moreCount > 0) {
                    Text("+${device.moreCount}", style = ShogunTheme.typography.dataSmall, maxLines = 1)
                }
            }
        }
    }
}

private val SecondLineKind.icon: ImageVector
    get() = when (this) {
        SecondLineKind.DESKTOP_HOLDER, SecondLineKind.DESKTOP_WAITING -> Icons.Default.DesktopMac
        SecondLineKind.EMULATOR -> Icons.Default.PhoneAndroid
        SecondLineKind.SIMULATOR -> Icons.Default.PhoneIphone
        SecondLineKind.HEADLESS_APP -> Icons.Default.DesktopWindows
    }

@Composable
private fun NameCellView(name: NameCell, onOpenGroupTag: () -> Unit) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        CellLine {
            WithTooltip(label = name.branchTooltip, modifier = Modifier.weight(1f, fill = false)) {
                BranchText(name.branchParts, Modifier.testTag(BranchTag))
            }
            if (name.isMain) StatusChip(Status.NEUTRAL, LocalStrings.current.mainWorktree)
        }
        CellLine(Modifier.testTag(SecondLineTag)) {
            name.groupTag?.let { tag ->
                Text(
                    tag,
                    style = ShogunTheme.typography.dataSmall,
                    color = secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .testTag(GroupTagTag)
                        .interactive(shape = MaterialTheme.shapes.extraSmall, onClick = onOpenGroupTag)
                        .padding(horizontal = Spacing.xxs),
                )
            }
            name.folder?.let { folder ->
                Text(
                    separated(folder, after = name.groupTag),
                    style = ShogunTheme.typography.dataSmall,
                    color = secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            name.project?.let { project ->
                Text(
                    separated(project, after = name.folder ?: name.groupTag),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BranchText(parts: BranchParts, modifier: Modifier = Modifier) {
    val style = ShogunTheme.typography.data
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        parts.prefix?.let { prefix ->
            Text(prefix, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
        Text(parts.rest, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun separated(text: String, after: String?): String = if (after != null) "$SecondLineSeparator$text" else text

private const val SecondLineSeparator = "· "

@Composable
private fun GitCellView(git: GitCell) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        when (git.state) {
            GitCellState.LOADING -> CellLine { InlineProgress(size = ProgressSize.Small) }
            GitCellState.UNAVAILABLE -> {
                CellLine {
                    val dash: @Composable () -> Unit = {
                        Text("—", style = ShogunTheme.typography.dataSmall, color = secondary)
                    }
                    git.unavailableTooltip?.let { WithTooltip(label = it, content = dash) } ?: dash()
                }
                CellLine {}
            }
            GitCellState.READY -> {
                CellLine { git.changes?.let { TooltipText(it, git.changesTooltip, Status.WARNING) } }
                CellLine { git.secondLine?.let { TooltipText(it, git.secondLineTooltip, null) } }
            }
        }
    }
}

@Composable
private fun TooltipText(text: String, tooltip: String?, status: Status?) {
    val color = status?.let { ShogunTheme.status.of(it).color } ?: MaterialTheme.colorScheme.onSurfaceVariant
    val label: @Composable () -> Unit = {
        Text(text, style = ShogunTheme.typography.dataSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (tooltip != null) WithTooltip(label = tooltip, content = label) else label()
}

@Composable
private fun TimeCellView(time: TimeCell, compact: Boolean) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        CellLine {
            val activity = time.activity
            val shown = if (compact) time.compactActivity else activity
            val text: @Composable () -> Unit = {
                Text(
                    shown.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (compact && activity != null) WithTooltip(label = activity, content = text) else text()
        }
        CellLine {
            if (time.isLocked) {
                val locked = LocalStrings.current.locked
                WithTooltip(label = locked) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = locked,
                        tint = secondary,
                        modifier = Modifier.size(CellIconSize).testTag(LockedTag),
                    )
                }
            }
        }
    }
}
