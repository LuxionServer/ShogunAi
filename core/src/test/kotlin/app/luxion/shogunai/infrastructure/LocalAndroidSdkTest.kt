package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.SdkLocation
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
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
        osName: String = "Mac OS X",
        executor: FakeShellCommandExecutor = FakeShellCommandExecutor(),
        launches: MutableList<Pair<List<String>, File>> = mutableListOf(),
    ) = LocalAndroidSdk(
        executor = executor,
        fileManager = FakeFileManager(existing = existing, properties = properties),
        environmentVariable = { environment[it] },
        userHome = "/home/dev",
        osName = osName,
        logDirectory = logs,
        launch = { command, log -> launches += command to log },
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
}
