package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.model.DeviceConnection
import app.luxion.shogunai.domain.model.DevicePlatform
import app.luxion.shogunai.domain.model.DeviceUsability
import app.luxion.shogunai.domain.model.PendingDevice
import app.luxion.shogunai.domain.model.PhysicalDevice
import app.luxion.shogunai.domain.model.SdkLocation
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class AdbPhysicalDevicesTest {

    private fun fixture(name: String): String = checkNotNull(javaClass.getResource("/devices/$name")) { name }.readText()

    private val location = SdkLocation("/sdk", "/sdk/emulator/emulator", "/sdk/platform-tools/adb")

    private fun localSdk(devices: String) = LocalAndroidSdk(
        executor = FakeShellCommandExecutor { command ->
            when {
                command == listOf(location.adb, "devices", "-l") -> FakeShellCommandExecutor.success(command, fixture(devices))
                command.last() == "getprop" -> when (command[2]) {
                    "R5CT10ABCDE", "adb-R5CT10ABCDE-xYz._adb-tls-connect._tcp" -> FakeShellCommandExecutor.success(command, fixture("getprop-a55.txt"))
                    "192.168.1.30:5555" -> FakeShellCommandExecutor.success(command, fixture("getprop-emulator.txt"))
                    "XMPOCO0123456789" -> FakeShellCommandExecutor.success(command, fixture("getprop-poco.txt"))
                    else -> FakeShellCommandExecutor.failure(command)
                }
                else -> FakeShellCommandExecutor.success(command)
            }
        },
        fileManager = FakeFileManager(existing = listOf(location.emulator)),
        environmentVariable = { if (it == "ANDROID_HOME") location.root else null },
        userHome = "/home/dev",
        logDirectory = Files.createTempDirectory("adb-physical"),
    )

    private val a55 = PhysicalDevice(
        platform = DevicePlatform.ANDROID,
        identity = "R5CT10ABCDE",
        transport = "R5CT10ABCDE",
        connection = DeviceConnection.USB,
        name = "Galaxy A55 5G",
        manufacturer = "samsung",
        model = "SM-A556B",
        marketingName = "Galaxy A55 5G",
        osVersion = "15",
    )

    @Test
    fun `a USB device is listed with its identity and properties and the emulator is skipped`() = runTest {
        val listing = AdbPhysicalDevices(localSdk("adb-devices-usb.txt")) { "/repo" }.list()

        assertEquals(listOf(a55), listing.devices)
        assertEquals(emptyList(), listing.pending)
        assertEquals("android:R5CT10ABCDE", listing.devices.single().id)
    }

    @Test
    fun `a device whose properties include multi-line values is read by its marketing name`() = runTest {
        val listing = AdbPhysicalDevices(localSdk("adb-devices-poco.txt")) { "/repo" }.list()

        assertEquals(
            listOf(
                PhysicalDevice(
                    platform = DevicePlatform.ANDROID,
                    identity = "XMPOCO0123456789",
                    transport = "XMPOCO0123456789",
                    connection = DeviceConnection.USB,
                    name = "POCO X6 Pro 5G",
                    manufacturer = "Xiaomi",
                    model = "2311DRK48G",
                    marketingName = "POCO X6 Pro 5G",
                    osVersion = "16",
                ),
            ),
            listing.devices,
        )
    }

    @Test
    fun `a wireless device keeps its identity, a TCP emulator is skipped and an unauthorized one is pending`() = runTest {
        val listing = AdbPhysicalDevices(localSdk("adb-devices-wifi.txt")) { "/repo" }.list()

        assertEquals(
            listOf(a55.copy(transport = "adb-R5CT10ABCDE-xYz._adb-tls-connect._tcp", connection = DeviceConnection.NETWORK)),
            listing.devices,
        )
        assertEquals(listOf(PendingDevice(DevicePlatform.ANDROID, "0A1B2C3D4E", DeviceUsability.UNAUTHORIZED)), listing.pending)
    }

    @Test
    fun `properties are read once per connection`() = runTest {
        val sdk = FakeAndroidSdk()
        sdk.physical["R5CT10ABCDE"] = "device"
        sdk.properties["R5CT10ABCDE"] = mapOf("ro.serialno" to "R5CT10ABCDE", "ro.product.model" to "Pixel 8", "ro.product.marketname" to "Pixel 8")
        val devices = AdbPhysicalDevices(sdk) { "/repo" }

        devices.list()
        devices.list()
        sdk.physical.clear()
        devices.list()
        sdk.physical["R5CT10ABCDE"] = "device"
        val listing = devices.list()

        assertEquals(listOf("R5CT10ABCDE", "R5CT10ABCDE"), sdk.propertyQueries)
        assertEquals("Pixel 8", listing.devices.single().marketingName)
    }

    @Test
    fun `a device whose properties can't be read yet is connecting and offline devices are pending`() = runTest {
        val sdk = FakeAndroidSdk()
        sdk.physical.putAll(mapOf("NEW1" to "device", "BLANK" to "device", "OFF1" to "offline", "AUTH" to "authorizing"))
        sdk.properties["BLANK"] = mapOf("ro.serialno" to " ", "ro.product.model" to "X")
        sdk.physical["NOSERIAL"] = "device"
        sdk.properties["NOSERIAL"] = mapOf("ro.product.model" to "X")

        val listing = AdbPhysicalDevices(sdk) { "/repo" }.list()

        assertEquals(emptyList(), listing.devices)
        assertEquals(
            listOf(
                PendingDevice(DevicePlatform.ANDROID, "NEW1", DeviceUsability.CONNECTING),
                PendingDevice(DevicePlatform.ANDROID, "BLANK", DeviceUsability.CONNECTING),
                PendingDevice(DevicePlatform.ANDROID, "OFF1", DeviceUsability.OFFLINE),
                PendingDevice(DevicePlatform.ANDROID, "AUTH", DeviceUsability.CONNECTING),
                PendingDevice(DevicePlatform.ANDROID, "NOSERIAL", DeviceUsability.CONNECTING),
            ),
            listing.pending,
        )
    }

    @Test
    fun `missing properties fall back to the model and blanks count as unknown`() = runTest {
        val sdk = FakeAndroidSdk()
        sdk.physical["S1"] = "device"
        sdk.properties["S1"] = mapOf("ro.serialno" to "S1", "ro.product.model" to "Phone", "ro.product.marketname" to "", "ro.product.manufacturer" to "")

        val device = AdbPhysicalDevices(sdk) { "/repo" }.list().devices.single()

        assertEquals("Phone", device.marketingName)
        assertEquals(null, device.manufacturer)
        assertEquals(null, device.osVersion)
    }

    @Test
    fun `no Android SDK lists nothing and a failing adb lists nothing`() = runTest {
        assertEquals(emptyList(), AdbPhysicalDevices(FakeAndroidSdk(missing = true)) { "/repo" }.list().devices)
        val failing = object : app.luxion.shogunai.domain.io.AndroidSdk by FakeAndroidSdk() {
            override suspend fun attachedDevices(sdk: SdkLocation): Map<String, String> = error("adb failed")
        }
        assertEquals(emptyList(), AdbPhysicalDevices(failing) { "/repo" }.list().pending)
    }

    @Test
    fun `network serials are told apart from USB ones`() {
        assertEquals(DeviceConnection.NETWORK, connectionOf("192.168.1.20:37000"))
        assertEquals(DeviceConnection.NETWORK, connectionOf("adb-R5CT10ABCDE-xYz._adb-tls-connect._tcp"))
        assertEquals(DeviceConnection.USB, connectionOf("R5CT10ABCDE"))
    }
}
