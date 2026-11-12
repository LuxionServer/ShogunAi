package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.DeviceLeases
import app.luxion.shogunai.domain.model.DeviceLoss
import app.luxion.shogunai.domain.model.DeviceLossReason
import app.luxion.shogunai.domain.model.DevicePlatform
import app.luxion.shogunai.domain.model.PhysicalDeviceLease
import app.luxion.shogunai.domain.model.RecordedDevice
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NioPhysicalDeviceStoresTest {

    private val folder: Path = Files.createTempDirectory("device-stores").resolve("machine")
    private val inventory = NioPhysicalDeviceInventoryStore(folder)
    private val leases = NioPhysicalDeviceLeaseStore(folder)

    private val a55 = RecordedDevice("android:R5CT10ABCDE", DevicePlatform.ANDROID, "a55", "Galaxy A55 5G", "samsung", "SM-A556B", "Galaxy A55 5G", "15", lent = false, lastSeen = 1_000)
    private val iphone = RecordedDevice("ios:00008150-000A1B2C3D4E5F60", DevicePlatform.IOS, lastSeen = 2_000)

    @Test
    fun `the inventory round-trips through devices json`() {
        inventory.write(listOf(a55, iphone))

        assertEquals(listOf(a55, iphone), inventory.read())
        assertEquals(listOf(a55, iphone), NioPhysicalDeviceInventoryStore(folder).read())
        assertEquals(true, folder.resolve("devices.json").exists())
    }

    @Test
    fun `a missing, corrupt or invalid inventory reads as empty and unknown platforms are skipped`() {
        assertEquals(emptyList(), inventory.read())
        Files.createDirectories(folder)
        Files.writeString(folder.resolve("devices.json"), "{ nope")
        assertEquals(emptyList(), inventory.read())
        Files.writeString(folder.resolve("devices.json"), """{"devices":[{"id":"x","platform":"android"}]}""")
        assertEquals(emptyList(), inventory.read())
        Files.writeString(folder.resolve("devices.json"), """{"devices":[{"id":"x","platform":"windows","lastSeen":1}]}""")
        assertEquals(emptyList(), inventory.read())
    }

    @Test
    fun `leases and losses round-trip and an empty record deletes the file`() {
        val lease = PhysicalDeviceLease(a55.id, DevicePlatform.ANDROID, "/repo/.git/worktrees/TASK-1", "/worktrees/TASK-1", 10, 20)
        val loss = DeviceLoss("/repo/.git/worktrees/TASK-2", "/home/dev/projects/worktrees/TASK", DevicePlatform.IOS, iphone.id, "iPhone", 30, DeviceLossReason.WITHDRAWN, null)
        val taken = loss.copy(reason = DeviceLossReason.TAKEN, forWorktree = "repo · TASK-1")

        leases.write(DeviceLeases(listOf(lease), listOf(loss, taken)))

        assertEquals(DeviceLeases(listOf(lease), listOf(loss, taken)), leases.read())
        leases.write(DeviceLeases(losses = listOf(loss)))
        assertEquals(DeviceLeases(losses = listOf(loss)), leases.read())
        leases.write(DeviceLeases())
        assertFalse(folder.resolve("device-leases.json").exists())
        assertEquals(DeviceLeases(), leases.read())
    }

    @Test
    fun `corrupt leases read as empty and unknown entries are skipped`() {
        Files.createDirectories(folder)
        Files.writeString(folder.resolve("device-leases.json"), "[")
        assertEquals(DeviceLeases(), leases.read())
        Files.writeString(
            folder.resolve("device-leases.json"),
            """{"leases":[{"deviceId":"a","platform":"tv","adminDirectory":"/a","worktreePath":"/w","acquiredAt":1,"renewedAt":1}],
               "losses":[{"adminDirectory":"/a","worktreePath":"/w","platform":"tv","deviceId":"a","deviceName":"A","at":1,"reason":"taken","forWorktree":null},
                         {"adminDirectory":"/a","worktreePath":"/w","platform":"ios","deviceId":"a","deviceName":"A","at":1,"reason":"lost","forWorktree":null}]}""",
        )
        assertEquals(DeviceLeases(), leases.read())
        Files.writeString(folder.resolve("device-leases.json"), """{"leases":7,"losses":[]}""")
        assertEquals(DeviceLeases(), leases.read())
    }
}
