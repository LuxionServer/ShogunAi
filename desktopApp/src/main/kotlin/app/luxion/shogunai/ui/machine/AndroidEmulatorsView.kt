package app.luxion.shogunai.ui.machine

import app.luxion.shogunai.ui.components.GroupTitle
import app.luxion.shogunai.ui.theme.Status
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.usecase.ProjectOwner
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.ProgressSize
import app.luxion.shogunai.ui.components.SecondaryButton
import app.luxion.shogunai.ui.components.StatusChip
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.worktree.emulatorStateLabel
import app.luxion.shogunai.ui.worktree.status

@Composable
fun AndroidEmulatorsView(machine: MachineResources, onOpenWorktree: (String, String) -> Unit) {
    val strings = LocalStrings.current
    MachineViewFrame(strings.androidEmulatorsTitle) {
        MachineErrorNotice(machine.error, machine::dismissError, machine.errorRetry?.let { { machine.retryError() } })
        PoolUsageSection(machine, MachineResource.ANDROID_EMULATORS)
        when (val state = machine.worktreeEmulators) {
            MachineLoad.Loading -> InlineProgress()
            MachineLoad.Unavailable -> MachineSecondaryText(strings.gradleDaemonsUnavailable)
            is MachineLoad.Loaded -> {
                if (state.value.isEmpty()) MachineSecondaryText(strings.noMachineEmulators) else MachineColumnHeader()
                state.value.forEach { entry ->
                    HorizontalDivider()
                    WorktreeEmulatorRow(entry, machine, onOpenWorktree)
                }
            }
        }
        val unowned = machine.unownedEmulators
        if (unowned.isNotEmpty()) {
            GroupTitle(strings.unownedEmulatorsTitle)
            unowned.forEach { emulator ->
                HorizontalDivider()
                MachineRow(
                    machineCell(MachineMainColumn) { MachineDataText(emulator.serial) },
                    machineCell(MachineOwnerColumn) { MachineStateText(strings.notOwnedByWorktree) },
                    machineCell(MachineStateColumn) { StatusChip(emulator.state.status(), emulatorStateLabel(emulator.state, strings)) },
                    machineActionCell {
                        if (emulator.serial in machine.startingDevices) {
                            InlineProgress(size = ProgressSize.Small)
                        } else {
                            SecondaryButton(onClick = { machine.stopUnownedEmulator(emulator.serial) }) { Text(strings.stopEmulator) }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun WorktreeEmulatorRow(entry: MachineEmulator, machine: MachineResources, onOpenWorktree: (String, String) -> Unit) {
    val strings = LocalStrings.current
    val isBusy = machine.isBusy(entry.projectId, entry.worktreePath)
    val current = entry.emulator?.state ?: EmulatorState.STOPPED
    val state = if (isBusy && current != EmulatorState.RUNNING) EmulatorState.BOOTING else current
    MachineRow(
        machineCell(MachineMainColumn) { MachineDataText(emulatorDeviceLabel(entry, machine.project(entry.projectId), strings)) },
        machineCell(MachineOwnerColumn) {
            OwnerCell(ProjectOwner.Owned(entry.projectId, entry.worktreePath), machine::projectName, onOpenWorktree)
        },
        machineCell(MachineStateColumn) {
            if (entry.emulator == null && !isBusy) {
                StatusChip(Status.NEUTRAL, strings.notAcquired)
            } else {
                StatusChip(state.status(), emulatorStateLabel(state, strings))
            }
        },
        machineActionCell {
            when {
                isBusy -> InlineProgress(size = ProgressSize.Small)
                state == EmulatorState.STOPPED ->
                    SecondaryButton(onClick = { machine.startEmulator(entry.projectId, entry.worktreePath) }) { Text(strings.startEmulator) }
                else ->
                    SecondaryButton(onClick = { machine.stopEmulator(entry.projectId, entry.worktreePath) }) { Text(strings.stopEmulator) }
            }
        },
    )
}
