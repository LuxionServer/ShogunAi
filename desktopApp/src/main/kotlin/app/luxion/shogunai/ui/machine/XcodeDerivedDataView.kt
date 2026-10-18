package app.luxion.shogunai.ui.machine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.ui.components.DangerTextButton
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.Notice
import app.luxion.shogunai.ui.components.NoticeKind
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.worktree.formatSize
import app.luxion.shogunai.ui.worktree.relativeTime
import java.time.Instant

@Composable
fun XcodeDerivedDataView(machine: MachineResources, onOpenWorktree: (String, String) -> Unit) {
    val strings = LocalStrings.current
    var confirmingDelete by remember { mutableStateOf(false) }
    MachineViewFrame(strings.xcodeDerivedDataTitle) {
        MachineErrorNotice(machine.error, machine::dismissError)
        when (val state = machine.derivedData) {
            MachineLoad.Loading -> InlineProgress()
            MachineLoad.Unavailable -> MachineSecondaryText(strings.xcodeDerivedDataUnavailable)
            is MachineLoad.Loaded -> LoadedDerivedData(state.value, machine, onOpenWorktree) { confirmingDelete = true }
        }
        machine.derivedDataDeletionReport?.let { report ->
            Notice(
                kind = NoticeKind.SUCCESS,
                message = strings.derivedDataDeleted(report.deleted.size, formatSize(report.bytesFreed)),
                onDismiss = machine::dismissDerivedDataDeletionReport,
            )
        }
    }
    if (confirmingDelete) {
        val orphans = machine.derivedDataOrphans
        ConfirmDeleteOrphansDialog(
            message = strings.confirmDeleteDerivedDataOrphans(orphans.size, formatSize(orphans.sumOf { it.sizeBytes })),
            onConfirm = {
                confirmingDelete = false
                machine.deleteOrphanedDerivedData()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
private fun LoadedDerivedData(
    folders: List<MachineDerivedData>,
    machine: MachineResources,
    onOpenWorktree: (String, String) -> Unit,
    onDeleteOrphans: () -> Unit,
) {
    val strings = LocalStrings.current
    val kept = folders.filter { it.owner == MachineDerivedDataOwner.UnavailableVolume }
    val orphans = folders.filter { it.owner == MachineDerivedDataOwner.Orphaned }
    val owned = folders - orphans.toSet()
    if (folders.isEmpty()) MachineSecondaryText(strings.noDerivedData)
    if (folders.isNotEmpty()) {
        MachineDataText(strings.derivedDataSummary(folders.size, formatSize(folders.sumOf { it.folder.sizeBytes })))
    }
    owned.forEach { entry ->
        HorizontalDivider()
        MachineRowLine {
            MachineDataText(entry.folder.workspacePath.substringAfterLast('/'), Modifier.weight(1f))
            MachineDataText(formatSize(entry.folder.sizeBytes), Modifier.width(80.dp))
            MachineDataText(
                entry.folder.lastAccessed?.let { relativeTime(it, Instant.now(), strings) }.orEmpty(),
                Modifier.width(120.dp),
            )
            when (val owner = entry.owner) {
                is MachineDerivedDataOwner.Project ->
                    OwnerCell(owner.owner, machine::projectName, onOpenWorktree, Modifier.width(MachineOwnerColumnWidth))
                MachineDerivedDataOwner.UnavailableVolume -> MachineDataText(strings.derivedDataKept, Modifier.width(MachineOwnerColumnWidth))
                MachineDerivedDataOwner.Orphaned -> Unit
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MachineDataText(strings.derivedDataOrphans(orphans.size, formatSize(orphans.sumOf { it.folder.sizeBytes })))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (machine.isDeletingDerivedData) InlineProgress(modifier = Modifier.padding(end = Spacing.sm))
            DangerTextButton(onClick = onDeleteOrphans, enabled = !machine.isDeletingDerivedData && orphans.isNotEmpty()) {
                Text(strings.deleteOrphans)
            }
        }
    }
    if (kept.isNotEmpty()) MachineSecondaryText(strings.derivedDataOnUnavailableVolumes(kept.size))
    machine.compilationCacheBytes?.let { MachineDataText(strings.derivedDataCompilationCache(formatSize(it))) }
}
