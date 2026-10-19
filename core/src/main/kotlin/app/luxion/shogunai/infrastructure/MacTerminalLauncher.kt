package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.TerminalLauncher
import app.luxion.shogunai.domain.model.TerminalId
import app.luxion.shogunai.domain.model.TerminalLaunch
import app.luxion.shogunai.domain.model.WorktreeError

class MacTerminalLauncher(
    override val terminal: TerminalId,
    private val executor: ShellCommandExecutor,
) : TerminalLauncher {

    init {
        require(terminal in SCRIPTS) { "${terminal.displayName} isn't scripted through osascript" }
    }

    override suspend fun open(launch: TerminalLaunch): Result<Unit> = runCatching {
        val command = listOf("osascript") + SCRIPTS.getValue(terminal).flatMap { listOf("-e", it) } + launch.line
        try {
            executor.runTool(command, bounded = false)
        } catch (failure: ToolCommandFailed) {
            if (failure.detail.contains(AUTOMATION_REFUSED)) throw WorktreeError.TerminalAutomationRefused(terminal)
            throw WorktreeError.TerminalLaunchFailed(terminal, failure.detail)
        }
        Unit
    }

    companion object {
        private const val AUTOMATION_REFUSED = "(-1743)"

        internal val SCRIPTS = mapOf(
            TerminalId.TERMINAL to listOf(
                "on run argv",
                "tell application \"Terminal\"",
                "activate",
                "do script (item 1 of argv)",
                "end tell",
                "end run",
            ),
            TerminalId.ITERM2 to listOf(
                "on run argv",
                "tell application \"iTerm\"",
                "activate",
                "set newWindow to (create window with default profile)",
                "tell current session of newWindow to write text (item 1 of argv)",
                "end tell",
                "end run",
            ),
        )
    }
}
