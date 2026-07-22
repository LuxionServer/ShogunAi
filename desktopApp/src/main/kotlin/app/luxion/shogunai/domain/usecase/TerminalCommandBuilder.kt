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
     * If no Terminal window is open, `do script` with no target creates one
     * directly (fallback, no tabs involved). If a window already exists, a
     * new tab is forced deterministically with Cmd+T (via `System Events`)
     * before running the command — a freshly created tab is always idle, so
     * `do script ... in front window` uses it unambiguously (unlike just
     * reusing "front window", which overwrites the existing tab if Terminal
     * hasn't marked it "busy" yet, producing concatenated commands with no
     * separator). Cmd+T requires Accessibility permission for `System
     * Events` to control Terminal; in dev mode (`./gradlew :desktopApp:run`,
     * no stable app bundle) macOS doesn't always get around to prompting for
     * it, and the keystroke has no effect with no visible error — degrading
     * to reusing the existing tab. Packaging the app as a native `.app`/dmg
     * should let macOS prompt for the permission correctly.
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
