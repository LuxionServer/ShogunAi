package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.AndroidSdk
import app.luxion.shogunai.domain.io.EmulatorProcess
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.SdkLocation
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.emulatorSerial
import app.luxion.shogunai.domain.usecase.nestedGradleBuildFolders
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

class LocalAndroidSdk(
    private val executor: ShellCommandExecutor,
    private val fileManager: FileManager,
    private val environmentVariable: (String) -> String?,
    private val userHome: String,
    private val osName: String = System.getProperty("os.name", ""),
    private val logDirectory: Path,
    private val launch: (command: List<String>, log: File) -> EmulatorProcess = ::launchDetached,
) : AndroidSdk {

    private val bootedSerials: MutableSet<String> = ConcurrentHashMap.newKeySet()

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

    override suspend fun deviceStates(sdk: SdkLocation): Map<String, EmulatorState> {
        val devices = parseAdbDevices(run(listOf(sdk.adb, "devices")))
        val online = devices.filterValues { it == "device" }.keys
        bootedSerials.retainAll(online)
        return devices.mapValues { (serial, status) ->
            when {
                status != "device" -> EmulatorState.BOOTING
                serial in bootedSerials -> EmulatorState.RUNNING
                bootCompleted(sdk, serial) -> {
                    bootedSerials.add(serial)
                    EmulatorState.RUNNING
                }
                else -> EmulatorState.BOOTING
            }
        }
    }

    override fun startEmulator(sdk: SdkLocation, avd: String, consolePort: Int, noWindow: Boolean): EmulatorProcess {
        Files.createDirectories(logDirectory)
        return launch(emulatorCommand(sdk, avd, consolePort, noWindow), logDirectory.resolve("${emulatorSerial(consolePort)}.log").toFile())
    }

    override suspend fun stopEmulator(sdk: SdkLocation, serial: String) {
        bootedSerials.remove(serial)
        run(listOf(sdk.adb, "-s", serial, "emu", "kill"))
    }

    private suspend fun bootCompleted(sdk: SdkLocation, serial: String): Boolean {
        val result = executor.executeWithin(listOf(sdk.adb, "-s", serial, "shell", "getprop", "sys.boot_completed"), CommandTimeouts.LISTING)
        return result.isSuccess && result.stdout.trim() == "1"
    }

    private suspend fun run(command: List<String>): String {
        val result = executor.executeWithin(command, CommandTimeouts.LISTING)
        if (!result.isSuccess) throw WorktreeError.AndroidToolFailed(command, result.exitCode, result.stderr.trim())
        return result.stdout
    }

    private fun sdkCandidates(repositoryPath: String): List<Pair<String, String>> = buildList {
        environmentVariable("ANDROID_HOME")?.takeIf { it.isNotBlank() }?.let { add("ANDROID_HOME" to it) }
        environmentVariable("ANDROID_SDK_ROOT")?.takeIf { it.isNotBlank() }?.let { add("ANDROID_SDK_ROOT" to it) }
        val root = repositoryPath.trimEnd('/')
        (listOf("") + nestedGradleBuildFolders(fileManager, root)).forEach { folder ->
            val properties = if (folder.isEmpty()) LOCAL_PROPERTIES else "$folder/$LOCAL_PROPERTIES"
            fileManager.readProperties("$root/$properties")["sdk.dir"]
                ?.takeIf { it.isNotBlank() }
                ?.let { add("$properties sdk.dir" to it) }
        }
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
        const val LOCAL_PROPERTIES = "local.properties"
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

internal fun nullDevice(separator: Char): File = File(if (separator == '\\') "NUL" else "/dev/null")

internal fun launchDetached(command: List<String>, log: File): EmulatorProcess {
    val process = ProcessBuilder(WindowsArguments.prepare(command, System.getProperty("os.name")))
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.appendTo(log))
        .redirectInput(ProcessBuilder.Redirect.from(nullDevice(File.separatorChar)))
        .start()
    return object : EmulatorProcess {
        override val isAlive: Boolean get() = process.isAlive

        override fun lastLogLines(count: Int): String =
            runCatching { log.readLines().takeLast(count).joinToString("\n") }.getOrDefault("")
    }
}
