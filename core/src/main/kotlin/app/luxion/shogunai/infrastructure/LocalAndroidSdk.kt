package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.AndroidSdk
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.SdkLocation
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.emulatorSerial
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class LocalAndroidSdk(
    private val executor: ShellCommandExecutor,
    private val fileManager: FileManager,
    private val environmentVariable: (String) -> String? = System::getenv,
    private val userHome: String = System.getProperty("user.home"),
    private val osName: String = System.getProperty("os.name").orEmpty(),
    private val logDirectory: Path = Path.of(System.getProperty("user.home"), ".shogunai", "logs"),
    private val launch: (command: List<String>, log: File) -> Unit = ::launchDetached,
) : AndroidSdk {

    private val isWindows: Boolean get() = osName.startsWith("Windows", ignoreCase = true)

    override fun locate(repositoryPath: String): SdkLocation {
        val candidates = sdkCandidates(repositoryPath)
        val root = candidates.map { it.second }.firstOrNull { fileManager.exists(executablePath(it, EMULATOR)) }
            ?: throw WorktreeError.AndroidSdkNotFound(candidates.map { (source, path) -> "$source ($path)" })
        return SdkLocation(root = root, emulator = executablePath(root, EMULATOR), adb = executablePath(root, ADB))
    }

    override suspend fun listAvds(sdk: SdkLocation): List<String> =
        run(listOf(sdk.emulator, "-list-avds")).lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("INFO") && !it.contains("|") }

    override suspend fun deviceStates(sdk: SdkLocation): Map<String, EmulatorState> =
        parseAdbDevices(run(listOf(sdk.adb, "devices"))).mapValues { (serial, status) ->
            when (status) {
                "device" -> if (bootCompleted(sdk, serial)) EmulatorState.RUNNING else EmulatorState.BOOTING
                else -> EmulatorState.BOOTING
            }
        }

    override fun startEmulator(sdk: SdkLocation, avd: String, consolePort: Int, noWindow: Boolean) {
        Files.createDirectories(logDirectory)
        launch(emulatorCommand(sdk, avd, consolePort, noWindow), logDirectory.resolve("${emulatorSerial(consolePort)}.log").toFile())
    }

    override suspend fun stopEmulator(sdk: SdkLocation, serial: String) {
        run(listOf(sdk.adb, "-s", serial, "emu", "kill"))
    }

    private suspend fun bootCompleted(sdk: SdkLocation, serial: String): Boolean {
        val result = executor.execute(listOf(sdk.adb, "-s", serial, "shell", "getprop", "sys.boot_completed"))
        return result.isSuccess && result.stdout.trim() == "1"
    }

    private suspend fun run(command: List<String>): String {
        val result = executor.execute(command)
        if (!result.isSuccess) throw WorktreeError.AndroidToolFailed(command, result.exitCode, result.stderr.trim())
        return result.stdout
    }

    private fun sdkCandidates(repositoryPath: String): List<Pair<String, String>> = buildList {
        environmentVariable("ANDROID_HOME")?.takeIf { it.isNotBlank() }?.let { add("ANDROID_HOME" to it) }
        environmentVariable("ANDROID_SDK_ROOT")?.takeIf { it.isNotBlank() }?.let { add("ANDROID_SDK_ROOT" to it) }
        fileManager.readProperties("${repositoryPath.trimEnd('/')}/local.properties")["sdk.dir"]
            ?.takeIf { it.isNotBlank() }
            ?.let { add("local.properties sdk.dir" to it) }
        add("default location" to defaultSdkPath())
    }

    private fun defaultSdkPath(): String = when {
        isWindows -> listOf(environmentVariable("LOCALAPPDATA") ?: "$userHome\\AppData\\Local", "Android", "Sdk").joinToString("\\")
        osName.startsWith("Mac", ignoreCase = true) -> "$userHome/Library/Android/sdk"
        else -> "$userHome/Android/Sdk"
    }

    private fun executablePath(root: String, tool: String): String {
        val separator = if (isWindows) "\\" else "/"
        val relative = if (tool == EMULATOR) listOf("emulator", "emulator") else listOf("platform-tools", "adb")
        return (listOf(root.trimEnd('/', '\\')) + relative).joinToString(separator) + if (isWindows) ".exe" else ""
    }

    private companion object {
        const val EMULATOR = "emulator"
        const val ADB = "adb"
    }
}

fun emulatorCommand(sdk: SdkLocation, avd: String, consolePort: Int, noWindow: Boolean): List<String> =
    listOf(sdk.emulator, "-avd", avd, "-port", consolePort.toString(), "-read-only", "-no-boot-anim") +
        if (noWindow) listOf("-no-window") else emptyList()

internal fun parseAdbDevices(output: String): Map<String, String> =
    output.lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("emulator-") }
        .mapNotNull { line ->
            val parts = line.split(Regex("\\s+"))
            if (parts.size >= 2) parts[0] to parts[1] else null
        }
        .toMap()

private fun launchDetached(command: List<String>, log: File) {
    ProcessBuilder(command)
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.appendTo(log))
        .redirectInput(ProcessBuilder.Redirect.from(File(if (File.separatorChar == '\\') "NUL" else "/dev/null")))
        .start()
}
