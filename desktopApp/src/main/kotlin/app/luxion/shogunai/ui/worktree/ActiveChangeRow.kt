package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.ui.actions.ActionEntry
import app.luxion.shogunai.ui.actions.ActionId
import app.luxion.shogunai.ui.actions.GroupCatalog
import app.luxion.shogunai.ui.actions.toMenu
import app.luxion.shogunai.ui.components.ActionSlot
import app.luxion.shogunai.ui.components.AgentIndicator
import app.luxion.shogunai.ui.components.AgentIndicatorModel
import app.luxion.shogunai.ui.components.DataCell
import app.luxion.shogunai.ui.components.DataRow
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.MenuEntry
import app.luxion.shogunai.ui.components.MoreMenuButton
import app.luxion.shogunai.ui.components.ProgressSize
import app.luxion.shogunai.ui.components.WithTooltip
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.theme.ShogunTheme

internal const val ActiveChangeTag = "active-change-row"
internal const val ActiveChangeDetailTag = "active-change-detail"

fun changeRowMenu(
    catalog: GroupCatalog,
    strings: Strings,
    onAction: (ActionId) -> Unit,
    onShowInMultirepo: () -> Unit,
): List<MenuEntry> {
    val actions = listOfNotNull(catalog.primary?.let { ActionEntry.Run(it) }) + catalog.overflowEntries(strings)
    return actions.toMenu(strings) { spec, _ -> onAction(spec.id) } + MenuEntry.Separator + MenuEntry.Item(strings.showInMultirepo, onShowInMultirepo)
}

fun changeDetail(group: ChangeGroup): String = listOf(group.branch, group.aliases.joinToString(", ")).joinToString(" · ")

@Composable
internal fun ActiveChangeRow(
    group: ChangeGroup,
    expanded: Boolean,
    agent: AgentIndicatorModel?,
    menu: () -> List<MenuEntry>,
    isBusy: Boolean,
    compact: Boolean,
    showExecution: Boolean,
    onToggle: () -> Unit,
) {
    val strings = LocalStrings.current
    WorktreeContextMenu(entries = menu) {
        DataRow(
            modifier = Modifier.testTag(ActiveChangeTag),
            cells = listOfNotNull(
                DataCell(BadgeColumn) { StateBadgeCell(StateBadge(group.state, group.state.label(strings))) },
                if (showExecution) DataCell(ExecutionColumn) { compact -> agent?.let { AgentIndicator(it, compact = compact) } } else null,
                DataCell(NameColumn) { ChangeNameCell(group, expanded) },
                DataCell(GitColumn) {},
                DataCell(TimeColumn) { if (isBusy) InlineProgress(size = ProgressSize.Small) },
            ),
            compact = compact,
            onSelect = onToggle,
            actions = ActionSlot(width = RowActionSlotSize, alwaysVisible = true) {
                MoreMenuButton(label = strings.moreActions, entries = menu())
            },
        )
    }
}

@Composable
private fun ChangeNameCell(group: ChangeGroup, expanded: Boolean) {
    val strings = LocalStrings.current
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        CellLine {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                contentDescription = if (expanded) strings.collapse else strings.expand,
                tint = secondary,
                modifier = Modifier.size(ChevronSize),
            )
            Text(
                group.title,
                style = ShogunTheme.typography.data,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CellLine {
            val detail = changeDetail(group)
            WithTooltip(label = detail) {
                Text(
                    detail,
                    style = ShogunTheme.typography.dataSmall,
                    color = secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(ActiveChangeDetailTag),
                )
            }
        }
    }
}

private val ChevronSize = 16.dp
