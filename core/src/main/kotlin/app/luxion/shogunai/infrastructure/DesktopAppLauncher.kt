package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.AppLauncher
import java.io.IOException
import java.nio.file.Path

internal fun appLaunchCommand(executable: String?, osName: String): List<String>? {
    if (executable.isNullOrBlank()) return null
    val normalized = executable.replace('\\', '/')
    val bundleMarker = ".app/Contents/MacOS/"
    val fileName = normalized.substringAfterLast('/')
    return when {
        osName.startsWith("Mac") && bundleMarker in normalized ->
            listOf("open", normalized.substringBefore(bundleMarker) + ".app")
        osName.startsWith("Windows") && fileName.equals("ShogunAi.exe", ignoreCase = true) -> listOf(executable)
        osName.startsWith("Linux") && fileName == "ShogunAi" -> listOf(executable)
        else -> null
    }
}

class DesktopAppLauncher(
    private val executable: () -> String? = { ProcessHandle.current().info().command().orElse(null) },
    private val osName: String = System.getProperty("os.name"),
) : AppLauncher {

    private val command: List<String>? by lazy {
        val real = executable()?.let { runCatching { Path.of(it).toRealPath().toString() }.getOrDefault(it) }
        appLaunchCommand(real, osName)
    }

    override fun isAvailable(): Boolean = command != null

    override fun launch() {
        val argv = command ?: throw IOException("The desktop app can't be located")
        ProcessBuilder(argv)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
            .outputStream.close()
    }
}
