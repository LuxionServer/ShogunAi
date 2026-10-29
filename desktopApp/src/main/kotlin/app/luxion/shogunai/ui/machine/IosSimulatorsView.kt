package app.luxion.shogunai.ui.machine

import app.luxion.shogunai.ui.components.GroupTitle
import app.luxion.shogunai.ui.theme.Status
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.usecase.ProjectOwner
import app.luxion.shogunai.ui.components.DangerTextButton
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.Notice
import app.luxion.shogunai.ui.components.NoticeKind
import app.luxion.shogunai.ui.components.ProgressSize
import app.luxion.shogunai.ui.components.SecondaryButton
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.StatusChip
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.worktree.simulatorStateLabel
import app.luxion.shogunai.ui.worktree.status

@Composable
fun IosSimulatorsView(machine: MachineResources, onOpenWorktree: (String, String) -> Unit) {
    val strings = LocalStrings.current
    var confirmingDelete by remember { mutableStateOf(false) }
    MachineViewFrame(strings.iosSimulatorsTitle) {
        MachineErrorNotice(machine.error, machine::dismissError, machine.errorRetry?.let { { machine.retryError() } })
        PoolUsageSection(machine, MachineResource.IOS_SIMULATORS)
        when (val state = machine.worktreeSimulators) {
            MachineLoad.Loading -> InlineProgress()
            MachineLoad.Unavailable -> MachineSecondaryText(strings.gradleDaemonsUnavailable)
            is MachineLoad.Loaded -> {
                if (state.value.isEmpty() && machine.simulatorOrphans.isEmpty()) MachineSecondaryText(strings.noMachineSimulators)
                if (state.value.isNotEmpty()) MachineColumnHeader()
                state.value.forEach { entry ->
                    HorizontalDivider()
                    WorktreeSimulatorRow(entry, machine, onOpenWorktree)
                }
            }
        }
        val orphans = machine.simulatorOrphans
        if (orphans.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GroupTitle(strings.simulatorOrphansTitle(orphans.size))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    if (machine.isDeletingSimulators) InlineProgress(size = ProgressSize.Small)
                    DangerTextButton(onClick = { confirmingDelete = true }, enabled = !machine.isDeletingSimulators) {
                        Text(strings.deleteOrphans)
                    }
                }
            }
            orphans.forEach { orphan ->
                HorizontalDivider()
                MachineRowLine {
                    MachineDataText(orphan.name, Modifier.weight(1f))
                    StatusChip(orphan.state.status(), simulatorStateLabel(orphan.state, strings))
                }
            }
        }
        machine.simulatorsDeleted?.let { count ->
            Notice(kind = NoticeKind.SUCCESS, message = strings.simulatorsDeleted(count), onDismiss = machine::dismissSimulatorsDeleted)
        }
    }
    if (confirmingDelete) {
        ConfirmDeleteOrphansDialog(
            message = strings.confirmDeleteSimulatorOrphans(machine.simulatorOrphans.size),
            onConfirm = {
                confirmingDelete = false
                machine.deleteSimulatorOrphans()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
private fun WorktreeSimulatorRow(entry: MachineSimulator, machine: MachineResources, onOpenWorktree: (String, String) -> Unit) {
    val strings = LocalStrings.current
    val isBusy = machine.isBusy(entry.projectId, entry.worktreePath)
    val current = entry.simulator?.state ?: SimulatorState.SHUTDOWN
    val state = if (isBusy && current != SimulatorState.BOOTED) SimulatorState.BOOTING else current
    MachineRowLine {
        MachineDataText(simulatorDeviceLabel(entry, machine.project(entry.projectId), strings), Modifier.weight(1f))
        OwnerCell(ProjectOwner.Owned(entry.projectId, entry.worktreePath), machine::projectName, onOpenWorktree, Modifier.width(MachineOwnerColumnWidth))
        Box(Modifier.width(MachineStateColumnWidth)) {
            if (entry.simulator == null && !isBusy) {
                StatusChip(Status.NEUTRAL, strings.notAcquired)
            } else {
                StatusChip(state.status(), simulatorStateLabel(state, strings))
            }
        }
        Row(Modifier.width(MachineActionColumnWidth), horizontalArrangement = Arrangement.End) {
            when {
                isBusy -> InlineProgress(size = ProgressSize.Small)
                state == SimulatorState.SHUTDOWN ->
                    SecondaryButton(onClick = { machine.startSimulator(entry.projectId, entry.worktreePath) }) { Text(strings.startSimulator) }
                else ->
                    SecondaryButton(onClick = { machine.stopSimulator(entry.projectId, entry.worktreePath) }) { Text(strings.stopSimulator) }
            }
        }
    }
}
