package app.luxion.shogunai.domain.model

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhysicalDeviceTest {

    private val base = RecordedDevice("android:R5CT10ABCDE", DevicePlatform.ANDROID, lastSeen = 0)

    @Test
    fun `a recorded device is named by its alias, then marketing name, name, model and id`() {
        assertEquals("a55", base.copy(alias = "a55", marketingName = "Galaxy A55 5G").displayName)
        assertEquals("Galaxy A55 5G", base.copy(marketingName = "Galaxy A55 5G", name = "phone").displayName)
        assertEquals("phone", base.copy(name = "phone", model = "SM-A556B").displayName)
        assertEquals("SM-A556B", base.copy(model = "SM-A556B").displayName)
        assertEquals("android:R5CT10ABCDE", base.displayName)
    }

    @Test
    fun `a request is encoded for the queue and decoded back`() {
        val full = DeviceRequest(DevicePlatform.IOS, name = "ipad", model = "Air")

        assertEquals(full, DeviceRequest.decode(full.encode()))
        assertEquals(DeviceRequest(), DeviceRequest.decode(DeviceRequest().encode()))
        assertEquals(DeviceRequest(model = "a55"), DeviceRequest.decode("platform=tv\nmodel=a55\nnoise"))
        assertNull(DeviceRequest.decode(null))
    }

    @Test
    fun `a request describes what it asks for`() {
        assertEquals("a55", DeviceRequest(name = "a55", model = "x").describe())
        assertEquals("a device matching \"pixel\"", DeviceRequest(model = "pixel").describe())
        assertEquals("an Android device", DeviceRequest(DevicePlatform.ANDROID).describe())
        assertEquals("a physical device", DeviceRequest().describe())
    }

    @Test
    fun `a model filter looks at the model and the marketing name`() {
        val inventory = listOf(base.copy(model = "SM-A556B"), base.copy(id = "android:2", marketingName = "Pixel 8"), base.copy(id = "android:3"))

        assertEquals(listOf(inventory[0]), DeviceRequest(model = "a556").matching(inventory))
        assertEquals(listOf(inventory[1]), DeviceRequest(model = "pixel").matching(inventory))
        assertEquals(inventory, DeviceRequest(DevicePlatform.ANDROID).matching(inventory))
        assertEquals(emptyList(), DeviceRequest(DevicePlatform.IOS).matching(inventory))
    }

    @Test
    fun `the state finds each worktree's device, place in line and loss by platform`() {
        val lease = PhysicalDeviceLease(base.id, DevicePlatform.ANDROID, "/repo/.git/worktrees/TASK-1/", "/w/TASK-1", 0, 0)
        val held = HeldDevice(base, lease, null, Instant.EPOCH, null)
        val ticket = WaitTicket("t", "/repo/.git/worktrees/TASK-2", "/w/TASK-2", ProcessIdentity(1, 1), 5)
        val loss = DeviceLoss("/repo/.git/worktrees/TASK-1", "/home/dev/projects/worktrees/TASK", DevicePlatform.IOS, "ios:1", "iPhone", 1, DeviceLossReason.TAKEN, "repo · TASK-2")
        val state = PhysicalDevicesState(
            listOf(DeviceOverview(base, null, held), DeviceOverview(base.copy(id = "android:2"), null, null)),
            emptyList(),
            mapOf(DevicePlatform.ANDROID to listOf(ticket)),
            listOf(loss),
            Duration.ofMinutes(20),
        )

        assertEquals(held, state.heldBy("/repo/.git/worktrees/TASK-1", DevicePlatform.ANDROID))
        assertNull(state.heldBy("/repo/.git/worktrees/TASK-1", DevicePlatform.IOS))
        assertNull(state.heldBy("/repo/.git/worktrees/TASK-2", DevicePlatform.ANDROID))
        assertEquals(1 to ticket, state.waitingOf("/repo/.git/worktrees/TASK-2", DevicePlatform.ANDROID))
        assertNull(state.waitingOf("/repo/.git/worktrees/TASK-2", DevicePlatform.IOS))
        assertEquals(loss, state.lossOf("/repo/.git/worktrees/TASK-1", DevicePlatform.IOS))
        assertNull(state.lossOf("/repo/.git/worktrees/TASK-1", DevicePlatform.ANDROID))
        assertNull(state.lossOf("/repo/.git/worktrees/TASK-2", DevicePlatform.IOS))
        assertEquals("repo · TASK-1", held.holderName)
    }

    @Test
    fun `an unavailable device with no reason or queue says only that`() {
        val error = WorktreeError.PhysicalDeviceUnavailable(DeviceRequest(DevicePlatform.IOS), emptyList(), 0)

        assertEquals("Couldn't acquire an iOS device", error.summary)
        assertTrue(error.message.endsWith("--wait"))
    }

    @Test
    fun `platforms and wait queues are paired`() {
        assertEquals(WaitQueueName.ANDROID_DEVICE, DevicePlatform.ANDROID.queueName)
        assertEquals(WaitQueueName.IOS_DEVICE, DevicePlatform.IOS.queueName)
        assertEquals(DevicePlatform.IOS, DevicePlatform.fromKey("ios"))
        assertNull(DevicePlatform.fromKey("tv"))
    }
}
