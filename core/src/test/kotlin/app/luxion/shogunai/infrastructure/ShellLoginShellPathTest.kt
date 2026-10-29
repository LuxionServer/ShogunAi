package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.model.HostOs
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ShellLoginShellPathTest {

    private val appPath = "/usr/bin:/bin:/usr/sbin:/sbin"
    private val fallback = listOf(
        "/usr/bin", "/bin", "/usr/sbin", "/sbin",
        "/opt/homebrew/bin", "/usr/local/bin", "/home/dev/.local/bin", "/home/dev/.cargo/bin",
    )

    private fun printing(stdout: String, exitCode: Int = 0) =
        FakeShellCommandExecutor { CommandResult(it, exitCode, stdout, "") }

    private fun loginShellPath(
        executor: FakeShellCommandExecutor,
        os: HostOs = HostOs.MACOS,
        shell: String? = "/bin/zsh",
    ) = ShellLoginShellPath(executor, os, shell, appPath, "/home/dev")

    @Test
    fun `puts the login shell folders before the app ones`() {
        val executor = printing("\n__SHOGUNAI_PATH__/opt/homebrew/bin:/home/dev/.local/bin:/usr/bin__SHOGUNAI_PATH__\n")

        val folders = loginShellPath(executor).folders()

        assertEquals(listOf("/opt/homebrew/bin", "/home/dev/.local/bin", "/usr/bin", "/bin", "/usr/sbin", "/sbin"), folders)
    }

    @Test
    fun `runs the user's shell as a login and interactive shell within a time limit`() {
        val executor = printing("__SHOGUNAI_PATH__/usr/bin__SHOGUNAI_PATH__")

        loginShellPath(executor, shell = "/usr/bin/fish").folders()

        val command = executor.executedCommands.single()
        assertEquals(listOf("/usr/bin/fish", "-l", "-i", "-c"), command.take(4))
        assertTrue("\"\$PATH\"" in command[4])
        assertEquals(5.seconds, executor.timeouts[command])
    }

    @Test
    fun `ignores what the shell's startup files print around the PATH`() {
        val executor = printing("Welcome back\n\n__SHOGUNAI_PATH__/opt/homebrew/bin__SHOGUNAI_PATH__\nbye")

        assertEquals("/opt/homebrew/bin", loginShellPath(executor).folders().first())
    }

    @Test
    fun `keeps the PATH printed by a shell whose startup files fail`() {
        val executor = printing("__SHOGUNAI_PATH__/opt/homebrew/bin__SHOGUNAI_PATH__", exitCode = 1)

        assertEquals("/opt/homebrew/bin", loginShellPath(executor).folders().first())
    }

    @Test
    fun `falls back to the app PATH and the usual install folders when the shell hangs`() {
        val executor = FakeShellCommandExecutor { throw CommandExecutionException.TimedOut(it, 5.seconds) }

        assertEquals(fallback, loginShellPath(executor).folders())
    }

    @Test
    fun `falls back when the shell can't start`() {
        val executor = FakeShellCommandExecutor { throw CommandExecutionException(it, IOException("No such file")) }

        assertEquals(fallback, loginShellPath(executor).folders())
    }

    @Test
    fun `falls back when the shell exits without printing its PATH`() {
        assertEquals(fallback, loginShellPath(printing("", exitCode = 127)).folders())
    }

    @Test
    fun `falls back when the shell prints an empty PATH`() {
        assertEquals(fallback, loginShellPath(printing("__SHOGUNAI_PATH____SHOGUNAI_PATH__")).folders())
    }

    @Test
    fun `falls back without running anything when there is no shell`() {
        val executor = printing("__SHOGUNAI_PATH__/opt/homebrew/bin__SHOGUNAI_PATH__")

        assertEquals(fallback, loginShellPath(executor, shell = " ").folders())
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `reads the shell only once`() {
        val executor = printing("__SHOGUNAI_PATH__/opt/homebrew/bin__SHOGUNAI_PATH__")
        val loginShellPath = loginShellPath(executor)

        loginShellPath.folders()
        loginShellPath.folders()

        assertEquals(1, executor.executedCommands.size)
    }

    @Test
    fun `keeps the app PATH on Windows without starting a shell`() {
        val executor = printing("__SHOGUNAI_PATH__/opt/homebrew/bin__SHOGUNAI_PATH__")
        val loginShellPath = ShellLoginShellPath(executor, HostOs.WINDOWS, null, "C:\\Windows;C:\\Tools", "C:\\Users\\dev")

        assertEquals(listOf("C:\\Windows", "C:\\Tools"), loginShellPath.folders())
        assertTrue(executor.executedCommands.isEmpty())
    }
}
