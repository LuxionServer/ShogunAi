package app.luxion.shogunai.ui.machine

import app.luxion.shogunai.MachineUseCases
import app.luxion.shogunai.domain.model.DeviceConnection
import app.luxion.shogunai.domain.model.DeviceLoss
import app.luxion.shogunai.domain.model.DeviceLossReason
import app.luxion.shogunai.domain.model.DeviceOverview
import app.luxion.shogunai.domain.model.DevicePlatform
import app.luxion.shogunai.domain.model.DeviceRelease
import app.luxion.shogunai.domain.model.HeldDevice
import app.luxion.shogunai.domain.model.PhysicalDevice
import app.luxion.shogunai.domain.model.PhysicalDeviceLease
import app.luxion.shogunai.domain.model.PhysicalDevicesState
import app.luxion.shogunai.domain.model.ProcessIdentity
import app.luxion.shogunai.domain.model.RecordedDevice
import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.domain.model.WaitTicket
import kotlinx.coroutines.flow.emptyFlow
import java.time.Duration
import java.time.Instant

class PhysicalDevicesFixture {
    val holderPath = "/home/dev/projects/api-TASK-1"
    val waiterPath = "/home/dev/projects/api-TASK-2"
    val holderAdmin = "/home/dev/projects/api/.git/worktrees/api-TASK-1"
    val waiterAdmin = "/home/dev/projects/api/.git/worktrees/api-TASK-2"
    val now: Instant = Instant.parse("2026-01-05T14:00:00Z")
    val expiresAt: Instant = now.plus(Duration.ofMinutes(20))
    val lastSeen: Instant = Instant.parse("2026-01-02T09:30:00Z")

    val pixel = RecordedDevice("android:3A281FDJH000AB", DevicePlatform.ANDROID, name = "Pixel 8", model = "Pixel 8", marketingName = "Pixel 8", osVersion = "16", lastSeen = now.toEpochMilli())
    val a55 = RecordedDevice("android:R5CT10ABCDE", DevicePlatform.ANDROID, alias = "a55", model = "SM-A556B", marketingName = "Galaxy A55 5G", osVersion = "15", lastSeen = lastSeen.toEpochMilli())
    val connectedPixel = PhysicalDevice(DevicePlatform.ANDROID, "3A281FDJH000AB", "3A281FDJH000AB", DeviceConnection.USB, model = "Pixel 8")
    val pixelLease = PhysicalDeviceLease(pixel.id, DevicePlatform.ANDROID, holderAdmin, holderPath, now.toEpochMilli(), now.toEpochMilli())
    val ticket = WaitTicket("t", waiterAdmin, waiterPath, ProcessIdentity(5, 1), now.toEpochMilli(), "platform=android")

    var state = initial()
    val released = mutableListOf<String>()
    val withdrawn = mutableListOf<String>()
    val lent = mutableListOf<String>()
    val forgotten = mutableListOf<String>()
    val renamed = mutableListOf<Pair<String, String>>()
    var reads = 0
    var shouldRead = true

    fun initial() = PhysicalDevicesState(
        devices = listOf(
            DeviceOverview(pixel, connectedPixel, HeldDevice(pixel, pixelLease, connectedPixel, expiresAt, null)),
            DeviceOverview(a55, null, null),
        ),
        pending = emptyList(),
        waiting = mapOf(DevicePlatform.ANDROID to listOf(ticket), DevicePlatform.IOS to emptyList()),
        losses = emptyList(),
        leaseDuration = Duration.ofMinutes(20),
    )

    fun withoutHolder() {
        state = state.copy(devices = state.devices.map { it.copy(holder = null) })
    }

    fun loss(reason: DeviceLossReason) =
        DeviceLoss(waiterAdmin, waiterPath, DevicePlatform.ANDROID, a55.id, "a55", now.toEpochMilli(), reason, "api · api-TASK-1".takeIf { reason == DeviceLossReason.TAKEN })

    val watch = PhysicalDevicesWatch(
        read = {
            reads++
            Result.success(state)
        },
        actions = PhysicalDeviceActions(
            release = { id ->
                released += id
                withoutHolder()
                Result.success(DeviceRelease(emptyList()))
            },
            lend = { id ->
                lent += id
                state = state.copy(devices = state.devices.map { if (it.device.id == id) it.copy(device = it.device.copy(lent = true)) else it })
                Result.success(a55)
            },
            withdraw = { id ->
                withdrawn += id
                state = state.copy(devices = state.devices.map { if (it.device.id == id) it.copy(device = it.device.copy(lent = false), holder = null) else it })
                Result.success(a55)
            },
            rename = { id, alias ->
                renamed += id to alias
                Result.success(a55)
            },
            forget = { id ->
                forgotten += id
                state = state.copy(devices = state.devices.filterNot { it.device.id == id })
                Result.success(a55)
            },
        ),
        shouldRead = { shouldRead },
    )

    fun unusedMachineUseCases(): MachineUseCases {
        val unused = IllegalStateException("not used")
        return MachineUseCases(
            listDaemons = { Result.failure(unused) },
            stopDaemons = { Result.failure(unused) },
            listDerivedData = { Result.failure(unused) },
            measureDerivedData = { _, _ -> emptyFlow() },
            measureCompilationCache = { Result.failure(unused) },
            deleteOrphanedDerivedData = { Result.failure(unused) },
            readEmulatorStates = { _, _ -> Result.failure(unused) },
            readRunningEmulators = { Result.failure(unused) },
            startEmulator = { _, _ -> Result.failure(unused) },
            readPool = { _, _, _, _ -> Result.failure(unused) },
            trimGradleDaemons = { Result.failure(unused) },
            resourceSettings = { ResourceSettings() },
            stopEmulator = { _, _ -> Result.failure(unused) },
            stopEmulatorSerials = { _, _ -> Result.failure(unused) },
            readSimulatorStates = { _, _ -> Result.failure(unused) },
            startSimulator = { _, _ -> Result.failure(unused) },
            stopSimulator = { _, _ -> Result.failure(unused) },
            readSimulatorOrphans = { Result.failure(unused) },
            deleteSimulators = { Result.failure(unused) },
        )
    }
}
