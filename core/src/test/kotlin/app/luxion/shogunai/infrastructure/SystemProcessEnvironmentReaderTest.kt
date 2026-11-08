package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.model.HostOs
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SystemProcessEnvironmentReaderTest {

    private val procRoot: Path = Files.createTempDirectory("proc")

    @AfterTest
    fun cleanUp() {
        procRoot.toFile().deleteRecursively()
    }

    private val psCommand = listOf("ps", "-E", "-ww", "-o", "command=", "-p", "4242")

    @Test
    fun `on macOS the variable comes from ps with the listing timeout`() = runTest {
        val executor = FakeShellCommandExecutor { command ->
            FakeShellCommandExecutor.success(command, "claude -n main-repo-task-1 TERM=xterm ORCA_TERMINAL_HANDLE=term_abc HOME=/home/dev\n")
        }

        val handle = SystemProcessEnvironmentReader(executor, HostOs.MACOS).variable(4242, "ORCA_TERMINAL_HANDLE")

        assertEquals("term_abc", handle)
        assertEquals(listOf(psCommand), executor.executedCommands)
        assertEquals(CommandTimeouts.LISTING, executor.timeouts[psCommand])
    }

    @Test
    fun `on macOS a missing variable or a failed ps gives null`() = runTest {
        val missing = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, "claude TERM=xterm\n") }
        val failed = FakeShellCommandExecutor { FakeShellCommandExecutor.failure(it) }

        assertNull(SystemProcessEnvironmentReader(missing, HostOs.MACOS).variable(4242, "ORCA_TERMINAL_HANDLE"))
        assertNull(SystemProcessEnvironmentReader(failed, HostOs.MACOS).variable(4242, "ORCA_TERMINAL_HANDLE"))
    }

    @Test
    fun `a variable that only prefixes another one doesn't match`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, "claude ORCA_TERMINAL_HANDLE_OLD=term_old\n") }

        assertNull(SystemProcessEnvironmentReader(executor, HostOs.MACOS).variable(4242, "ORCA_TERMINAL_HANDLE"))
    }

    @Test
    fun `on Linux the variable comes from the process environ file`() = runTest {
        procRoot.resolve("4242").createDirectories().resolve("environ")
            .writeText("TERM=xterm\u0000ORCA_TERMINAL_HANDLE=term_abc\u0000HOME=/home/dev\u0000")
        val executor = FakeShellCommandExecutor()

        val reader = SystemProcessEnvironmentReader(executor, HostOs.LINUX, procRoot.toString())

        assertEquals("term_abc", reader.variable(4242, "ORCA_TERMINAL_HANDLE"))
        assertNull(reader.variable(7, "ORCA_TERMINAL_HANDLE"))
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `on Windows nothing is read`() = runTest {
        val executor = FakeShellCommandExecutor()

        assertNull(SystemProcessEnvironmentReader(executor, HostOs.WINDOWS).variable(4242, "ORCA_TERMINAL_HANDLE"))
        assertTrue(executor.executedCommands.isEmpty())
    }

    @Test
    fun `an empty value is no value`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, "claude ORCA_TERMINAL_HANDLE= TERM=xterm\n") }

        assertNull(SystemProcessEnvironmentReader(executor, HostOs.MACOS).variable(4242, "ORCA_TERMINAL_HANDLE"))
    }
}
