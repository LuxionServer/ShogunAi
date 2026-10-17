package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.model.SimulatorDevice
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimctlAppleSimulatorsTest {

    private val runtimesJson = """
        {
          "runtimes" : [
            {
              "isAvailable" : true,
              "version" : "18.5",
              "identifier" : "com.apple.CoreSimulator.SimRuntime.iOS-18-5",
              "platform" : "iOS",
              "name" : "iOS 18.5",
              "supportedDeviceTypes" : [
                { "name" : "iPhone 16", "productFamily" : "iPhone", "identifier" : "com.apple.CoreSimulator.SimDeviceType.iPhone-16" },
                { "name" : "iPad Pro 11-inch (M4)", "productFamily" : "iPad", "identifier" : "com.apple.CoreSimulator.SimDeviceType.iPad-Pro-11-inch-M4-8GB" }
              ]
            },
            {
              "isAvailable" : false,
              "version" : "17.0",
              "identifier" : "com.apple.CoreSimulator.SimRuntime.iOS-17-0",
              "platform" : "iOS",
              "name" : "iOS 17.0",
              "supportedDeviceTypes" : []
            }
          ]
        }
    """.trimIndent()

    private val devicesJson = """
        {
          "devices" : {
            "com.apple.CoreSimulator.SimRuntime.iOS-27-0" : [
              { "udid" : "C132ED0E-5184-451D-B128-F2BFFC0BD1A7", "isAvailable" : true, "state" : "Shutdown", "name" : "iPhone 18 Pro", "dataPathSize" : 18337792 }
            ],
            "com.apple.CoreSimulator.SimRuntime.iOS-18-5" : [
              { "udid" : "AAAA", "isAvailable" : true, "state" : "Booted", "name" : "ShogunAi · main-repo · TASK-1" },
              { "udid" : "BBBB", "isAvailable" : true, "state" : "Shutting Down", "name" : "ShogunAi · main-repo · TASK-2" }
            ]
          }
        }
    """.trimIndent()

    @Test
    fun `reads available runtimes with their device types`() {
        val runtimes = parseRuntimes(runtimesJson)

        assertEquals(listOf("iOS 18.5"), runtimes.map { it.name })
        assertEquals(listOf("iPhone 16", "iPad Pro 11-inch (M4)"), runtimes.single().supportedDeviceTypes.map { it.name })
        assertEquals("iPad", runtimes.single().supportedDeviceTypes.last().productFamily)
    }

    @Test
    fun `reads devices and their states`() {
        assertEquals(
            listOf(
                SimulatorDevice("C132ED0E-5184-451D-B128-F2BFFC0BD1A7", "iPhone 18 Pro", SimulatorState.SHUTDOWN),
                SimulatorDevice("AAAA", "ShogunAi · main-repo · TASK-1", SimulatorState.BOOTED),
                SimulatorDevice("BBBB", "ShogunAi · main-repo · TASK-2", SimulatorState.BOOTING),
            ),
            parseDevices(devicesJson),
        )
    }

    @Test
    fun `creates, boots, shuts down and deletes through simctl`() = runTest {
        val executor = FakeShellCommandExecutor { command ->
            if (command.getOrNull(2) == "create") FakeShellCommandExecutor.success(command, "NEW-UDID\n") else FakeShellCommandExecutor.success(command)
        }
        val simulators = SimctlAppleSimulators(executor, osName = "Mac OS X")

        assertEquals(
            "NEW-UDID",
            simulators.create("ShogunAi · main-repo · TASK-1", "com.apple.CoreSimulator.SimDeviceType.iPhone-16", "com.apple.CoreSimulator.SimRuntime.iOS-18-5"),
        )
        simulators.boot("NEW-UDID")
        simulators.show("NEW-UDID")
        simulators.shutdown("NEW-UDID")
        simulators.delete("NEW-UDID")

        assertTrue(
            executor.executed(
                "xcrun", "simctl", "create", "ShogunAi · main-repo · TASK-1",
                "com.apple.CoreSimulator.SimDeviceType.iPhone-16", "com.apple.CoreSimulator.SimRuntime.iOS-18-5",
            ),
        )
        assertTrue(executor.executed("xcrun", "simctl", "boot", "NEW-UDID"))
        assertTrue(executor.executed("open", "-b", "com.apple.iphonesimulator", "--args", "-CurrentDeviceUDID", "NEW-UDID"))
        assertFalse(executor.executed("open", "-b", "com.apple.dt.Devices"))
        assertTrue(executor.executed("xcrun", "simctl", "shutdown", "NEW-UDID"))
        assertTrue(executor.executed("xcrun", "simctl", "delete", "NEW-UDID"))
    }

    @Test
    fun `shows the device in Device Hub when the Simulator app isn't installed`() = runTest {
        val executor = FakeShellCommandExecutor { command ->
            if ("com.apple.iphonesimulator" in command) {
                FakeShellCommandExecutor.failure(command, exitCode = 1, stderr = "Unable to find application")
            } else {
                FakeShellCommandExecutor.success(command)
            }
        }

        SimctlAppleSimulators(executor, osName = "Mac OS X").show("NEW-UDID")

        assertTrue(executor.executed("open", "-b", "com.apple.dt.Devices"))
    }

    @Test
    fun `fails to show the device when neither app opens`() = runTest {
        val executor = FakeShellCommandExecutor { command -> FakeShellCommandExecutor.failure(command, exitCode = 1, stderr = "Unable to find application") }

        assertFailsWith<WorktreeError.AppleToolFailed> { SimctlAppleSimulators(executor, osName = "Mac OS X").show("NEW-UDID") }
    }

    @Test
    fun `booting a booted device and shutting down a shut down one succeed`() = runTest {
        val executor = FakeShellCommandExecutor { command ->
            val state = if (command[2] == "boot") "Booted" else "Shutdown"
            FakeShellCommandExecutor.failure(command, exitCode = 149, stderr = "Unable to ${command[2]} device in current state: $state")
        }
        val simulators = SimctlAppleSimulators(executor, osName = "Mac OS X")

        simulators.boot("UDID")
        simulators.shutdown("UDID")
    }

    @Test
    fun `other failures are reported with the command`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.failure(it, exitCode = 148, stderr = "Invalid device: NOPE") }

        val error = assertFailsWith<WorktreeError.AppleToolFailed> { SimctlAppleSimulators(executor, osName = "Mac OS X").delete("NOPE") }

        assertEquals(listOf("xcrun", "simctl", "delete", "NOPE"), error.command)
    }

    @Test
    fun `only supported on macOS`() {
        assertTrue(SimctlAppleSimulators(FakeShellCommandExecutor(), osName = "Mac OS X").isSupported())
        assertFalse(SimctlAppleSimulators(FakeShellCommandExecutor(), osName = "Linux").isSupported())
        assertFalse(SimctlAppleSimulators(FakeShellCommandExecutor(), osName = "Windows 11").isSupported())
    }
}
