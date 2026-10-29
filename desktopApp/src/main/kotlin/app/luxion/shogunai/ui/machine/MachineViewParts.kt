package app.luxion.shogunai.ui.machine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.usecase.ProjectOwner
import app.luxion.shogunai.ui.components.ColumnSpec
import app.luxion.shogunai.ui.components.DataCell
import app.luxion.shogunai.ui.components.DataHeader
import app.luxion.shogunai.ui.components.DataRow
import app.luxion.shogunai.ui.components.GroupedNotice
import app.luxion.shogunai.ui.components.RowLines
import app.luxion.shogunai.ui.components.ShogunDialog
import app.luxion.shogunai.ui.components.DangerButton
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.LocalErrorFixActions
import app.luxion.shogunai.ui.components.NoticeKind
import app.luxion.shogunai.ui.components.StatusChip
import app.luxion.shogunai.ui.components.ViewTitle
import app.luxion.shogunai.ui.theme.Status
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.interactive
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.components.ErrorNotice
import app.luxion.shogunai.ui.theme.ShogunTheme

val MachineActionColumnWidth: Dp = 96.dp
val MachineOwnerColumnWidth: Dp = 220.dp
val MachineStateColumnWidth: Dp = 110.dp

@Composable
fun MachineViewFrame(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        ViewTitle(title)
        content()
    }
}

@Composable
fun PoolUsageSection(machine: MachineResources, resource: MachineResource) {
    val strings = LocalStrings.current
    val usage = machine.poolOf(resource)
    if (usage != null) {
        val buildsHolding = buildsHoldingPool(usage)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MachineDataText(strings.poolUsage(usage.inUse, usage.effectiveLimit))
                MachineStateText(" · " + strings.profileName(machine.profile))
            }
            when {
                buildsHolding != null -> StatusChip(Status.INFO, strings.overLimitBuildsRunningChip)
                usage.isOverLimit -> StatusChip(Status.WARNING, strings.overLimitChip)
            }
            LocalErrorFixActions.current?.let { actions ->
                NeutralTextButton(onClick = actions.openPreferences) {
                    Text(strings.changeInPreferences)
                }
            }
        }
        buildsHolding?.let { MachineSecondaryText(strings.overLimitNothingIdle(it.busy, it.unknown)) }
    }
    poolNoticeBanner(machine.noticesOf(resource), strings)?.let { banner ->
        GroupedNotice(
            kind = NoticeKind.INFO,
            summary = banner.summary,
            details = banner.details,
            onDismiss = { machine.dismissNotices(resource) },
        )
    }
}

@Composable
fun MachineErrorNotice(error: Throwable?, onDismiss: () -> Unit, onRetry: (() -> Unit)? = null) {
    if (error == null) return
    ErrorNotice(error = error, onDismiss = onDismiss, onRetry = onRetry)
}

@Composable
fun MachineSecondaryText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
fun MachineDataText(text: String, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text,
        style = ShogunTheme.typography.dataSmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun DeviceLabelText(label: DeviceLabel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        label.note?.let { MachineStateText(it) }
        if (label.note != null && label.data != null) MachineStateText(" · ")
        label.data?.let { MachineDataText(it) }
    }
}

@Composable
fun MachineStateText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

val MachineMainColumn = ColumnSpec()
val MachineOwnerColumn = ColumnSpec(width = MachineOwnerColumnWidth)
val MachineStateColumn = ColumnSpec(width = MachineStateColumnWidth)
val MachineActionColumn = ColumnSpec(width = MachineActionColumnWidth)
val MachineSizeColumn = ColumnSpec(width = 80.dp)
val MachineDaemonStateColumn = ColumnSpec(width = 170.dp)

fun machineCell(spec: ColumnSpec, content: @Composable () -> Unit): DataCell = DataCell(spec) { content() }

fun machineActionCell(content: @Composable () -> Unit): DataCell = DataCell(MachineActionColumn) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        content()
    }
}

@Composable
fun MachineRow(vararg cells: DataCell) {
    DataRow(cells = cells.toList(), lines = RowLines.ONE)
}

@Composable
fun MachineColumnHeader() {
    val strings = LocalStrings.current
    DataHeader(
        listOf(
            MachineMainColumn to strings.machineColumnDevice,
            MachineOwnerColumn to strings.machineColumnWorktree,
            MachineStateColumn to strings.machineColumnState,
            MachineActionColumn to "",
        ),
    )
}

@Composable
fun OwnerCell(
    owner: ProjectOwner?,
    projectName: (String) -> String,
    onOpenWorktree: (projectId: String, path: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    if (owner == null) {
        MachineDataText("", modifier)
        return
    }
    val label = owner.label(strings, projectName)
    if (owner is ProjectOwner.Owned) {
        Text(
            label,
            style = ShogunTheme.typography.dataSmall,
            color = ShogunTheme.colors.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.interactive { onOpenWorktree(owner.projectId, owner.worktreePath) },
        )
    } else {
        MachineDataText(label, modifier)
    }
}

@Composable
fun MachineBusy(isBusy: Boolean) {
    if (isBusy) InlineProgress()
}

@Composable
fun ConfirmDeleteOrphansDialog(message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    ShogunDialog(
        title = strings.confirmDeleteOrphansTitle,
        onDismissRequest = onDismiss,
        confirm = { DangerButton(onClick = onConfirm) { Text(strings.deleteOrphans) } },
        dismiss = { NeutralTextButton(onClick = onDismiss) { Text(strings.cancel) } },
    ) {
        Text(message)
    }
}
