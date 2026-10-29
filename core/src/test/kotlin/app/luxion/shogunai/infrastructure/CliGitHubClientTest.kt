package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.CommandTimeouts
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class CliGitHubClientTest {

    private val gh = "/opt/homebrew/bin/gh"
    private val worktree = "/projects/worktrees/TASK-1"

    private fun client(executor: FakeShellCommandExecutor, installed: Boolean = true) =
        CliGitHubClient(executor, findExecutable = { if (installed && it == "gh") gh else null })

    @Test
    fun `a branch's most recent pull request is looked up in the worktree`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, """[{"number":42}]""") }

        assertEquals(42, client(executor).pullRequestFor(worktree, "feature/TASK-1"))
        val command = listOf(gh, "pr", "list", "--head", "feature/TASK-1", "--state", "all", "--limit", "1", "--json", "number")
        assertEquals(listOf(command), executor.executedCommands)
        assertEquals(listOf<String?>(worktree), executor.workingDirectories)
        assertEquals(CommandTimeouts.LISTING, executor.timeouts[command])
    }

    @Test
    fun `a branch without pull requests has none`() = runTest {
        assertNull(client(FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, "[]") }).pullRequestFor(worktree, "feature/TASK-1"))
    }

    @Test
    fun `an existing issue is found by its number`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, """{"number":7}""") }

        assertTrue(client(executor).issueExists(worktree, 7))
        assertEquals(listOf(listOf(gh, "issue", "view", "7", "--json", "number")), executor.executedCommands)
    }

    @Test
    fun `a failing gh, such as one not signed in, finds nothing`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.failure(it, stderr = "To get started with GitHub CLI, please run: gh auth login") }

        assertNull(client(executor).pullRequestFor(worktree, "feature/TASK-1"))
        assertFalse(client(executor).issueExists(worktree, 7))
    }

    @Test
    fun `a lookup that times out finds nothing`() = runTest {
        val executor = FakeShellCommandExecutor { throw CommandExecutionException.TimedOut(it, 10.seconds) }

        assertNull(client(executor).pullRequestFor(worktree, "feature/TASK-1"))
    }

    @Test
    fun `without gh nothing runs`() = runTest {
        val executor = FakeShellCommandExecutor()

        assertNull(client(executor, installed = false).pullRequestFor(worktree, "feature/TASK-1"))
        assertFalse(client(executor, installed = false).issueExists(worktree, 7))
        assertTrue(executor.executedCommands.isEmpty())
    }
}
