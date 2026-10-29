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
import app.luxion.shogunai.domain.model.DerivedDataFolder
import app.luxion.shogunai.ui.components.DangerTextButton
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.Notice
import app.luxion.shogunai.ui.components.NoticeKind
import app.luxion.shogunai.ui.components.ColumnSpec
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.worktree.formatSize
import app.luxion.shogunai.ui.worktree.relativeTime
import java.time.Instant

@Composable
fun XcodeDerivedDataView(machine: MachineResources, onOpenWorktree: (String, String) -> Unit) {
    val strings = LocalStrings.current
    var confirmingDelete by remember { mutableStateOf(false) }
    MachineViewFrame(strings.xcodeDerivedDataTitle) {
        MachineErrorNotice(machine.error, machine::dismissError, machine.errorRetry?.let { { machine.retryError() } })
        when (val state = machine.derivedData) {
            MachineLoad.Loading -> InlineProgress()
            MachineLoad.Unavailable -> MachineSecondaryText(strings.xcodeDerivedDataUnavailable)
            is MachineLoad.Loaded -> LoadedDerivedData(state.value, machine, onOpenWorktree) { confirmingDelete = true }
        }
        machine.derivedDataTrimReport?.let { report ->
            Notice(
                kind = NoticeKind.SUCCESS,
                message = strings.derivedDataReleased(report.released.size, formatSize(report.bytesFreed)),
                onDismiss = machine::dismissDerivedDataTrimReport,
            )
            report.released.forEach { entry ->
                val worktree = (entry.owner as? app.luxion.shogunai.domain.model.DerivedDataOwner.Worktree)?.path?.substringAfterLast('/')
                MachineDataText(listOfNotNull(entry.folder.workspacePath.substringAfterLast('/'), worktree).joinToString(" · "))
            }
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
            message = strings.confirmDeleteDerivedDataOrphans(orphans.size, totalSize(orphans.map { it.sizeBytes })?.let(::formatSize)),
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
    val sizeText: (Long?) -> String = { size -> size?.let(::formatSize) ?: strings.calculating }
    val kept = folders.filter { it.owner == MachineDerivedDataOwner.UnavailableVolume }
    val orphans = folders.filter { it.owner == MachineDerivedDataOwner.Orphaned }
    val owned = folders - orphans.toSet()
    if (folders.isEmpty()) MachineSecondaryText(strings.noDerivedData)
    if (folders.isNotEmpty()) {
        MachineDataText(strings.derivedDataSummary(folders.size, sizeText(totalSize(folders.map { it.folder.sizeBytes }))))
    }
    val quota = machine.derivedDataQuotaBytes
    if (machine.isDerivedDataQuotaUnknown) MachineDataText(strings.derivedDataQuotaUnknown)
    if (quota != null) {
        MachineDataText(strings.derivedDataQuotaUsage(sizeText(machine.derivedDataUsageBytes), formatSize(quota), machine.isDerivedDataQuotaAutomatic))
        if (machine.derivedDataQuotaExceeded) {
            MachineDataText(strings.derivedDataQuotaExceeded(machine.derivedDataInUse))
        }
    }
    owned.forEach { entry ->
        HorizontalDivider()
        MachineRow(
            machineCell(MachineMainColumn) { MachineDataText(folderLabel(entry.folder, strings)) },
            machineCell(MachineSizeColumn) { MachineDataText(sizeText(entry.folder.sizeBytes)) },
            machineCell(LastAccessColumn) {
                MachineStateText(entry.folder.lastAccessed?.let { relativeTime(it, Instant.now(), strings) }.orEmpty())
            },
            machineCell(MachineOwnerColumn) {
                when (val owner = entry.owner) {
                    is MachineDerivedDataOwner.Project -> OwnerCell(owner.owner, machine::projectName, onOpenWorktree)
                    MachineDerivedDataOwner.UnavailableVolume -> MachineStateText(strings.derivedDataKept)
                    MachineDerivedDataOwner.Orphaned -> Unit
                }
            },
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MachineDataText(strings.derivedDataOrphans(orphans.size, sizeText(totalSize(orphans.map { it.folder.sizeBytes }))))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (machine.isDeletingDerivedData) InlineProgress(modifier = Modifier.padding(end = Spacing.sm))
            DangerTextButton(onClick = onDeleteOrphans, enabled = !machine.isDeletingDerivedData && orphans.isNotEmpty()) {
                Text(strings.deleteOrphans)
            }
        }
    }
    if (kept.isNotEmpty()) MachineSecondaryText(strings.derivedDataOnUnavailableVolumes(kept.size))
    if (machine.isCompilationCacheCalculating) {
        MachineDataText(strings.derivedDataCompilationCache(strings.calculating))
    } else {
        machine.compilationCacheBytes?.let { MachineDataText(strings.derivedDataCompilationCache(formatSize(it))) }
    }
    machine.packageCacheSizes?.swiftPmBytes?.let { MachineDataText(strings.derivedDataSwiftPmCache(formatSize(it))) }
    machine.packageCacheSizes?.cocoaPodsBytes?.let { MachineDataText(strings.derivedDataCocoaPodsCache(formatSize(it))) }
}

private fun folderLabel(folder: DerivedDataFolder, strings: Strings): String {
    val name = folder.workspacePath.substringAfterLast('/')
    val packages = folder.packagesBytes?.takeIf { it > 0 } ?: return name
    return strings.derivedDataWithPackages(name, formatSize(packages))
}

private fun totalSize(sizes: List<Long?>): Long? = if (sizes.any { it == null }) null else sizes.sumOf { it ?: 0L }

private val LastAccessColumn = ColumnSpec(width = 120.dp)
