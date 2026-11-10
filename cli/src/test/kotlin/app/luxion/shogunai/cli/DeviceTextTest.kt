package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.DeviceAcquisition
import app.luxion.shogunai.domain.model.DeviceBlock
import app.luxion.shogunai.domain.model.DeviceConnection
import app.luxion.shogunai.domain.model.DeviceLoss
import app.luxion.shogunai.domain.model.DeviceLossReason
import app.luxion.shogunai.domain.model.DeviceOverview
import app.luxion.shogunai.domain.model.DevicePlatform
import app.luxion.shogunai.domain.model.DeviceUsability
import app.luxion.shogunai.domain.model.HeldDevice
import app.luxion.shogunai.domain.model.PhysicalDevice
import app.luxion.shogunai.domain.model.PhysicalDeviceLease
import app.luxion.shogunai.domain.model.PhysicalDeviceWaitProgress
import app.luxion.shogunai.domain.model.PhysicalDevicesState
import app.luxion.shogunai.domain.model.ProcessIdentity
import app.luxion.shogunai.domain.model.RecordedDevice
import app.luxion.shogunai.domain.model.WaitTicket
import app.luxion.shogunai.domain.model.toLocalClockTime
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceTextTest {

    private val now = Instant.parse("2026-01-05T14:00:00Z")
    private val admin = "/home/dev/projects/main-repo/.git/worktrees/TASK-1"
    private val worktree = "/home/dev/projects/worktrees/TASK-1"
    private val bare = RecordedDevice("android:S1", DevicePlatform.ANDROID, lastSeen = now.toEpochMilli())
    private val tablet = RecordedDevice("ios:U1", DevicePlatform.IOS, name = "Test iPad", lent = false, lastSeen = now.toEpochMilli())
    private val lease = PhysicalDeviceLease(bare.id, DevicePlatform.ANDROID, admin, worktree, now.toEpochMilli(), now.toEpochMilli())

    private fun held(connected: PhysicalDevice? = null, agent: AgentState? = null, device: RecordedDevice = bare) =
        HeldDevice(device, lease.copy(deviceId = device.id, platform = device.platform), connected, now + Duration.ofMinutes(20), agent)

    private fun state(devices: List<DeviceOverview>, waiting: Map<DevicePlatform, List<WaitTicket>> = emptyMap(), losses: List<DeviceLoss> = emptyList()) =
        PhysicalDevicesState(devices, emptyList(), DevicePlatform.entries.associateWith { waiting[it].orEmpty() }, losses, Duration.ofMinutes(20))

    private val ticket = WaitTicket("t", admin, worktree, ProcessIdentity(1, 1), now.toEpochMilli())
    private val until = (now + Duration.ofMinutes(20)).toLocalClockTime()

    @Test
    fun `a bare unusable or withdrawn device lists what is known about it`() {
        val unusable = PhysicalDevice(DevicePlatform.IOS, "U1", "U1", DeviceConnection.USB, DeviceUsability.DEVELOPER_MODE_OFF)
        val lines = deviceListLines(state(listOf(DeviceOverview(bare, null, held()), DeviceOverview(tablet, unusable, null))), all = true)

        assertTrue(lines[0].startsWith("android:S1 (android:S1)  Android  disconnected, last seen") && lines[0].endsWith("lent  held by main-repo · TASK-1 until $until"), lines[0])
        assertEquals("Test iPad (ios:U1)  iOS  USB  developer mode must be turned on  withdrawn", lines[1])
    }

    @Test
    fun `device json leaves the unknown connection empty and says whether the holder works`() {
        val json = deviceJson(DeviceOverview(bare, null, held(agent = AgentState.WORKING)))

        assertEquals(JsonNull, json["connection"])
        assertEquals(JsonNull, json["usability"])
        assertEquals(JsonNull, json["serial"])
        assertEquals(true, json.getValue("holder").jsonObject.getValue("agentWorking").jsonPrimitive.boolean)
        assertEquals("U1", deviceJson(DeviceOverview(tablet, null, null)).getValue("udid").jsonPrimitive.content)
    }

    @Test
    fun `the device list json leaves out disconnected devices unless all are asked for`() {
        val connected = PhysicalDevice(DevicePlatform.IOS, "U1", "U1", DeviceConnection.USB)
        val overviews = listOf(DeviceOverview(bare, null, null), DeviceOverview(tablet, connected, null))

        assertEquals(1, deviceListJson(state(overviews), all = false).size)
        assertEquals(2, deviceListJson(state(overviews), all = true).size)
    }

    @Test
    fun `a disconnected held device exports its identity and shows no connection`() {
        assertEquals("export SHOGUN_ANDROID_DEVICE_SERIAL=S1", deviceVariable(held()))
        assertEquals(JsonNull, heldDeviceJson(held())["connection"])
        assertEquals(false, heldDeviceJson(held()).getValue("connected").jsonPrimitive.boolean)
    }

    @Test
    fun `an acquisition names who lost the device and skips a model equal to the name`() {
        val pixel = bare.copy(model = "Pixel 8")
        val connected = PhysicalDevice(DevicePlatform.ANDROID, "S1", "S1", DeviceConnection.NETWORK)

        val taken = deviceAcquisitionLines(DeviceAcquisition(held(connected, device = pixel), renewed = false, waitedSeconds = 5, takenFrom = "main-repo · TASK-2"))
        val renewed = deviceAcquisitionLines(DeviceAcquisition(held(device = bare), renewed = true, waitedSeconds = null, takenFrom = null))

        assertEquals("acquired Android device Pixel 8, serial S1, network, lease until $until, taken from main-repo · TASK-2", taken[0])
        assertEquals("already held Android device android:S1, disconnected, lease until $until", renewed[0])
    }

    @Test
    fun `status says how a device was withdrawn and when a platform has none`() {
        val loss = DeviceLoss(admin, "/home/dev/projects/worktrees/TASK", DevicePlatform.IOS, tablet.id, "Test iPad", now.toEpochMilli(), DeviceLossReason.WITHDRAWN)

        val lines = deviceStatusLines(state(emptyList(), losses = listOf(loss)), admin)

        assertEquals(listOf("android: none", "ios: none (Test iPad was withdrawn by the user at ${now.toLocalClockTime()})"), lines)
    }

    @Test
    fun `status json gives the place in line of a waiting platform and the held device`() {
        val json = deviceStatusJson(state(listOf(DeviceOverview(tablet, null, held(device = tablet))), mapOf(DevicePlatform.ANDROID to listOf(ticket))), admin)

        assertEquals("U1", json.getValue("ios").jsonObject.getValue("device").jsonObject.getValue("udid").jsonPrimitive.content)
        assertEquals("1", json.getValue("android").jsonObject.getValue("waiting").jsonObject.getValue("position").jsonPrimitive.content)
        assertEquals(JsonNull, json.getValue("ios").jsonObject["waiting"])
    }

    @Test
    fun `without a read every worktree has no device`() {
        assertEquals(emptyList(), deviceStatusNotes(null, worktree))
        assertEquals(JsonNull, worktreeDevicesJson(null, worktree)["android"])
        val held = worktreeDevicesJson(state(listOf(DeviceOverview(bare.copy(model = "SM-A556B"), null, held(device = bare.copy(model = "SM-A556B"))))), worktree)
        assertEquals("SM-A556B", held.getValue("android").jsonObject.getValue("model").jsonPrimitive.content)
    }

    @Test
    fun `pool lines skip platforms with nothing lent or waiting and don't count unusable devices as free`() {
        val unusable = PhysicalDevice(DevicePlatform.ANDROID, "S1", "S1", DeviceConnection.USB, DeviceUsability.UNAUTHORIZED)
        val unplugged = bare.copy(id = "android:S2")
        val lines = devicePoolLines(
            state(listOf(DeviceOverview(bare, unusable, null), DeviceOverview(unplugged, null, null), DeviceOverview(tablet, null, null)), mapOf(DevicePlatform.IOS to listOf(ticket))),
        )

        assertEquals(
            listOf("Android physical devices: 0 of 1 free", "  android:S1 free", "iOS physical devices: 0 of 0 free", "  1. main-repo · TASK-1 waiting since ${now.toLocalClockTime()}"),
            lines,
        )
        val waiting = devicePoolJson(state(emptyList(), mapOf(DevicePlatform.IOS to listOf(ticket)))).getValue("ios").jsonObject.getValue("waiting").jsonArray.single().jsonObject
        assertEquals(worktree, waiting.getValue("worktree").jsonPrimitive.content)
        assertEquals("/home/dev/projects/main-repo", waiting.getValue("repository").jsonPrimitive.content)
    }

    @Test
    fun `wait progress explains every block or that earlier waiters go first`() {
        val blocks = listOf(
            DeviceBlock.Held(held(PhysicalDevice(DevicePlatform.ANDROID, "S1", "S1", DeviceConnection.USB), AgentState.WORKING)),
            DeviceBlock.Withdrawn(tablet),
            DeviceBlock.Unusable(tablet, DeviceUsability.UNPAIRED),
        )

        assertEquals(
            "waiting for an Android device: 1st of 2 in line; android:S1 held by main-repo · TASK-1 (agent working); Test iPad isn't lent to agents; Test iPad: not paired with this Mac",
            deviceWaitProgressLine(PhysicalDeviceWaitProgress(DevicePlatform.ANDROID, 1, 2, blocks)),
        )
        assertEquals(
            "waiting for an iOS device: 2nd of 2 in line, behind earlier waiters",
            deviceWaitProgressLine(PhysicalDeviceWaitProgress(DevicePlatform.IOS, 2, 2, emptyList())),
        )
    }
}
