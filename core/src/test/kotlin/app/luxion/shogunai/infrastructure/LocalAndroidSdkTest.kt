package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.SdkLocation
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.assertFalse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LocalAndroidSdkTest {

    private val repository = "/home/dev/projects/main-repo"
    private val logs = Files.createTempDirectory("emulator-logs")

    @AfterTest
    fun cleanUp() {
        logs.toFile().deleteRecursively()
    }

    private fun sdk(
        existing: List<String> = emptyList(),
        environment: Map<String, String> = emptyMap(),
        properties: Map<String, Map<String, String>> = emptyMap(),
        directories: Set<String> = emptySet(),
        osName: String = "Mac OS X",
        executor: FakeShellCommandExecutor = FakeShellCommandExecutor(),
        launches: MutableList<Pair<List<String>, File>> = mutableListOf(),
    ) = LocalAndroidSdk(
        executor = executor,
        fileManager = FakeFileManager(existing = existing, properties = properties, directories = directories),
        environmentVariable = { environment[it] },
        userHome = "/home/dev",
        osName = osName,
        logDirectory = logs,
        launch = { command, log ->
            launches += command to log
            FakeAndroidSdk.FakeEmulatorProcess()
        },
    )

    @Test
    fun `looks for the SDK in the variables, local properties and the default location`() {
        val fromHome = sdk(
            existing = listOf("/opt/home-sdk/emulator/emulator", "/opt/root-sdk/emulator/emulator"),
            environment = mapOf("ANDROID_HOME" to "/opt/home-sdk", "ANDROID_SDK_ROOT" to "/opt/root-sdk"),
        )
        assertEquals("/opt/home-sdk/platform-tools/adb", fromHome.locate(repository).adb)

        val fromProperties = sdk(
            existing = listOf("/opt/android-sdk/emulator/emulator"),
            environment = mapOf("ANDROID_HOME" to "/missing"),
            properties = mapOf("$repository/local.properties" to mapOf("sdk.dir" to "/opt/android-sdk")),
        )
        assertEquals("/opt/android-sdk/emulator/emulator", fromProperties.locate(repository).emulator)

        val fromDefault = sdk(existing = listOf("/home/dev/Library/Android/sdk/emulator/emulator"))
        assertEquals("/home/dev/Library/Android/sdk", fromDefault.locate(repository).root)

        val linux = sdk(existing = listOf("/home/dev/Android/Sdk/emulator/emulator"), osName = "Linux")
        assertEquals("/home/dev/Android/Sdk", linux.locate(repository).root)
    }

    @Test
    fun `looks for the SDK in a nested build's local properties`() {
        val nested = sdk(
            existing = listOf("/opt/android-sdk/emulator/emulator", "$repository/android/settings.gradle.kts"),
            properties = mapOf("$repository/android/local.properties" to mapOf("sdk.dir" to "/opt/android-sdk")),
            directories = setOf("$repository/android"),
        )

        assertEquals("/opt/android-sdk/emulator/emulator", nested.locate(repository).emulator)
    }

    @Test
    fun `names a nested build's local properties among the places checked`() {
        val error = assertFailsWith<WorktreeError.AndroidSdkNotFound> {
            sdk(
                existing = listOf("$repository/android/settings.gradle.kts"),
                properties = mapOf("$repository/android/local.properties" to mapOf("sdk.dir" to "/missing")),
                directories = setOf("$repository/android"),
            ).locate(repository)
        }

        assertEquals(
            listOf("android/local.properties sdk.dir (/missing)", "default location (/home/dev/Library/Android/sdk)"),
            error.checked,
        )
    }

    @Test
    fun `adds exe on Windows`() {
        val windows = sdk(
            existing = listOf("C:\\Users\\dev\\AppData\\Local\\Android\\Sdk\\emulator\\emulator.exe"),
            environment = mapOf("LOCALAPPDATA" to "C:\\Users\\dev\\AppData\\Local"),
            osName = "Windows 11",
        )

        assertEquals("C:\\Users\\dev\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe", windows.locate(repository).adb)
    }

    @Test
    fun `names every place checked when the SDK is missing`() {
        val error = assertFailsWith<WorktreeError.AndroidSdkNotFound> {
            sdk(environment = mapOf("ANDROID_HOME" to "/missing")).locate(repository)
        }

        assertEquals(listOf("ANDROID_HOME (/missing)", "default location (/home/dev/Library/Android/sdk)"), error.checked)
    }

    private val location = SdkLocation("/sdk", "/sdk/emulator/emulator", "/sdk/platform-tools/adb")

    @Test
    fun `reports emulators as running, booting or offline`() = runTest {
        val executor = FakeShellCommandExecutor { command ->
            when {
                command == listOf("/sdk/platform-tools/adb", "devices") -> FakeShellCommandExecutor.success(
                    command,
                    "* daemon started successfully\nList of devices attached\nemulator-5554\tdevice\nemulator-5556\tdevice\nemulator-5558\toffline\nR58M123\tdevice\n\n",
                )
                command.contains("emulator-5554") -> FakeShellCommandExecutor.success(command, "1\n")
                else -> FakeShellCommandExecutor.success(command, "\n")
            }
        }

        val states = sdk(executor = executor).deviceStates(location)

        assertEquals(
            mapOf(
                "emulator-5554" to EmulatorState.RUNNING,
                "emulator-5556" to EmulatorState.BOOTING,
                "emulator-5558" to EmulatorState.BOOTING,
            ),
            states,
        )
    }

    @Test
    fun `asks adb for devices with a limit`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, "List of devices attached\n") }

        sdk(executor = executor).deviceStates(location)

        assertEquals(CommandTimeouts.LISTING, executor.timeouts[listOf("/sdk/platform-tools/adb", "devices")])
    }

    @Test
    fun `lists AVDs skipping informational lines`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, "INFO    | Storing crashdata\nMedium_Phone_API_35\nPixel_8_API_35\n") }

        assertEquals(listOf("Medium_Phone_API_35", "Pixel_8_API_35"), sdk(executor = executor).listAvds(location))
    }

    @Test
    fun `starts detached with the documented flags and a log per serial`() {
        val launches = mutableListOf<Pair<List<String>, File>>()

        sdk(launches = launches).startEmulator(location, "Medium_Phone_API_35", 5556, noWindow = true)

        val (command, log) = launches.single()
        assertEquals(
            listOf("/sdk/emulator/emulator", "-avd", "Medium_Phone_API_35", "-port", "5556", "-read-only", "-no-boot-anim", "-no-window"),
            command,
        )
        assertEquals(logs.resolve("emulator-5556.log").toFile(), log)
    }

    @Test
    fun `stops through the emulator console and reports tool failures`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.failure(it, exitCode = 1, stderr = "device not found") }

        val error = assertFailsWith<WorktreeError.AndroidToolFailed> { sdk(executor = executor).stopEmulator(location, "emulator-5556") }

        assertEquals(listOf("/sdk/platform-tools/adb", "-s", "emulator-5556", "emu", "kill"), error.command)
        assertTrue(error.output.contains("device not found"))
    }

    private fun listing(vararg serials: String) = serials.joinToString("\n", prefix = "List of devices attached\n") { "$it\tdevice" }

    private fun bootExecutor(devices: () -> String, bootCompleted: () -> String, commands: MutableList<List<String>>) =
        FakeShellCommandExecutor { command ->
            commands += command
            when {
                command.contains("devices") -> FakeShellCommandExecutor.success(command, devices())
                command.contains("getprop") -> FakeShellCommandExecutor.success(command, bootCompleted())
                else -> FakeShellCommandExecutor.success(command)
            }
        }

    private fun getprops(commands: List<List<String>>) = commands.count { it.contains("getprop") }

    @Test
    fun `a booted emulator isn't asked for its boot property again while it stays online`() = runTest {
        val commands = mutableListOf<List<String>>()
        val android = sdk(executor = bootExecutor({ listing("emulator-5554") }, { "1\n" }, commands))

        android.deviceStates(location)
        val second = android.deviceStates(location)

        assertEquals(mapOf("emulator-5554" to EmulatorState.RUNNING), second)
        assertEquals(1, getprops(commands))
    }

    @Test
    fun `an emulator that disappears and comes back is asked for its boot property again`() = runTest {
        val commands = mutableListOf<List<String>>()
        var devices = listing("emulator-5554")
        var bootCompleted = "1\n"
        val android = sdk(executor = bootExecutor({ devices }, { bootCompleted }, commands))

        android.deviceStates(location)
        devices = listing()
        android.deviceStates(location)
        devices = listing("emulator-5554")
        bootCompleted = "\n"
        val states = android.deviceStates(location)

        assertEquals(mapOf("emulator-5554" to EmulatorState.BOOTING), states)
        assertEquals(2, getprops(commands))
    }

    @Test
    fun `stopping an emulator makes the next listing ask for its boot property again`() = runTest {
        val commands = mutableListOf<List<String>>()
        val android = sdk(executor = bootExecutor({ listing("emulator-5554") }, { "1\n" }, commands))

        android.deviceStates(location)
        android.stopEmulator(location, "emulator-5554")
        android.deviceStates(location)

        assertEquals(2, getprops(commands))
    }

    @Test
    fun `a detached emulator process writes its output to its log`() {
        val log = logs.resolve("echo.log").toFile()

        val process = launchDetached(echoCommand("booted"), log)
        while (process.isAlive) Thread.sleep(20)

        assertEquals("booted", process.lastLogLines(1).trim())
    }

    @Test
    fun `blank variables and SDK folders are skipped and Windows falls back to the user's local app data`() {
        val windows = sdk(environment = mapOf("ANDROID_HOME" to " ", "ANDROID_SDK_ROOT" to " "), properties = mapOf("$repository/local.properties" to mapOf("sdk.dir" to " ")), osName = "Windows 11")

        val error = assertFailsWith<WorktreeError.AndroidSdkNotFound> { windows.locate(repository) }

        assertEquals(listOf("default location (/home/dev\\AppData\\Local\\Android\\Sdk)"), error.checked)
    }

    @Test
    fun `devices lines without a state are skipped and the null device follows the platform`() {
        assertEquals(emptyMap(), parseAdbDevices("emulator-5554\n"))
        assertEquals(File("NUL"), nullDevice('\\'))
        assertEquals(File("/dev/null"), nullDevice('/'))
        assertEquals(listOf("/e", "-avd", "Pixel", "-port", "5554", "-read-only", "-no-boot-anim", "-no-window"), emulatorCommand(SdkLocation("/s", "/e", "/a"), "Pixel", 5554, noWindow = true))
    }

    @Test
    fun `a detached process whose log is gone has no last lines`() {
        val log = logs.resolve("gone.log").toFile()
        val process = launchDetached(listOf("true"), log)
        log.delete()

        assertEquals("", process.lastLogLines(5))
    }

    @Test
    fun `an emulator whose boot property can't be read is still booting and a windowed one isn't headless`() = runTest {
        val android = sdk(
            executor = FakeShellCommandExecutor { command ->
                if (command.contains("devices")) FakeShellCommandExecutor.success(command, listing("emulator-5554")) else FakeShellCommandExecutor.failure(command)
            },
        )

        assertEquals(mapOf("emulator-5554" to EmulatorState.BOOTING), android.deviceStates(location))
        assertFalse("-no-window" in emulatorCommand(location, "Pixel", 5554, noWindow = false))
    }
}
