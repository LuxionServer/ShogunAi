package app.luxion.shogunai.ui.machine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.DyldCaches
import app.luxion.shogunai.domain.model.RuntimeRefusal
import app.luxion.shogunai.domain.model.RuntimeUser
import app.luxion.shogunai.domain.model.SimulatorStorage
import app.luxion.shogunai.domain.model.StoredRuntime
import app.luxion.shogunai.domain.model.dyldRemovalCommand
import app.luxion.shogunai.ui.components.ColumnSpec
import app.luxion.shogunai.ui.components.DangerButton
import app.luxion.shogunai.ui.components.DangerTextButton
import app.luxion.shogunai.ui.components.GroupTitle
import app.luxion.shogunai.ui.components.InlineConfirmButton
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.Notice
import app.luxion.shogunai.ui.components.NoticeKind
import app.luxion.shogunai.ui.components.ShogunDialog
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.copyText
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.worktree.formatSize
import app.luxion.shogunai.ui.worktree.relativeTime
import java.time.Instant
import kotlinx.coroutines.launch

private sealed interface StorageConfirmation {
    data class Runtime(val runtime: StoredRuntime) : StorageConfirmation
    data class Unavailable(val count: Int, val bytes: Long) : StorageConfirmation
}

@Composable
fun SimulatorStorageSection(storage: MachineSimulatorStorage) {
    val strings = LocalStrings.current
    var confirming by remember { mutableStateOf<StorageConfirmation?>(null) }
    LaunchedEffect(storage) { storage.load() }
    GroupTitle(strings.simulatorStorageTitle)
    MachineErrorNotice(storage.error, storage::dismissError)
    when (val state = storage.storage) {
        MachineLoad.Loading -> InlineProgress()
        MachineLoad.Unavailable -> MachineSecondaryText(strings.simulatorStorageUnavailable)
        is MachineLoad.Loaded -> LoadedStorage(state.value, storage.isDeleting) { confirming = it }
    }
    storage.report?.let { report ->
        Notice(kind = NoticeKind.SUCCESS, message = reportText(report, strings), onDismiss = storage::dismissReport)
    }
    confirming?.let { confirmation ->
        ConfirmStorageDeletion(
            confirmation,
            onConfirm = {
                confirming = null
                when (confirmation) {
                    is StorageConfirmation.Runtime -> storage.deleteRuntime(confirmation.runtime)
                    is StorageConfirmation.Unavailable -> storage.deleteUnavailable()
                }
            },
            onDismiss = { confirming = null },
        )
    }
}

@Composable
private fun LoadedStorage(storage: SimulatorStorage, isDeleting: Boolean, onConfirm: (StorageConfirmation) -> Unit) {
    val strings = LocalStrings.current
    if (storage.runtimes.isEmpty()) {
        MachineSecondaryText(strings.noSimulatorRuntimes)
    } else {
        MachineDataText(strings.simulatorRuntimesSummary(storage.runtimes.size, formatSize(storage.runtimeBytes)))
    }
    storage.runtimes.forEach { runtime ->
        HorizontalDivider()
        RuntimeRow(runtime, isDeleting) { onConfirm(StorageConfirmation.Runtime(runtime)) }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MachineDataText(strings.unavailableDevices(storage.unavailableDevices.size, formatSize(storage.unavailableBytes)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isDeleting) InlineProgress(modifier = Modifier.padding(end = Spacing.sm))
            DangerTextButton(
                onClick = { onConfirm(StorageConfirmation.Unavailable(storage.unavailableDevices.size, storage.unavailableBytes)) },
                enabled = storage.unavailableDevices.isNotEmpty() && !isDeleting,
                modifier = Modifier.testTag("delete-unavailable-devices"),
            ) { Text(strings.deleteUnavailableDevices) }
        }
    }
    DyldCacheRows(storage.dyldCaches)
}

@Composable
private fun RuntimeRow(runtime: StoredRuntime, isDeleting: Boolean, onDelete: () -> Unit) {
    val strings = LocalStrings.current
    MachineRow(
        machineCell(MachineMainColumn) { MachineDataText("${runtime.image.name} (${runtime.image.build})") },
        machineCell(MachineSizeColumn) { MachineDataText(sizeText(runtime.image.sizeBytes, strings)) },
        machineCell(LastUsedColumn) {
            val lastUsed = runtime.image.lastUsedAt
            MachineStateText(if (lastUsed == null) strings.runtimeNeverUsed else relativeTime(lastUsed, Instant.now(), strings))
        },
        machineCell(MachineOwnerColumn) { MachineStateText(runtimeStatus(runtime, strings)) },
        machineActionCell {
            DangerTextButton(
                onClick = onDelete,
                enabled = runtime.canDelete && !isDeleting,
                modifier = Modifier.testTag("delete-runtime-${runtime.image.identifier}"),
            ) { Text(strings.deleteRuntime) }
        },
    )
}

@Composable
private fun DyldCacheRows(caches: DyldCaches) {
    if (caches.folders.isEmpty()) return
    val strings = LocalStrings.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    MachineDataText(strings.dyldCachesSummary(formatSize(caches.totalBytes)))
    caches.folders.forEach { folder ->
        HorizontalDivider()
        val stale = caches.isStale(folder)
        MachineRow(
            machineCell(MachineMainColumn) { MachineDataText(folder.build) },
            machineCell(MachineSizeColumn) { MachineDataText(sizeText(folder.sizeBytes, strings)) },
            machineCell(LastUsedColumn) {},
            machineCell(MachineOwnerColumn) { MachineStateText(if (stale) strings.dyldCacheStale else strings.dyldCacheCurrent) },
            machineActionCell {
                if (stale) {
                    InlineConfirmButton(
                        label = strings.copyRemovalCommand,
                        onClick = { scope.launch { clipboard.copyText(dyldRemovalCommand(folder)) } },
                        modifier = Modifier.testTag("copy-dyld-${folder.build}"),
                    )
                }
            },
        )
    }
}

@Composable
private fun ConfirmStorageDeletion(confirmation: StorageConfirmation, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    val (title, message, action) = when (confirmation) {
        is StorageConfirmation.Runtime -> Triple(
            strings.confirmDeleteRuntimeTitle,
            strings.confirmDeleteRuntime(confirmation.runtime.image.name, sizeText(confirmation.runtime.image.sizeBytes, strings)),
            strings.deleteRuntime,
        )
        is StorageConfirmation.Unavailable -> Triple(
            strings.confirmDeleteUnavailableDevicesTitle,
            strings.confirmDeleteUnavailableDevices(confirmation.count, formatSize(confirmation.bytes)),
            strings.deleteUnavailableDevices,
        )
    }
    ShogunDialog(
        title = title,
        onDismissRequest = onDismiss,
        confirm = { DangerButton(onClick = onConfirm) { Text(action) } },
        dismiss = { NeutralTextButton(onClick = onDismiss) { Text(strings.cancel) } },
    ) {
        Text(message)
    }
}

internal fun runtimeStatus(runtime: StoredRuntime, strings: Strings): String = when (runtime.refusal) {
    null -> strings.runtimeUnused
    RuntimeRefusal.BUNDLED -> strings.runtimeBundled
    RuntimeRefusal.BOOTED -> strings.runtimeBooted
    RuntimeRefusal.IN_USE -> strings.runtimeUsedBy(runtime.users.map { userLabel(it, strings) })
}

private fun userLabel(user: RuntimeUser, strings: Strings): String =
    if (user == RuntimeUser.Default) strings.runtimeDefault else user.label

internal fun reportText(report: SimulatorStorageReport, strings: Strings): String = when (report) {
    is SimulatorStorageReport.Runtime ->
        strings.runtimeDeleted(report.deletion.runtime.image.name, formatSize(report.deletion.bytesFreed), report.deletion.unavailableLeft.size)
    is SimulatorStorageReport.Unavailable ->
        strings.unavailableDevicesDeleted(report.deletion.devices.size, formatSize(report.deletion.bytesFreed))
}

private fun sizeText(bytes: Long?, strings: Strings): String = if (bytes == null) strings.sizeUnknown else formatSize(bytes)

private val LastUsedColumn = ColumnSpec(width = 120.dp)
