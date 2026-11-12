package app.luxion.shogunai.ui.machine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.DeviceConnection
import app.luxion.shogunai.domain.model.DeviceOverview
import app.luxion.shogunai.domain.model.DevicePlatform
import app.luxion.shogunai.domain.model.DeviceUsability
import app.luxion.shogunai.domain.model.toLastSeenText
import app.luxion.shogunai.domain.model.toLocalClockTime
import app.luxion.shogunai.ui.components.ColumnSpec
import app.luxion.shogunai.ui.components.DangerButton
import app.luxion.shogunai.ui.components.Field
import app.luxion.shogunai.ui.components.GhostButton
import app.luxion.shogunai.ui.components.GroupTitle
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.PrimaryButton
import app.luxion.shogunai.ui.components.ProgressSize
import app.luxion.shogunai.ui.components.ShogunDialog
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.StatusChip
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.theme.Status
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun PhysicalDevicesView(machine: MachineResources, onOpenWorktree: (String, String) -> Unit) {
    val strings = LocalStrings.current
    val watch = machine.physicalDevices
    MachineViewFrame(strings.physicalDevicesTitle) {
        MachineSecondaryText(strings.physicalDevicesExplanation)
        MachineErrorNotice(watch.error, watch::dismissError)
        watch.devices.forEach { overview ->
            HorizontalDivider()
            DeviceRow(overview, machine, onOpenWorktree)
        }
        DevicePlatform.entries.forEach { platform ->
            val waiting = watch.waiting(platform)
            if (waiting.isNotEmpty()) {
                GroupTitle(strings.devicesWaitingTitle(platform.label))
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
    }
    DeviceActionDialog(watch)
}

@Composable
private fun DeviceRow(overview: DeviceOverview, machine: MachineResources, onOpenWorktree: (String, String) -> Unit) {
    val strings = LocalStrings.current
    val watch = machine.physicalDevices
    val scope = rememberCoroutineScope()
    val device = overview.device
    val holder = overview.holder
    MachineRow(
        machineCell(MachineMainColumn) {
            DeviceLabelText(DeviceLabel(data = listOfNotNull(device.marketingName ?: device.model, device.osVersion?.let { "${device.platform.label} $it" }).joinToString(" · "), note = device.displayName))
        },
        machineCell(MachineOwnerColumn) {
            if (holder == null) {
                MachineStateText(if (device.lent) strings.deviceFree else strings.deviceWithdrawn)
            } else {
                OwnerCell(machine.ownerOfPath(holder.lease.worktreePath), machine::projectName, onOpenWorktree)
            }
        },
        machineCell(DeviceStateColumn) { DeviceState(overview, strings) },
        machineCell(DeviceActionsColumn) {
            if (watch.busyDevice == device.id) {
                InlineProgress(size = ProgressSize.Small)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.testTag("device-actions-${device.id}")) {
                    if (holder != null) GhostButton(onClick = { watch.request(PendingDeviceAction.Release(overview)) }) { Text(strings.releaseDevice) }
                    if (device.lent) {
                        GhostButton(onClick = { scope.launch { watch.withdraw(overview) } }) { Text(strings.withdrawDevice) }
                    } else {
                        GhostButton(onClick = { scope.launch { watch.lend(overview) } }) { Text(strings.lendDevice) }
                    }
                    GhostButton(onClick = { watch.request(PendingDeviceAction.Rename(overview)) }) { Text(strings.renameDevice) }
                    if (overview.connected == null) GhostButton(onClick = { watch.request(PendingDeviceAction.Forget(overview)) }) { Text(strings.forgetDevice) }
                }
            }
        },
    )
}

@Composable
private fun DeviceState(overview: DeviceOverview, strings: Strings) {
    val connected = overview.connected
    val holder = overview.holder
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        when {
            connected == null -> StatusChip(Status.NEUTRAL, strings.deviceLastSeen(Instant.ofEpochMilli(overview.device.lastSeen).toLastSeenText()))
            !connected.isUsable -> StatusChip(Status.WARNING, usabilityLabel(connected.usability, strings))
            else -> StatusChip(Status.SUCCESS, strings.deviceConnection(connected.connection == DeviceConnection.NETWORK))
        }
        holder?.let { MachineStateText(strings.deviceHeldUntil(it.expiresAt.toLocalClockTime())) }
    }
}

fun usabilityLabel(usability: DeviceUsability, strings: Strings): String = when (usability) {
    DeviceUsability.UNAUTHORIZED -> strings.deviceUnauthorized
    DeviceUsability.OFFLINE -> strings.deviceOffline
    DeviceUsability.UNPAIRED -> strings.deviceUnpaired
    DeviceUsability.DEVELOPER_MODE_OFF -> strings.deviceDeveloperModeOff
    DeviceUsability.CONNECTING, DeviceUsability.USABLE -> strings.deviceConnecting
}

@Composable
private fun DeviceActionDialog(watch: PhysicalDevicesWatch) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val pending = watch.pending ?: return
    val name = pending.device.device.displayName
    val holder = pending.device.holder?.holderName.orEmpty()
    when (pending) {
        is PendingDeviceAction.Release -> ConfirmDeviceDialog(
            title = strings.confirmReleaseDeviceTitle,
            message = strings.confirmReleaseDeviceMessage(name, holder),
            action = strings.releaseDevice,
            onConfirm = { scope.launch { watch.confirm() } },
            onDismiss = watch::cancel,
        )
        is PendingDeviceAction.Withdraw -> ConfirmDeviceDialog(
            title = strings.confirmWithdrawDeviceTitle,
            message = strings.confirmWithdrawDeviceMessage(name, holder),
            action = strings.withdrawDevice,
            onConfirm = { scope.launch { watch.confirm() } },
            onDismiss = watch::cancel,
        )
        is PendingDeviceAction.Forget -> ConfirmDeviceDialog(
            title = strings.confirmForgetDeviceTitle,
            message = strings.confirmForgetDeviceMessage(name),
            action = strings.forgetDevice,
            onConfirm = { scope.launch { watch.confirm() } },
            onDismiss = watch::cancel,
        )
        is PendingDeviceAction.Rename -> RenameDeviceDialog(
            current = pending.device.device.alias.orEmpty(),
            onConfirm = { alias -> scope.launch { watch.confirm(alias) } },
            onDismiss = watch::cancel,
        )
    }
}

@Composable
fun ConfirmDeviceDialog(title: String, message: String, action: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    ShogunDialog(
        title = title,
        onDismissRequest = onDismiss,
        confirm = { DangerButton(onClick = onConfirm) { Text(action) } },
        dismiss = { NeutralTextButton(onClick = onDismiss) { Text(strings.cancel) } },
    ) {
        Text(message)
    }
}

@Composable
private fun RenameDeviceDialog(current: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    var alias by remember { mutableStateOf(current) }
    ShogunDialog(
        title = strings.renameDeviceTitle,
        onDismissRequest = onDismiss,
        confirm = { PrimaryButton(onClick = { onConfirm(alias) }) { Text(strings.save) } },
        dismiss = { NeutralTextButton(onClick = onDismiss) { Text(strings.cancel) } },
    ) {
        Field(value = alias, onValueChange = { alias = it }, label = strings.deviceAliasLabel, inputModifier = Modifier.testTag("device-alias"))
    }
}

private val PositionColumnWidth = 24.dp
private val DeviceStateColumn = ColumnSpec(width = 200.dp)
private val DeviceActionsColumn = ColumnSpec(width = 300.dp)
