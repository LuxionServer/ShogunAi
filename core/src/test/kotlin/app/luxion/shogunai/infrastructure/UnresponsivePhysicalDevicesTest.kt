package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.HangingShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.model.DevicePlatform
import app.luxion.shogunai.domain.model.DeviceUsability
import app.luxion.shogunai.domain.model.PendingDevice
import app.luxion.shogunai.domain.model.SdkLocation
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

class UnresponsivePhysicalDevicesTest {

    private fun fixture(name: String): String = checkNotNull(javaClass.getResource("/devices/$name")) { name }.readText()

    private val location = SdkLocation("/sdk", "/sdk/emulator/emulator", "/sdk/platform-tools/adb")
    private val devices = "List of devices attached\nR5CT10ABCDE device usb:1-1\nXMPOCO0123456789 device usb:1-2\n"

    private fun executor(hung: MutableSet<String>) = HangingShellCommandExecutor(
        hangs = { command -> command.last() == "getprop" && command[2] in hung },
        responder = { command ->
            when {
                command == listOf(location.adb, "devices", "-l") -> FakeShellCommandExecutor.success(command, devices)
                command[2] == "R5CT10ABCDE" -> FakeShellCommandExecutor.success(command, fixture("getprop-a55.txt"))
                else -> FakeShellCommandExecutor.success(command, fixture("getprop-poco.txt"))
            }
        },
    )

    private fun physical(executor: HangingShellCommandExecutor) = AdbPhysicalDevices(
        LocalAndroidSdk(
            executor = executor,
            fileManager = FakeFileManager(existing = listOf(location.emulator)),
            environmentVariable = { if (it == "ANDROID_HOME") location.root else null },
            userHome = "/home/dev",
            osName = "Mac OS X",
            logDirectory = Files.createTempDirectory("adb-physical"),
        ),
    ) { "/repo" }

    @Test
    fun `a device whose properties don't answer is pending and unresponsive and the others are listed`() = runTest {
        val executor = executor(mutableSetOf("R5CT10ABCDE"))

        val listing = physical(executor).list()

        assertEquals(listOf("XMPOCO0123456789"), listing.devices.map { it.transport })
        assertEquals(listOf(PendingDevice(DevicePlatform.ANDROID, "R5CT10ABCDE", DeviceUsability.UNRESPONSIVE)), listing.pending)
        assertEquals(CommandTimeouts.DEVICE_PROBE, executor.timeouts.getValue(listOf(location.adb, "-s", "R5CT10ABCDE", "shell", "getprop")))
    }

    @Test
    fun `devices that don't answer are probed at the same time and again on the next listing`() = runTest {
        val hung = mutableSetOf("R5CT10ABCDE", "XMPOCO0123456789")
        val devices = physical(executor(hung))

        assertEquals(2, devices.list().pending.size)
        assertEquals(CommandTimeouts.DEVICE_PROBE.inWholeMilliseconds, currentTime)

        hung.clear()
        assertEquals(listOf("R5CT10ABCDE", "XMPOCO0123456789"), devices.list().devices.map { it.transport }.sorted())
    }
}
