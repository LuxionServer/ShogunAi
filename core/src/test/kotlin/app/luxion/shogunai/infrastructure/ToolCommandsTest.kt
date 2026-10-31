package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.CommandResult
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ToolCommandsTest {

    @Test
    fun `a tool that can't start reports its cause or the command`() = runTest {
        val withCause = FakeShellCommandExecutor { throw CommandExecutionException(it, IOException("no such file")) }
        val withoutCause = FakeShellCommandExecutor { throw CommandExecutionException(it, IOException()) }

        assertEquals("no such file", assertFailsWith<ToolCommandFailed> { withCause.runTool(listOf("tool")) }.detail)
        assertEquals("Could not start the command: tool", assertFailsWith<ToolCommandFailed> { withoutCause.runTool(listOf("tool"), bounded = false) }.detail)
    }

    @Test
    fun `a failure is described by its error output, its output or its exit code`() {
        assertEquals("err", CommandResult(listOf("t"), 1, "out", " err ").failureDetail())
        assertEquals("out", CommandResult(listOf("t"), 1, " out ", " ").failureDetail())
        assertEquals("exit code 1", CommandResult(listOf("t"), 1, " ", " ").failureDetail())
    }
}
