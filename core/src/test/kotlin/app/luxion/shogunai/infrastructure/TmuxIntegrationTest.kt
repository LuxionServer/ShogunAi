package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.TerminalLaunch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration

class TmuxIntegrationTest {

    private val socket = "shogunai-test-${System.nanoTime()}"
    private val processes = ProcessBuilderShellCommandExecutor(path = null)

    private val isolatedTmux = object : ShellCommandExecutor by processes {
        override suspend fun executeWithin(
            command: List<String>,
            timeout: Duration,
            workingDirectory: String?,
            environment: Map<String, String>,
        ): CommandResult {
            val isolated = if (command.first() == "tmux") listOf("tmux", "-L", socket) + command.drop(1) else command
            return processes.executeWithin(isolated, timeout, workingDirectory, environment)
        }
    }

    private fun tmuxAvailable(): Boolean = runCatching {
        ProcessBuilder("tmux", "-V").redirectErrorStream(true).start().waitFor() == 0
    }.getOrDefault(false)

    private fun linesOf(file: Path): List<String> =
        if (Files.exists(file)) Files.readAllLines(file) else emptyList()

    @Test
    fun `opens a window in a real tmux server with the folder and the environment`() = runBlocking {
        if (!tmuxAvailable()) return@runBlocking
        val folder = Files.createTempDirectory("tmux-integration").toRealPath()
        val output = folder.resolve("output.txt")
        try {
            check(isolatedTmux.executeWithin(listOf("tmux", "new-session", "-d", "-s", "base"), CommandTimeouts.LISTING).isSuccess)
            val launch = TerminalLaunch(
                worktreePath = folder.toString(),
                variables = listOf("SHOGUN_TEST" to "it's \$(id)"),
                agentCommand = "pwd > output.txt; printenv SHOGUN_TEST >> output.txt",
                line = "",
            )

            TmuxTerminalLauncher(isolatedTmux).open(launch).getOrThrow()

            val expected = listOf(folder.toString(), "it's \$(id)")
            repeat(100) { if (linesOf(output) != expected) delay(100) }
            assertEquals(expected, linesOf(output))
        } finally {
            isolatedTmux.executeWithin(listOf("tmux", "kill-server"), CommandTimeouts.LISTING)
            folder.toFile().deleteRecursively()
        }
    }
}
