package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.model.DeviceConnection
import app.luxion.shogunai.domain.model.DevicePlatform
import app.luxion.shogunai.domain.model.DeviceUsability
import app.luxion.shogunai.domain.model.PhysicalDevice
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DevicectlPhysicalDevicesTest {

    private fun fixture(name: String): String = checkNotNull(javaClass.getResource("/devices/$name")) { name }.readText()

    private val folder: Path = Files.createTempDirectory("devicectl")
    private val output: Path = folder.resolve("devices.json")

    private fun devicectl(fixture: String?, exitCode: Int = 0, osName: String = "Mac OS X") = run {
        val executor = FakeShellCommandExecutor { command ->
            fixture?.let { Files.writeString(Path.of(command.last()), fixture(it)) }
            if (exitCode == 0) FakeShellCommandExecutor.success(command) else FakeShellCommandExecutor.failure(command)
        }
        executor to DevicectlPhysicalDevices(executor, osName) { output.also { Files.writeString(it, "") } }
    }

    private val iphone = PhysicalDevice(
        platform = DevicePlatform.IOS,
        identity = "00008150-000A1B2C3D4E5F60",
        transport = "00008150-000A1B2C3D4E5F60",
        connection = DeviceConnection.NETWORK,
        name = "Test iPhone",
        manufacturer = "Apple",
        model = "iPhone18,3",
        marketingName = "iPhone 17",
        osVersion = "26.6",
    )

    @Test
    fun `a paired iPhone on the network is listed, the simulator skipped and the file deleted`() = runTest {
        val (executor, devices) = devicectl("devicectl-network.json")

        assertEquals(listOf(iphone), devices.list().devices)
        assertEquals(listOf("xcrun", "devicectl", "list", "devices", "--json-output", output.toString()), executor.executedCommands.single())
        assertFalse(output.exists())
    }

    @Test
    fun `a paired iPhone by USB is listed as USB`() = runTest {
        assertEquals(listOf(iphone.copy(connection = DeviceConnection.USB)), devicectl("devicectl-usb.json").second.list().devices)
    }

    @Test
    fun `the properties dictionary alone is enough`() = runTest {
        assertEquals(listOf(iphone.copy(connection = DeviceConnection.USB)), devicectl("devicectl-properties-only.json").second.list().devices)
    }

    @Test
    fun `an unplugged device isn't listed`() = runTest {
        assertEquals(emptyList(), devicectl("devicectl-unplugged.json").second.list().devices)
    }

    @Test
    fun `developer mode off and unpaired devices can't be used`() = runTest {
        val devices = devicectl("devicectl-unusable.json").second.list().devices

        assertEquals(listOf(DeviceUsability.DEVELOPER_MODE_OFF, DeviceUsability.UNPAIRED), devices.map { it.usability })
        assertEquals("Test iPad", devices.first().name)
    }

    @Test
    fun `Linux runs nothing`() = runTest {
        val (executor, devices) = devicectl("devicectl-usb.json", osName = "Linux")

        assertEquals(emptyList(), devices.list().devices)
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `a failing devicectl, an unreadable file or no temp file lists nothing`() = runTest {
        assertEquals(emptyList(), devicectl("devicectl-usb.json", exitCode = 1).second.list().devices)
        assertFalse(output.exists())
        val unreadable = DevicectlPhysicalDevices(FakeShellCommandExecutor { command -> Files.delete(Path.of(command.last())); FakeShellCommandExecutor.success(command) }, "Mac OS X") {
            output.also { Files.writeString(it, "") }
        }
        assertEquals(emptyList(), unreadable.list().devices)
        assertEquals(emptyList(), DevicectlPhysicalDevices(FakeShellCommandExecutor(), "Mac OS X") { throw IOException("no temp") }.list().devices)
    }

    @Test
    fun `malformed documents and entries are skipped`() {
        assertEquals(emptyList(), parseDevicectlDevices("not json"))
        assertEquals(emptyList(), parseDevicectlDevices("[]"))
        assertEquals(emptyList(), parseDevicectlDevices("""{"result":{"devices":{}}}"""))
        assertEquals(emptyList(), parseDevicectlDevices("""{"result":{}}"""))
        assertEquals(
            emptyList(),
            parseDevicectlDevices(
                """{"result":{"devices":[1,
                {"hardwareProperties":{"reality":"physical"}},
                {"hardwareProperties":{"reality":"physical","udid":"U1"}},
                {"hardwareProperties":{"reality":"physical","udid":"U1"},"connectionProperties":{"transportType":"wired"}},
                {"hardwareProperties":{"reality":7}}]}}""",
            ),
        )
    }

    @Test
    fun `a device without developer mode information can't be used`() {
        val device = parseDevicectlDevices(
            """{"result":{"devices":[{"hardwareProperties":{"reality":"physical","udid":"U1"},
            "connectionProperties":{"transportType":"wired","tunnelState":"connected","pairingState":"paired"},
            "properties":{"state":{"developerModeStatus":"weird"}}}]}}""",
        ).single()

        assertEquals(DeviceUsability.DEVELOPER_MODE_OFF, device.usability)
        assertEquals(null, device.name)
    }

    @Test
    fun `a device without a tunnel state or with an empty developer mode isn't usable`() {
        val missingTunnel = """{"result":{"devices":[{"hardwareProperties":{"reality":"physical","udid":"U1"},"connectionProperties":{"transportType":"wired"}}]}}"""
        val emptyMode = """{"result":{"devices":[{"properties":{"hardware":{"reality":"physical","udid":"U2"},
            "connection":{"transportType":"wired","state":"connected","pairingState":"paired"},"state":{"developerModeStatus":{}}}}]}}"""

        val unavailable = """{"result":{"devices":[{"hardwareProperties":{"reality":"physical","udid":"U1"},"connectionProperties":{"transportType":"wired","tunnelState":"unavailable"}}]}}"""

        assertEquals(emptyList(), parseDevicectlDevices(missingTunnel))
        assertEquals(emptyList(), parseDevicectlDevices(unavailable))
        val noMode = """{"result":{"devices":[{"hardwareProperties":{"reality":"physical","udid":"U3"},"connectionProperties":{"transportType":"wired","tunnelState":"connected","pairingState":"paired"}}]}}"""

        assertEquals(DeviceUsability.DEVELOPER_MODE_OFF, parseDevicectlDevices(emptyMode).single().usability)
        assertEquals(DeviceUsability.DEVELOPER_MODE_OFF, parseDevicectlDevices(noMode).single().usability)
    }
}
