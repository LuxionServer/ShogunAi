package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.TerminalLauncher
import app.luxion.shogunai.domain.model.TerminalId
import app.luxion.shogunai.domain.model.TerminalLaunch
import app.luxion.shogunai.domain.model.WorktreeError

class TmuxTerminalLauncher(private val executor: ShellCommandExecutor) : TerminalLauncher {

    override val terminal = TerminalId.TMUX

    override suspend fun open(launch: TerminalLaunch): Result<Unit> = runCatching {
        try {
            requireEnvironmentSupport()
            val session = mostRecentSession()
            val window = executor.runTool(newWindow(session, launch)).stdout.trim()
            executor.runTool(listOf("tmux", "send-keys", "-t", window, "-l", launch.agentCommand))
            executor.runTool(listOf("tmux", "send-keys", "-t", window, "Enter"))
        } catch (failure: ToolCommandFailed) {
            throw WorktreeError.TerminalLaunchFailed(terminal, failure.detail)
        }
    }

    private fun newWindow(session: String, launch: TerminalLaunch): List<String> =
        listOf("tmux", "new-window", "-P", "-F", "#{window_id}", "-t", "=$session:", "-c", launch.worktreePath) +
            launch.variables.flatMap { (name, value) -> listOf("-e", "$name=$value") }

    private suspend fun requireEnvironmentSupport() {
        val version = executor.runTool(listOf("tmux", "-V")).stdout
        val major = VERSION.find(version)?.groupValues?.get(1)?.toIntOrNull()
        if (major != null && major < MINIMUM_MAJOR) {
            throw WorktreeError.TerminalUnsupported(terminal, "it needs tmux $MINIMUM_MAJOR.0 or later, found ${version.trim()}")
        }
    }

    private suspend fun mostRecentSession(): String {
        val listing = executor.runTool(listOf("tmux", "list-sessions", "-F", "#{session_last_attached}\t#{session_name}")).stdout
        return listing.lines().filter { it.isNotBlank() }
            .map { line -> line.substringBefore('\t').toLongOrNull().orZero() to line.substringAfter('\t') }
            .maxByOrNull { it.first }?.second
            ?: throw ToolCommandFailed(null, "no tmux session is running")
    }

    private fun Long?.orZero(): Long = this ?: 0L

    private companion object {
        const val MINIMUM_MAJOR = 3
        val VERSION = Regex("(\\d+)\\.\\d+")
    }
}

class ZellijTerminalLauncher(private val executor: ShellCommandExecutor) : TerminalLauncher {

    override val terminal = TerminalId.ZELLIJ

    override suspend fun open(launch: TerminalLaunch): Result<Unit> = runCatching {
        try {
            val session = executor.runTool(listOf("zellij", "list-sessions", "--short")).stdout
                .lines().map { it.trim() }.lastOrNull { it.isNotEmpty() }
                ?: throw ToolCommandFailed(null, "no Zellij session is running")
            val action = listOf("zellij", "--session", session, "action")
            executor.runTool(action + listOf("new-tab", "--cwd", launch.worktreePath))
            executor.runTool(action + listOf("write-chars", launch.line))
            executor.runTool(action + listOf("write", "13"))
        } catch (failure: ToolCommandFailed) {
            throw WorktreeError.TerminalLaunchFailed(terminal, failure.detail)
        }
    }
}
