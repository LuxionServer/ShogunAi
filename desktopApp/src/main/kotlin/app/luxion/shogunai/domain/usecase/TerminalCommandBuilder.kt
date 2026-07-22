package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.model.TerminalEmulator

/**
 * Builds the argv to run to open each [TerminalEmulator] at a given path,
 * running a command inside it.
 *
 * Each branch returns a list of arguments ready to pass directly to
 * `ProcessBuilder`, without invoking a shell to interpret a full string
 * (except for the `bash -c "<command>"` each terminal itself needs to
 * stay open after running the command).
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
     * Substitutes `{path}` and `{command}` in each element of [template],
     * without concatenating anything into a single shell string.
     */
    fun buildCustom(template: List<String>, workingDirectory: String, command: String): List<String> =
        template.map { it.replace("{path}", workingDirectory).replace("{command}", command) }

    /**
     * `do script` with no target window always opens a new Terminal window
     * and runs the command there, regardless of how many windows are
     * already open. An earlier version tried to force a new *tab* instead
     * (Cmd+T via `System Events`), but that requires Accessibility
     * permission for `System Events` to control Terminal; when that
     * permission isn't granted (routinely the case in dev mode, e.g.
     * `./gradlew :desktopApp:run`), the keystroke line throws and aborts the
     * whole AppleScript with no visible error, so the button did nothing.
     * Opening a new window every time trades tab-reuse for a launch that
     * always works with no extra permissions.
     */
    private fun macosTerminalCommand(workingDirectory: String, command: String): List<String> {
        val shellCommand = "cd '$workingDirectory' && $command"
        val escaped = shellCommand.escapedForAppleScript()
        return listOf(
            "osascript",
            "-e", "tell application \"Terminal\"",
            "-e", "do script \"$escaped\"",
            "-e", "activate",
            "-e", "end tell",
        )
    }

    /** Creates a new tab in iTerm2's current window instead of a new window; with no windows, creates one. */
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
