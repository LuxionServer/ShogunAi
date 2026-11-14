package app.luxion.shogunai.infrastructure

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProcessBuilderForegroundCommandRunnerTest {

    private val runner = ProcessBuilderForegroundCommandRunner()
    private val folder = Files.createTempDirectory("foreground").toString()

    private fun exiting(code: Int) = if (isWindowsHost) listOf("cmd", "/c", "exit $code") else listOf("sh", "-c", "exit $code")

    @Test
    fun `the command's exit code is returned`() = runBlocking {
        assertEquals(0, runner.run(exiting(0), folder))
        assertEquals(3, runner.run(exiting(3), folder))
    }

    @Test
    fun `a command that can't start returns no exit code`() = runBlocking {
        assertNull(runner.run(listOf("shogun-test-command-that-does-not-exist"), folder))
    }

    @Test
    fun `a cancelled run stops its process`() = runBlocking {
        val sleeping = if (isWindowsHost) listOf("ping", "-n", "30", "127.0.0.1") else listOf("sleep", "30")
        val started = System.nanoTime()
        val run = async(Dispatchers.Default) { runner.run(sleeping, folder) }
        delay(500)

        run.cancel()
        run.join()

        assertTrue(run.isCancelled)
        assertTrue(System.nanoTime() - started < 20_000_000_000L)
    }
}
