package app.luxion.shogunai.domain.executor

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ShellCommandExecutorTest {

    @Test
    fun `an executor without its own environment support runs the command as is`() = runTest {
        val executed = mutableListOf<Pair<List<String>, String?>>()
        val executor = object : ShellCommandExecutor {
            override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult {
                executed += command to workingDirectory
                return CommandResult(command, 0, "done", "")
            }
        }

        val result = executor.executeWithEnvironment(listOf("git", "status"), mapOf("NAME" to "value"), "/home/dev/projects/main-repo")

        assertEquals("done", result.stdout)
        assertEquals(listOf<Pair<List<String>, String?>>(listOf("git", "status") to "/home/dev/projects/main-repo"), executed)
    }
}
