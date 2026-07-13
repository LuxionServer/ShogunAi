package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.model.TerminalEmulator

/**
 * Construye el argv a ejecutar para abrir cada [TerminalEmulator] en una ruta
 * dada, corriendo un comando dentro.
 *
 * Cada rama devuelve una lista de argumentos lista para pasar directamente a
 * `ProcessBuilder`, sin invocar un shell para interpretar una cadena completa
 * (salvo el propio `bash -c "<comando>"` que cada terminal necesita para
 * quedarse abierta tras ejecutar el comando).
 */
object TerminalCommandBuilder {

    fun build(emulator: TerminalEmulator, workingDirectory: String, command: String): List<String> =
        when (emulator) {
            TerminalEmulator.MACOS_TERMINAL -> macosTerminalCommand(workingDirectory, command)
            TerminalEmulator.ITERM2 -> iTerm2Command(workingDirectory, command)
            TerminalEmulator.WARP -> listOf("open", "-a", "Warp", workingDirectory)
            TerminalEmulator.GNOME_TERMINAL -> listOf(
                "gnome-terminal",
                "--working-directory=$workingDirectory",
                "--",
                "bash",
                "-c",
                "$command; exec bash",
            )
            TerminalEmulator.KONSOLE -> listOf(
                "konsole",
                "--workdir",
                workingDirectory,
                "-e",
                "bash",
                "-c",
                "$command; exec bash",
            )
            TerminalEmulator.XTERM -> listOf(
                "xterm",
                "-e",
                "bash",
                "-c",
                "cd '$workingDirectory' && $command; exec bash",
            )
        }

    /**
     * Sustituye `{path}` y `{command}` en cada elemento de [template], sin
     * concatenar nada en una única cadena de shell.
     */
    fun buildCustom(template: List<String>, workingDirectory: String, command: String): List<String> =
        template.map { it.replace("{path}", workingDirectory).replace("{command}", command) }

    /**
     * Si no hay ninguna ventana de Terminal abierta, `do script` sin destino
     * la crea directamente (fallback, sin pestañas de por medio). Si ya hay
     * una ventana, se fuerza una pestaña nueva de forma determinista con
     * Cmd+T (vía `System Events`) antes de correr el comando — una pestaña
     * recién creada siempre está idle, así que `do script ... in front
     * window` la usa sin ambigüedad (a diferencia de reusar "front window"
     * sin más, que escribe sobre la pestaña existente si Terminal aún no la
     * marcó como "ocupada", produciendo comandos concatenados sin separador).
     * Cmd+T requiere permiso de Accesibilidad para que `System Events`
     * controle Terminal; en modo dev (`./gradlew :desktopApp:run`, sin
     * bundle de app estable) macOS no siempre llega a pedirlo, y el keystroke
     * queda sin efecto y sin error visible — degradando a reusar la pestaña
     * existente. Empaquetar la app como `.app`/dmg nativo debería permitir
     * que macOS pida el permiso correctamente.
     */
    private fun macosTerminalCommand(workingDirectory: String, command: String): List<String> {
        val shellCommand = "cd '$workingDirectory' && $command"
        val escaped = shellCommand.escapedForAppleScript()
        return listOf(
            "osascript",
            "-e", "set alreadyRunning to false",
            "-e", "tell application \"Terminal\"",
            "-e", "if (count of windows) = 0 then",
            "-e", "do script \"$escaped\"",
            "-e", "set alreadyRunning to true",
            "-e", "else",
            "-e", "activate",
            "-e", "end if",
            "-e", "end tell",
            "-e", "if alreadyRunning is false then",
            "-e", "tell application \"System Events\" to keystroke \"t\" using command down",
            "-e", "tell application \"Terminal\" to do script \"$escaped\" in front window",
            "-e", "end if",
        )
    }

    /** Crea una pestaña nueva en la ventana actual de iTerm2 en vez de una ventana nueva; sin ventanas, crea una. */
    private fun iTerm2Command(workingDirectory: String, command: String): List<String> {
        val shellCommand = "cd '$workingDirectory' && $command"
        val escaped = shellCommand.escapedForAppleScript()
        return listOf(
            "osascript",
            "-e", "tell application \"iTerm2\"",
            "-e", "if (count of windows) > 0 then",
            "-e", "tell current window to create tab with default profile",
            "-e", "else",
            "-e", "create window with default profile",
            "-e", "end if",
            "-e", "tell current session of current window to write text \"$escaped\"",
            "-e", "end tell",
        )
    }

    private fun String.escapedForAppleScript(): String =
        replace("\\", "\\\\").replace("\"", "\\\"")
}
