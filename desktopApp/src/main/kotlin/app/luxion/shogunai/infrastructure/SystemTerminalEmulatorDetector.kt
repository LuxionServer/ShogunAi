package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.executor.TerminalEmulatorDetector
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.model.TerminalEmulator

/**
 * Implementación de [TerminalEmulatorDetector] para macOS y Linux.
 *
 * En macOS comprueba la existencia de la app en `/Applications`; Terminal.app
 * viene siempre instalado con el sistema, así que actúa de última opción.
 * En Linux comprueba si el binario está en el `PATH` con `which`.
 */
class SystemTerminalEmulatorDetector(
    private val fileManager: FileManager,
    private val shellCommandExecutor: ShellCommandExecutor,
    private val osName: String = System.getProperty("os.name") ?: "",
) : TerminalEmulatorDetector {

    override suspend fun detectAvailable(): List<TerminalEmulator> =
        if (isMacOs()) detectMacOs() else detectLinux()

    private fun isMacOs(): Boolean = osName.contains("mac", ignoreCase = true)

    private fun detectMacOs(): List<TerminalEmulator> = buildList {
        if (fileManager.exists("/Applications/iTerm.app")) add(TerminalEmulator.ITERM2)
        if (fileManager.exists("/Applications/Warp.app")) add(TerminalEmulator.WARP)
        add(TerminalEmulator.MACOS_TERMINAL)
    }

    private suspend fun detectLinux(): List<TerminalEmulator> = buildList {
        if (isBinaryAvailable("gnome-terminal")) add(TerminalEmulator.GNOME_TERMINAL)
        if (isBinaryAvailable("konsole")) add(TerminalEmulator.KONSOLE)
        if (isBinaryAvailable("xterm")) add(TerminalEmulator.XTERM)
    }

    private suspend fun isBinaryAvailable(binary: String): Boolean =
        shellCommandExecutor.execute(listOf("which", binary)).isSuccess
}
