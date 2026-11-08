package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PowerShellUserPathTest {

    @Test
    fun `finds a folder in the user PATH regardless of case and trailing separator`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, "C:\\Tools;C:\\Users\\Dev\\.shogunai\\bin\\\r\n") }

        assertTrue(PowerShellUserPath(executor).contains("c:\\users\\dev\\.shogunai\\bin"))
    }

    @Test
    fun `does not find a folder that isn't there or when PowerShell fails`() = runTest {
        assertFalse(PowerShellUserPath(FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, "C:\\Tools") }).contains("C:\\Other"))
        assertFalse(PowerShellUserPath(FakeShellCommandExecutor { FakeShellCommandExecutor.failure(it) }).contains("C:\\Other"))
    }

    @Test
    fun `adds a folder through PowerShell as separate arguments`() = runTest {
        val executor = FakeShellCommandExecutor()

        PowerShellUserPath(executor).add("C:\\Users\\dev\\.shogunai\\bin")

        val command = executor.executedCommands.single()
        assertEquals(listOf("powershell", "-NoProfile", "-NonInteractive", "-Command"), command.take(4))
        assertTrue("'C:\\Users\\dev\\.shogunai\\bin'" in command[4])
        assertTrue("SetEnvironmentVariable" in command[4])
    }

    @Test
    fun `quotes a folder with an apostrophe`() = runTest {
        val executor = FakeShellCommandExecutor()

        PowerShellUserPath(executor).add("C:\\Users\\O'Neil\\bin")

        assertTrue("'C:\\Users\\O''Neil\\bin'" in executor.executedCommands.single()[4])
    }

    @Test
    fun `removes a folder through PowerShell and reports a failure`() = runTest {
        val executor = FakeShellCommandExecutor()

        PowerShellUserPath(executor).remove("C:\\Users\\dev\\.shogunai\\bin\\")

        assertTrue("'C:\\Users\\dev\\.shogunai\\bin'" in executor.executedCommands.single()[4])
        assertFailsWith<IllegalStateException> {
            PowerShellUserPath(FakeShellCommandExecutor { FakeShellCommandExecutor.failure(it, stderr = "denied") }).remove("C:\\Tools")
        }
    }

    @Test
    fun `a failing add is reported`() = runTest {
        assertFailsWith<IllegalStateException> {
            PowerShellUserPath(FakeShellCommandExecutor { FakeShellCommandExecutor.failure(it, stderr = "denied") }).add("C:\\Tools")
        }
    }
}
