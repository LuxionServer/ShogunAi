package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.io.HostPath
import app.luxion.shogunai.domain.io.HostPathSource
import app.luxion.shogunai.domain.model.HostOs
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SystemHostEnvironmentTest {

    private val appPath = "/usr/bin:/bin:/usr/sbin:/sbin"
    private val withUsualFolders = listOf(
        "/usr/bin", "/bin", "/usr/sbin", "/sbin",
        "/opt/homebrew/bin", "/usr/local/bin", "/home/dev/.local/bin", "/home/dev/.cargo/bin",
    )
    private val fallback = HostPath(withUsualFolders, HostPathSource.FALLBACK)

    private fun printing(stdout: String, exitCode: Int = 0) =
        FakeShellCommandExecutor { CommandResult(it, exitCode, stdout, "") }

    private fun desktopApp(
        executor: FakeShellCommandExecutor,
        os: HostOs = HostOs.MACOS,
        shell: String? = "/bin/zsh",
    ) = SystemHostEnvironment(os, listOfNotNull("PATH" to appPath, shell?.let { "SHELL" to it }).toMap(), "/home/dev", executor)

    @Test
    fun `puts the login shell folders before the app ones`() {
        val executor = printing("\n__SHOGUNAI_PATH__/opt/homebrew/bin:/home/dev/.local/bin:/usr/bin__SHOGUNAI_PATH__\n")

        val path = desktopApp(executor).path

        assertEquals(listOf("/opt/homebrew/bin", "/home/dev/.local/bin", "/usr/bin", "/bin", "/usr/sbin", "/sbin"), path.folders)
        assertEquals(HostPathSource.LOGIN_SHELL, path.source)
    }

    @Test
    fun `runs the user's shell as a login and interactive shell within a time limit`() {
        val executor = printing("__SHOGUNAI_PATH__/usr/bin__SHOGUNAI_PATH__")

        desktopApp(executor, shell = "/usr/bin/fish").path

        val command = executor.executedCommands.single()
        assertEquals(listOf("/usr/bin/fish", "-l", "-i", "-c"), command.take(4))
        assertTrue("\"\$PATH\"" in command[4])
        assertEquals(5.seconds, executor.timeouts[command])
    }

    @Test
    fun `ignores what the shell's startup files print around the PATH`() {
        val executor = printing("Welcome back\n\n__SHOGUNAI_PATH__/opt/homebrew/bin__SHOGUNAI_PATH__\nbye")

        assertEquals("/opt/homebrew/bin", desktopApp(executor).path.folders.first())
    }

    @Test
    fun `keeps the PATH printed by a shell whose startup files fail`() {
        val executor = printing("__SHOGUNAI_PATH__/opt/homebrew/bin__SHOGUNAI_PATH__", exitCode = 1)

        assertEquals("/opt/homebrew/bin", desktopApp(executor).path.folders.first())
    }

    @Test
    fun `falls back to the app PATH and the usual install folders when the shell hangs`() {
        val executor = FakeShellCommandExecutor { throw CommandExecutionException.TimedOut(it, 5.seconds) }

        assertEquals(fallback, desktopApp(executor).path)
    }

    @Test
    fun `falls back when the shell can't start`() {
        val executor = FakeShellCommandExecutor { throw CommandExecutionException(it, IOException("No such file")) }

        assertEquals(fallback, desktopApp(executor).path)
    }

    @Test
    fun `falls back when the shell exits without printing its PATH`() {
        assertEquals(fallback, desktopApp(printing("", exitCode = 127)).path)
    }

    @Test
    fun `falls back when the shell prints an empty PATH`() {
        assertEquals(fallback, desktopApp(printing("__SHOGUNAI_PATH____SHOGUNAI_PATH__")).path)
    }

    @Test
    fun `falls back without running anything when there is no shell`() {
        val executor = printing("__SHOGUNAI_PATH__/opt/homebrew/bin__SHOGUNAI_PATH__")

        assertEquals(fallback, desktopApp(executor, shell = null).path)
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `treats a blank shell as no shell`() {
        val executor = printing("__SHOGUNAI_PATH__/opt/homebrew/bin__SHOGUNAI_PATH__")
        val environment = desktopApp(executor, shell = " ")

        assertNull(environment.shell)
        assertEquals(fallback, environment.path)
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `reads the shell only once`() {
        val executor = printing("__SHOGUNAI_PATH__/opt/homebrew/bin__SHOGUNAI_PATH__")
        val environment = desktopApp(executor)

        environment.path
        environment.path

        assertEquals(1, executor.executedCommands.size)
    }

    @Test
    fun `keeps the app PATH on Windows without starting a shell`() {
        val executor = printing("__SHOGUNAI_PATH__/opt/homebrew/bin__SHOGUNAI_PATH__")
        val environment = SystemHostEnvironment(HostOs.WINDOWS, mapOf("Path" to "C:\\Windows;C:\\Tools"), "C:\\Users\\dev", executor)

        assertEquals(HostPath(listOf("C:\\Windows", "C:\\Tools"), HostPathSource.PROCESS), environment.path)
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `the command line keeps its own PATH first and adds the usual install folders without a shell`() {
        val environment = SystemHostEnvironment(
            HostOs.LINUX,
            mapOf("PATH" to "/home/dev/bin:/usr/bin:/bin:/usr/local/bin", "SHELL" to "/bin/bash"),
            "/home/dev",
            loginShell = null,
        )

        assertEquals(
            HostPath(
                listOf("/home/dev/bin", "/usr/bin", "/bin", "/usr/local/bin", "/opt/homebrew/bin", "/home/dev/.local/bin", "/home/dev/.cargo/bin"),
                HostPathSource.PROCESS,
            ),
            environment.path,
        )
    }

    @Test
    fun `exposes the home folder, the shell and other variables`() {
        val environment = SystemHostEnvironment(
            HostOs.MACOS,
            mapOf("SHELL" to "/bin/zsh", "CLAUDE_CONFIG_DIR" to "/home/dev/.claude-work"),
            "/home/dev",
            loginShell = null,
        )

        assertEquals("/home/dev", environment.home)
        assertEquals("/bin/zsh", environment.shell)
        assertEquals("/home/dev/.claude-work", environment.variable("CLAUDE_CONFIG_DIR"))
        assertNull(environment.variable("claude_config_dir"))
        assertNull(environment.variable("GRADLE_USER_HOME"))
    }

    @Test
    fun `reads variables regardless of case on Windows`() {
        val environment = SystemHostEnvironment(HostOs.WINDOWS, mapOf("LocalAppData" to "C:\\Users\\dev\\AppData\\Local"), "C:\\Users\\dev", null)

        assertEquals("C:\\Users\\dev\\AppData\\Local", environment.variable("LOCALAPPDATA"))
    }
}
