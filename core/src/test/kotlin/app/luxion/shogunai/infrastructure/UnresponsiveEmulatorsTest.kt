package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.HangingShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.SdkLocation
import app.luxion.shogunai.domain.model.WorktreeError
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

class UnresponsiveEmulatorsTest {

    private val logs = Files.createTempDirectory("emulator-logs")
    private val location = SdkLocation("/sdk", "/sdk/emulator/emulator", "/sdk/platform-tools/adb")

    @AfterTest
    fun cleanUp() {
        logs.toFile().deleteRecursively()
    }

    private fun sdk(executor: ShellCommandExecutor) = LocalAndroidSdk(
        executor = executor,
        fileManager = FakeFileManager(),
        environmentVariable = { null },
        userHome = "/home/dev",
        osName = "Mac OS X",
        logDirectory = logs,
        launch = { _, _ -> FakeAndroidSdk.FakeEmulatorProcess() },
    )

    private fun listing(vararg serials: String) = serials.joinToString("\n", prefix = "List of devices attached\n") { "$it\tdevice" }

    private fun executor(devices: String, hung: Set<String>) = HangingShellCommandExecutor(
        hangs = { command -> command.contains("getprop") && hung.any { it in command } },
        responder = { command ->
            if (command.contains("devices")) FakeShellCommandExecutor.success(command, devices) else FakeShellCommandExecutor.success(command, "1\n")
        },
    )

    @Test
    fun `an emulator whose boot property doesn't answer is unresponsive and the others are still read`() = runTest {
        val executor = executor(listing("emulator-5554", "emulator-5556"), hung = setOf("emulator-5554"))

        val states = sdk(executor).deviceStates(location)

        assertEquals(mapOf("emulator-5554" to EmulatorState.UNRESPONSIVE, "emulator-5556" to EmulatorState.RUNNING), states)
        assertEquals(CommandTimeouts.DEVICE_PROBE, executor.timeouts.getValue(listOf("/sdk/platform-tools/adb", "-s", "emulator-5554", "shell", "getprop", "sys.boot_completed")))
    }

    @Test
    fun `hung emulators are probed at the same time`() = runTest {
        val serials = setOf("emulator-5554", "emulator-5556", "emulator-5558")
        val executor = executor(listing(*serials.toTypedArray()), hung = serials)

        val states = sdk(executor).deviceStates(location)

        assertEquals(serials.associateWith { EmulatorState.UNRESPONSIVE }, states)
        assertEquals(CommandTimeouts.DEVICE_PROBE.inWholeMilliseconds, currentTime)
    }

    @Test
    fun `an unresponsive emulator that answers later is read from its answer`() = runTest {
        val hung = mutableSetOf("emulator-5554")
        val android = sdk(executor(listing("emulator-5554"), hung))

        android.deviceStates(location)
        hung.clear()

        assertEquals(mapOf("emulator-5554" to EmulatorState.RUNNING), android.deviceStates(location))
    }

    @Test
    fun `only the requested emulators are probed`() = runTest {
        val executor = executor(listing("emulator-5554", "emulator-5560"), hung = setOf("emulator-5554"))

        val states = sdk(executor).deviceStates(location, setOf("emulator-5560"))

        assertEquals(mapOf("emulator-5560" to EmulatorState.RUNNING), states)
        assertFalse(executor.executedCommands.any { "emulator-5554" in it })
        assertEquals(0L, currentTime)
    }

    @Test
    fun `a probe that can't start still fails the read`() = runTest {
        val executor = HangingShellCommandExecutor(hangs = { false }) { command ->
            if (command.contains("devices")) FakeShellCommandExecutor.success(command, listing("emulator-5554"))
            else throw CommandExecutionException(command, IOException("no such file"))
        }

        assertFailsWith<CommandExecutionException> { sdk(executor).deviceStates(location) }
    }

    @Test
    fun `an emulator that doesn't answer the request to exit names its console port`() = runTest {
        val executor = HangingShellCommandExecutor(hangs = { it.contains("emu") })

        val error = assertFailsWith<WorktreeError.EmulatorDidNotExit> { sdk(executor).stopEmulator(location, "emulator-5556") }

        assertEquals("emulator-5556", error.serial)
        assertEquals(5556, error.consolePort)
        assertTrue("5556" in error.message)
        assertFalse("adb" in error.message)
    }
}
