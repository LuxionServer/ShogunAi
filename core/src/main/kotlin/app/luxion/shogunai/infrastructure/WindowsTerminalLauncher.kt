package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.TerminalLauncher
import app.luxion.shogunai.domain.model.TerminalId
import app.luxion.shogunai.domain.model.TerminalLaunch
import app.luxion.shogunai.domain.model.WorktreeError

class WindowsTerminalLauncher(private val executor: ShellCommandExecutor) : TerminalLauncher {

    override val terminal = TerminalId.WINDOWS_TERMINAL

    override suspend fun open(launch: TerminalLaunch): Result<Unit> = runCatching {
        val command = listOf(
            "wt.exe", "-w", "0", "new-tab", "-d", escape(launch.worktreePath),
            "powershell.exe", "-NoExit", "-Command", escape(launch.line),
        )
        try {
            executor.runTool(command, bounded = false)
        } catch (failure: ToolCommandFailed) {
            throw WorktreeError.TerminalLaunchFailed(terminal, failure.detail)
        }
    }

    private fun escape(value: String): String = value.replace(";", "\\;")
}
