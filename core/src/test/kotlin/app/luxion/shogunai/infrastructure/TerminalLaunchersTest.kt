package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.model.TerminalId
import app.luxion.shogunai.domain.model.TerminalLaunch
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.assertFailsWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TerminalLaunchersTest {

    private val path = "/home/dev/projects/worktrees/TASK-8"
    private val launch = TerminalLaunch(
        worktreePath = path,
        variables = listOf("PORT" to "20000", "NOTE" to "it's \$(id)"),
        agentCommand = "claude",
        line = "cd '$path' && export PORT='20000' && claude",
    )

    @Test
    fun `Terminal runs the line through a fixed script that takes it as an argument`() = runTest {
        val executor = FakeShellCommandExecutor()

        MacTerminalLauncher(TerminalId.TERMINAL, executor).open(launch).getOrThrow()

        val command = executor.executedCommands.single()
        assertEquals("osascript", command.first())
        assertEquals(launch.line, command.last())
        val script = command.filterIndexed { index, _ -> index > 0 && command[index - 1] == "-e" }
        assertTrue(script.contains("do script (item 1 of argv)"))
        assertTrue(script.none { it.contains("TASK-8") || it.contains("claude") })
    }

    @Test
    fun `iTerm2 opens a window with the default profile and writes the line`() = runTest {
        val executor = FakeShellCommandExecutor()

        MacTerminalLauncher(TerminalId.ITERM2, executor).open(launch).getOrThrow()

        val command = executor.executedCommands.single()
        assertTrue(command.contains("set newWindow to (create window with default profile)"))
        assertTrue(command.contains("tell current session of newWindow to write text (item 1 of argv)"))
        assertEquals(launch.line, command.last())
    }

    @Test
    fun `a refused macOS automation permission gets its own error`() = runTest {
        val executor = FakeShellCommandExecutor {
            FakeShellCommandExecutor.failure(it, stderr = "execution error: Not authorized to send Apple events to Terminal. (-1743)")
        }

        val error = MacTerminalLauncher(TerminalId.TERMINAL, executor).open(launch).exceptionOrNull()

        assertIs<WorktreeError.TerminalAutomationRefused>(error)
        assertTrue(error.message.orEmpty().contains("System Settings > Privacy & Security > Automation"))
    }

    @Test
    fun `any other osascript failure names the terminal and its output`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.failure(it, stderr = "syntax error") }

        val error = MacTerminalLauncher(TerminalId.ITERM2, executor).open(launch).exceptionOrNull()

        assertIs<WorktreeError.TerminalLaunchFailed>(error)
        assertEquals(TerminalId.ITERM2, error.terminal)
        assertTrue(error.message.orEmpty().contains("syntax error"))
    }

    @Test
    fun `a missing osascript is reported as a launch failure`() = runTest {
        val executor = FakeShellCommandExecutor { throw CommandExecutionException(it, IOException("No such file")) }

        val error = MacTerminalLauncher(TerminalId.TERMINAL, executor).open(launch).exceptionOrNull()

        assertIs<WorktreeError.TerminalLaunchFailed>(error)
    }

    @Test
    fun `tmux opens a window in the most recently attached session with the environment`() = runTest {
        val executor = FakeShellCommandExecutor { command ->
            when (command[1]) {
                "-V" -> FakeShellCommandExecutor.success(command, "tmux 3.4\n")
                "list-sessions" -> FakeShellCommandExecutor.success(command, "100\tolder\n300\twork on api\n200\tother\n")
                "new-window" -> FakeShellCommandExecutor.success(command, "@7\n")
                else -> FakeShellCommandExecutor.success(command)
            }
        }

        TmuxTerminalLauncher(executor).open(launch).getOrThrow()

        assertEquals(
            listOf(
                "tmux", "new-window", "-P", "-F", "#{window_id}", "-t", "=work on api:", "-c", path,
                "-e", "PORT=20000", "-e", "NOTE=it's \$(id)",
            ),
            executor.executedCommands.single { it[1] == "new-window" },
        )
        assertTrue(executor.executed("tmux", "send-keys", "-t", "@7", "-l", "claude"))
        assertEquals(listOf("tmux", "send-keys", "-t", "@7", "Enter"), executor.executedCommands.last())
    }

    @Test
    fun `tmux older than 3_0 is reported as unsupported`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, "tmux 2.9a\n") }

        val error = TmuxTerminalLauncher(executor).open(launch).exceptionOrNull()

        assertIs<WorktreeError.TerminalUnsupported>(error)
        assertTrue(executor.executedCommands.none { it[1] == "new-window" })
    }

    @Test
    fun `tmux without a running session reports a launch failure`() = runTest {
        val executor = FakeShellCommandExecutor { command ->
            if (command[1] == "-V") {
                FakeShellCommandExecutor.success(command, "tmux next-3.5\n")
            } else {
                FakeShellCommandExecutor.failure(command, stderr = "no server running on /tmp/tmux-501/default")
            }
        }

        val error = TmuxTerminalLauncher(executor).open(launch).exceptionOrNull()

        assertIs<WorktreeError.TerminalLaunchFailed>(error)
        assertTrue(error.message.orEmpty().contains("no server running"))
    }

    @Test
    fun `tmux with a server but no session reports a launch failure`() = runTest {
        val executor = FakeShellCommandExecutor { command ->
            if (command[1] == "-V") FakeShellCommandExecutor.success(command, "tmux 3.4\n") else FakeShellCommandExecutor.success(command, "\n")
        }

        val error = TmuxTerminalLauncher(executor).open(launch).exceptionOrNull()

        assertIs<WorktreeError.TerminalLaunchFailed>(error)
        assertTrue(error.message.orEmpty().contains("no tmux session is running"))
    }

    @Test
    fun `Zellij opens a tab in its latest session and writes the line`() = runTest {
        val executor = FakeShellCommandExecutor { command ->
            if (command[1] == "list-sessions") FakeShellCommandExecutor.success(command, "first\nlast\n") else FakeShellCommandExecutor.success(command)
        }

        ZellijTerminalLauncher(executor).open(launch).getOrThrow()

        assertEquals(
            listOf(
                listOf("zellij", "list-sessions", "--short"),
                listOf("zellij", "--session", "last", "action", "new-tab", "--cwd", path),
                listOf("zellij", "--session", "last", "action", "write-chars", launch.line),
                listOf("zellij", "--session", "last", "action", "write", "13"),
            ),
            executor.executedCommands,
        )
    }

    @Test
    fun `Zellij without a session reports a launch failure`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, "") }

        val error = ZellijTerminalLauncher(executor).open(launch).exceptionOrNull()

        assertIs<WorktreeError.TerminalLaunchFailed>(error)
    }

    @Test
    fun `Windows Terminal opens a tab in the folder and escapes the semicolons of the PowerShell line`() = runTest {
        val executor = FakeShellCommandExecutor()
        val windowsLaunch = launch.copy(line = "Set-Location -LiteralPath 'C:\\work'; \$env:PORT = '20000'; claude")

        WindowsTerminalLauncher(executor).open(windowsLaunch).getOrThrow()

        assertEquals(
            listOf(
                "wt.exe", "-w", "0", "new-tab", "-d", path,
                "powershell.exe", "-NoExit", "-Command",
                "Set-Location -LiteralPath 'C:\\work'\\; \$env:PORT = '20000'\\; claude",
            ),
            executor.executedCommands.single(),
        )
    }

    @Test
    fun `Windows Terminal escapes a semicolon in the folder`() = runTest {
        val executor = FakeShellCommandExecutor()

        WindowsTerminalLauncher(executor).open(launch.copy(worktreePath = "C:\\a;b")).getOrThrow()

        assertEquals("C:\\a\\;b", executor.executedCommands.single()[5])
    }

    @Test
    fun `Windows Terminal failures name the terminal`() = runTest {
        val executor = FakeShellCommandExecutor { throw CommandExecutionException(it, IOException("not found")) }

        val error = WindowsTerminalLauncher(executor).open(launch).exceptionOrNull()

        assertIs<WorktreeError.TerminalLaunchFailed>(error)
        assertEquals(TerminalId.WINDOWS_TERMINAL, error.terminal)
    }

    @Test
    fun `an unrecognised tmux version is allowed and a single session without a timestamp is used`() = runTest {
        val executor = FakeShellCommandExecutor { command ->
            when (command[1]) {
                "-V" -> FakeShellCommandExecutor.success(command, "tmux next\n")
                "list-sessions" -> FakeShellCommandExecutor.success(command, "\tsolo\n\n \n")
                "new-window" -> FakeShellCommandExecutor.success(command, "@1\n")
                else -> FakeShellCommandExecutor.success(command)
            }
        }

        TmuxTerminalLauncher(executor).open(launch).getOrThrow()

        assertTrue(executor.executedCommands.single { it[1] == "new-window" }.contains("=solo:"))
    }

    @Test
    fun `only Terminal and iTerm2 are scripted through osascript`() {
        assertFailsWith<IllegalArgumentException> { MacTerminalLauncher(TerminalId.TMUX, FakeShellCommandExecutor()) }
    }
}
