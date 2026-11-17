package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.model.SimulatorDevice
import app.luxion.shogunai.domain.model.SimulatorRuntimeImage
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import app.luxion.shogunai.domain.model.SimulatorDeviceType
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimctlAppleSimulatorsTest {

    private val runtimeImagesJson = """
        {
          "3CB0B166-85EF-4895-AA94-3FBA3BE8FB8A" : {
            "build" : "23F77",
            "deletable" : true,
            "identifier" : "3CB0B166-85EF-4895-AA94-3FBA3BE8FB8A",
            "kind" : "Patchable Cryptex Disk Image",
            "lastUsedAt" : "2026-11-14T12:22:46Z",
            "platformIdentifier" : "com.apple.platform.iphonesimulator",
            "runtimeIdentifier" : "com.apple.CoreSimulator.SimRuntime.iOS-26-5",
            "sizeBytes" : 8494282293,
            "state" : "Ready",
            "version" : "26.5"
          },
          "B0B0B0B0-0000-0000-0000-000000000000" : {
            "runtimeIdentifier" : "com.apple.CoreSimulator.SimRuntime.watchOS-11-0",
            "sizeBytes" : "large",
            "lastUsedAt" : "yesterday"
          },
          "C" : { "identifier" : "C" },
          "E" : { "runtimeIdentifier" : "com.apple.CoreSimulator.SimRuntime.tvOS-18-0", "deletable" : "yes" },
          "D" : "x"
        }
    """.trimIndent()

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
              { "udid" : "BBBB", "isAvailable" : false, "state" : "Shutting Down", "name" : "ShogunAi · main-repo · TASK-2" }
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
                SimulatorDevice("C132ED0E-5184-451D-B128-F2BFFC0BD1A7", "iPhone 18 Pro", SimulatorState.SHUTDOWN, IOS_27_0, dataBytes = 18337792),
                SimulatorDevice("AAAA", "ShogunAi · main-repo · TASK-1", SimulatorState.BOOTED, IOS_18_5),
                SimulatorDevice("BBBB", "ShogunAi · main-repo · TASK-2", SimulatorState.BOOTING, IOS_18_5, isAvailable = false),
            ),
            parseDevices(devicesJson),
        )
    }

    @Test
    fun `runtimes and devices are read from simctl's JSON listings`() = runTest {
        val executor = FakeShellCommandExecutor { command ->
            FakeShellCommandExecutor.success(command, if (command.last() == "runtimes") runtimesJson else devicesJson)
        }
        val simulators = SimctlAppleSimulators(executor, osName = "Mac OS X")

        assertEquals(parseRuntimes(runtimesJson), simulators.runtimes())
        assertEquals(parseDevices(devicesJson), simulators.devices())
        assertTrue(executor.executed("xcrun", "simctl", "list", "-j", "devices"))
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
        assertEquals(CommandTimeouts.LOCAL_WORK, executor.timeouts[listOf("xcrun", "simctl", "boot", "NEW-UDID")])
        assertEquals(CommandTimeouts.LOCAL_TOOL, executor.timeouts[listOf("xcrun", "simctl", "shutdown", "NEW-UDID")])
        assertEquals(CommandTimeouts.LOCAL_TOOL, executor.timeouts[listOf("xcrun", "simctl", "delete", "NEW-UDID")])
        assertEquals(CommandTimeouts.LOCAL_TOOL, executor.timeouts[listOf("open", "-b", "com.apple.iphonesimulator", "--args", "-CurrentDeviceUDID", "NEW-UDID")])
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
    fun `reads runtime images with their size, last use and whether they can be deleted`() {
        assertEquals(
            listOf(
                SimulatorRuntimeImage(
                    identifier = "3CB0B166-85EF-4895-AA94-3FBA3BE8FB8A",
                    runtimeIdentifier = "com.apple.CoreSimulator.SimRuntime.iOS-26-5",
                    version = "26.5",
                    build = "23F77",
                    sizeBytes = 8494282293,
                    lastUsedAt = Instant.parse("2026-11-14T12:22:46Z"),
                    deletable = true,
                ),
                SimulatorRuntimeImage(
                    identifier = "B0B0B0B0-0000-0000-0000-000000000000",
                    runtimeIdentifier = "com.apple.CoreSimulator.SimRuntime.watchOS-11-0",
                    version = "",
                    build = "",
                    sizeBytes = null,
                    lastUsedAt = null,
                    deletable = false,
                ),
                SimulatorRuntimeImage("E", "com.apple.CoreSimulator.SimRuntime.tvOS-18-0", "", "", null, null, false),
            ),
            parseRuntimeImages(runtimeImagesJson),
        )
        assertEquals(emptyList(), parseRuntimeImages("[]"))
    }

    @Test
    fun `lists, deletes runtimes and deletes unavailable devices through simctl`() = runTest {
        val executor = FakeShellCommandExecutor { command -> FakeShellCommandExecutor.success(command, runtimeImagesJson) }
        val simulators = SimctlAppleSimulators(executor, osName = "Mac OS X")

        assertEquals(parseRuntimeImages(runtimeImagesJson), simulators.runtimeImages())
        simulators.deleteRuntime("3CB0B166-85EF-4895-AA94-3FBA3BE8FB8A")
        simulators.deleteUnavailable()

        assertEquals(CommandTimeouts.LISTING, executor.timeouts[listOf("xcrun", "simctl", "runtime", "list", "-j")])
        assertEquals(CommandTimeouts.LOCAL_WORK, executor.timeouts[listOf("xcrun", "simctl", "runtime", "delete", "3CB0B166-85EF-4895-AA94-3FBA3BE8FB8A")])
        assertEquals(CommandTimeouts.LOCAL_WORK, executor.timeouts[listOf("xcrun", "simctl", "delete", "unavailable")])
        assertTrue(executor.executedCommands.none { "sudo" in it })
    }

    @Test
    fun `a failed runtime deletion reports the tool's message`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.failure(it, exitCode = 1, stderr = "Permission denied") }

        val error = assertFailsWith<WorktreeError.AppleToolFailed> { SimctlAppleSimulators(executor, osName = "Mac OS X").deleteRuntime("ID") }

        assertEquals("Permission denied", error.output)
    }

    @Test
    fun `only supported on macOS`() {
        assertTrue(SimctlAppleSimulators(FakeShellCommandExecutor(), osName = "Mac OS X").isSupported())
        assertFalse(SimctlAppleSimulators(FakeShellCommandExecutor(), osName = "Linux").isSupported())
        assertFalse(SimctlAppleSimulators(FakeShellCommandExecutor(), osName = "Windows 11").isSupported())
    }

    @Test
    fun `malformed runtimes and device types are skipped`() {
        assertEquals(emptyList(), parseRuntimes("[]"))
        assertEquals(emptyList(), parseRuntimes("""{"runtimes": {}}"""))
        val runtimes = parseRuntimes(
            """{"runtimes": [
                "x",
                {"isAvailable": false, "identifier": "a", "name": "A"},
                {"isAvailable": "yes", "name": "B"},
                {"identifier": "c"},
                {"identifier": "d", "name": "D", "supportedDeviceTypes": "x"},
                {"identifier": "e", "name": "E", "version": 18, "supportedDeviceTypes": ["x", {"name": "n"}, {"identifier": "i"}, {"identifier": "t", "name": "T"}]}
            ]}""",
        )

        assertEquals(listOf("D", "E"), runtimes.map { it.name })
        assertEquals(listOf("" to ""), runtimes.map { it.version to it.platform }.drop(1))
        assertEquals(listOf(SimulatorDeviceType("t", "T", "")), runtimes.last().supportedDeviceTypes)
    }

    @Test
    fun `malformed device listings are skipped`() {
        assertEquals(emptyList(), parseDevices("[]"))
        assertEquals(emptyList(), parseDevices("""{"devices": []}"""))
        assertEquals(
            listOf(SimulatorDevice("u", "", SimulatorState.SHUTDOWN, "runtime-b")),
            parseDevices("""{"devices": {"runtime-a": "x", "runtime-b": ["x", {"name": "n"}, {"udid": "u", "state": "Shutdown"}]}}"""),
        )
    }

    private companion object {
        const val IOS_27_0 = "com.apple.CoreSimulator.SimRuntime.iOS-27-0"
        const val IOS_18_5 = "com.apple.CoreSimulator.SimRuntime.iOS-18-5"
    }
}
