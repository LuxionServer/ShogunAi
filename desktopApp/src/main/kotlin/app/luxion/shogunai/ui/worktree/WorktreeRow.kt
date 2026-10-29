package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopMac
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.MenuEntry
import app.luxion.shogunai.ui.components.InteractiveShape
import app.luxion.shogunai.ui.components.ProgressSize
import app.luxion.shogunai.ui.components.Spacing
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
    onSelect: () -> Unit,
    onCopyLaunchCommand: () -> Unit,
    contextMenu: () -> List<MenuEntry> = { emptyList() },
    onOpenGroupTag: () -> Unit = {},
) {
    val strings = LocalStrings.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    WorktreeContextMenu(entries = contextMenu) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .hoverable(interactionSource)
                .interactive(onClick = onSelect)
                .background(
                    color = if (selected) ShogunTheme.colors.accentContainer else Color.Transparent,
                    shape = InteractiveShape,
                )
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            model.alias?.let { AliasCellView(it, compact) }
            StateBadgeCell(model.badge)
            ExecutionCellView(model.execution, compact)
            NameCellView(model.name, onOpenGroupTag, modifier = Modifier.weight(1f))
            GitCellView(model.git, compact)
            TimeCellView(model.time, compact)
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

private val BadgeSize = 20.dp
private val BadgeIconSize = 12.dp
private val BadgeOutlineWidth = 1.dp
private val ExecutionWidth = 176.dp
private val CompactExecutionWidth = 44.dp
private val GitWidth = 76.dp
private val CompactGitWidth = 68.dp
private val TimeWidth = 172.dp
private val CompactTimeWidth = 96.dp
private val RowActionSlotSize = 28.dp
private val RowActionIconSize = 16.dp
private val LineHeight = 18.dp
private val CellIconSize = 14.dp

internal const val BranchTag = "row-branch"
internal const val GitTag = "row-git"
internal const val TimeTag = "row-time"
internal const val ExecutionTag = "row-execution"
internal const val AliasTag = "row-alias"
internal const val GroupTagTag = "row-group-tag"
private val AliasWidth = 72.dp
private val CompactAliasWidth = 48.dp

@Composable
private fun AliasCellView(alias: String, compact: Boolean) {
    WithTooltip(label = alias) {
        Text(
            alias,
            style = ShogunTheme.typography.data,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(if (compact) CompactAliasWidth else AliasWidth).testTag(AliasTag),
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
    StateBadgeKind.NEW -> Icons.Default.FiberNew
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
private fun ExecutionCellView(cell: ExecutionCell, compact: Boolean) {
    Column(modifier = Modifier.width(if (compact) CompactExecutionWidth else ExecutionWidth).testTag(ExecutionTag)) {
        CellLine {
            cell.agent?.let { AgentLineView(it, compact) }
        }
        CellLine {
            cell.device?.let { DeviceLineView(it, compact) }
        }
    }
}

@Composable
private fun AgentLineView(agent: AgentLine, compact: Boolean) {
    val color = ShogunTheme.status.of(agent.status).color
    WithTooltip(label = agent.tooltip) {
        CellLine {
            Icon(Icons.Default.SmartToy, contentDescription = agent.label, tint = color, modifier = Modifier.size(CellIconSize))
            if (!compact) {
                Text(
                    agent.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (agent.moreCount > 0) {
                    Text("+${agent.moreCount}", style = ShogunTheme.typography.dataSmall, maxLines = 1)
                }
            }
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
private fun NameCellView(name: NameCell, onOpenGroupTag: () -> Unit, modifier: Modifier) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = modifier) {
        WithTooltip(label = name.branch) {
            CellLine {
                Text(
                    name.branch,
                    style = ShogunTheme.typography.data,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(BranchTag),
                )
            }
        }
        CellLine {
            name.groupTag?.let { tag ->
                Text(
                    tag,
                    style = ShogunTheme.typography.dataSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .testTag(GroupTagTag)
                        .interactive(shape = MaterialTheme.shapes.extraSmall, onClick = onOpenGroupTag)
                        .padding(horizontal = Spacing.xxs),
                )
            }
            val context = listOfNotNull(name.folder, name.origin).joinToString(" · ")
            if (context.isNotEmpty()) {
                Text(
                    context,
                    style = ShogunTheme.typography.dataSmall,
                    color = secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            name.project?.let { project ->
                Text(
                    if (context.isNotEmpty()) "· $project" else project,
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
private fun GitCellView(git: GitCell, compact: Boolean) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = Modifier.width(if (compact) CompactGitWidth else GitWidth).testTag(GitTag)) {
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
    Column(modifier = Modifier.width(if (compact) CompactTimeWidth else TimeWidth).testTag(TimeTag)) {
        CellLine {
            Text(
                time.activity.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CellLine {
            Text(
                time.marks.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
