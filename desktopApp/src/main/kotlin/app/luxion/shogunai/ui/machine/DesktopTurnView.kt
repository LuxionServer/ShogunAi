package app.luxion.shogunai.ui.machine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.toLocalClockTime
import app.luxion.shogunai.ui.components.ColumnSpec
import app.luxion.shogunai.ui.components.GroupTitle
import app.luxion.shogunai.ui.components.ShogunDialog
import app.luxion.shogunai.ui.components.DangerButton
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.ProgressSize
import app.luxion.shogunai.ui.components.SecondaryButton
import app.luxion.shogunai.ui.i18n.LocalStrings
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun DesktopTurnView(machine: MachineResources, onOpenWorktree: (String, String) -> Unit) {
    val strings = LocalStrings.current
    val watch = machine.desktopTurn
    val scope = rememberCoroutineScope()
    MachineViewFrame(strings.desktopTurnTitle) {
        MachineSecondaryText(strings.desktopTurnExplanation)
        MachineErrorNotice(watch.error, watch::dismissError)
        val holder = watch.state.holder
        if (holder == null) {
            MachineSecondaryText(strings.desktopTurnFree)
        } else {
            HorizontalDivider()
            MachineRow(
                machineCell(MachineMainColumn) { OwnerCell(machine.ownerOfPath(holder.worktreePath), machine::projectName, onOpenWorktree) },
                machineCell(MachineOwnerColumn) {
                    MachineDataText(strings.desktopTurnHeld(clockTime(holder.acquiredAt), clockTime(holder.expiresAt)))
                },
                machineActionCell {
                    if (watch.isReleasing) {
                        InlineProgress(size = ProgressSize.Small)
                    } else {
                        SecondaryButton(onClick = watch::requestRelease) { Text(strings.releaseDesktopTurn) }
                    }
                },
            )
        }
        val waiting = watch.state.waiting
        if (waiting.isNotEmpty()) {
            GroupTitle(strings.desktopTurnWaitingTitle)
            waiting.forEachIndexed { index, ticket ->
                HorizontalDivider()
                MachineRow(
                    machineCell(ColumnSpec(width = PositionColumnWidth)) { MachineDataText("${index + 1}.") },
                    machineCell(MachineMainColumn) { OwnerCell(machine.ownerOfPath(ticket.worktreePath), machine::projectName, onOpenWorktree) },
                    machineCell(MachineOwnerColumn) { MachineStateText(strings.desktopTurnWaitingSince(clockTime(ticket.since))) },
                )
            }
        }
    }
    watch.pendingRelease?.let { pending ->
        ReleaseDesktopTurnDialog(
            holder = pending.displayName,
            onConfirm = { scope.launch { watch.confirmRelease() } },
            onDismiss = watch::cancelRelease,
        )
    }
}

@Composable
fun ReleaseDesktopTurnDialog(holder: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    ShogunDialog(
        title = strings.confirmReleaseDesktopTurnTitle,
        onDismissRequest = onDismiss,
        confirm = { DangerButton(onClick = onConfirm) { Text(strings.releaseDesktopTurn) } },
        dismiss = { NeutralTextButton(onClick = onDismiss) { Text(strings.cancel) } },
    ) {
        Text(strings.confirmReleaseDesktopTurnMessage(holder))
    }
}

fun clockTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toLocalClockTime()

private val PositionColumnWidth = 24.dp
