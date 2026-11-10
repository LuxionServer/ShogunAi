package app.luxion.shogunai.ui.machine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.luxion.shogunai.domain.model.DeviceLoss
import app.luxion.shogunai.domain.model.DeviceOverview
import app.luxion.shogunai.domain.model.DevicePlatform
import app.luxion.shogunai.domain.model.DeviceRelease
import app.luxion.shogunai.domain.model.HeldDevice
import app.luxion.shogunai.domain.model.PhysicalDevicesState
import app.luxion.shogunai.domain.model.RecordedDevice
import app.luxion.shogunai.domain.model.WaitTicket

data class DeviceWaitPlace(val platform: DevicePlatform, val position: Int, val ticket: WaitTicket)

class PhysicalDeviceActions(
    val release: suspend (deviceId: String) -> Result<DeviceRelease>,
    val lend: suspend (deviceId: String) -> Result<RecordedDevice>,
    val withdraw: suspend (deviceId: String) -> Result<RecordedDevice>,
    val rename: suspend (deviceId: String, alias: String) -> Result<RecordedDevice>,
    val forget: suspend (deviceId: String) -> Result<RecordedDevice>,
) {
    companion object {
        private val NOTHING: Result<RecordedDevice> = Result.failure(IllegalStateException("no physical devices"))

        val NONE = PhysicalDeviceActions(
            release = { Result.success(DeviceRelease(emptyList())) },
            lend = { NOTHING },
            withdraw = { NOTHING },
            rename = { _, _ -> NOTHING },
            forget = { NOTHING },
        )
    }
}

sealed interface PendingDeviceAction {
    val device: DeviceOverview

    data class Release(override val device: DeviceOverview) : PendingDeviceAction

    data class Withdraw(override val device: DeviceOverview) : PendingDeviceAction

    data class Forget(override val device: DeviceOverview) : PendingDeviceAction

    data class Rename(override val device: DeviceOverview) : PendingDeviceAction
}

class PhysicalDevicesWatch(
    private val read: suspend () -> Result<PhysicalDevicesState>,
    private val actions: PhysicalDeviceActions,
    private val shouldRead: suspend () -> Boolean,
) {
    var state by mutableStateOf<PhysicalDevicesState?>(null)
        private set
    var pending by mutableStateOf<PendingDeviceAction?>(null)
        private set
    var busyDevice by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<Throwable?>(null)
        private set

    val devices: List<DeviceOverview> get() = state?.devices.orEmpty()

    val isActive: Boolean get() = devices.isNotEmpty()

    val connectedCount: Int get() = devices.count { it.connected != null }

    val heldCount: Int get() = devices.count { it.holder != null }

    suspend fun refresh() {
        if (!shouldRead()) {
            state = null
            return
        }
        read().onSuccess { state = it }
    }

    fun heldBy(worktreePath: String): List<HeldDevice> =
        devices.mapNotNull { it.holder }.filter { samePath(it.lease.worktreePath, worktreePath) }.sortedBy { it.lease.platform }

    fun waitsOf(worktreePath: String): List<DeviceWaitPlace> = DevicePlatform.entries.mapNotNull { platform ->
        val queue = state?.waiting?.get(platform).orEmpty()
        val index = queue.indexOfFirst { samePath(it.worktreePath, worktreePath) }
        if (index < 0) null else DeviceWaitPlace(platform, index + 1, queue[index])
    }

    fun lossesOf(worktreePath: String): List<DeviceLoss> {
        val held = heldBy(worktreePath).map { it.lease.platform }.toSet()
        return state?.losses.orEmpty().filter { samePath(it.worktreePath, worktreePath) && it.platform !in held }
    }

    fun waiting(platform: DevicePlatform): List<WaitTicket> = state?.waiting?.get(platform).orEmpty()

    fun request(action: PendingDeviceAction) {
        pending = action
    }

    fun cancel() {
        pending = null
    }

    suspend fun lend(device: DeviceOverview) = act(device) { actions.lend(it.device.id) }

    suspend fun withdraw(device: DeviceOverview) {
        if (device.holder == null) act(device) { actions.withdraw(it.device.id) } else request(PendingDeviceAction.Withdraw(device))
    }

    suspend fun confirm(alias: String = "") {
        val action = pending ?: return
        pending = null
        act(action.device) { device ->
            when (action) {
                is PendingDeviceAction.Release -> actions.release(device.device.id)
                is PendingDeviceAction.Withdraw -> actions.withdraw(device.device.id)
                is PendingDeviceAction.Forget -> actions.forget(device.device.id)
                is PendingDeviceAction.Rename -> actions.rename(device.device.id, alias)
            }
        }
    }

    fun dismissError() {
        error = null
    }

    private suspend fun act(device: DeviceOverview, action: suspend (DeviceOverview) -> Result<*>) {
        busyDevice = device.device.id
        try {
            action(device).onFailure { error = it }
            refresh()
        } finally {
            busyDevice = null
        }
    }

    private fun samePath(first: String, second: String): Boolean = first.comparablePath() == second.comparablePath()

    private fun String.comparablePath(): String = replace('\\', '/').trimEnd('/')

    companion object {
        val NONE: PhysicalDevicesWatch
            get() = PhysicalDevicesWatch(read = { Result.failure(IllegalStateException("no physical devices")) }, actions = PhysicalDeviceActions.NONE, shouldRead = { false })
    }
}
