package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.HerdrWorktreeOpening
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CliHerdrClientTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/herdr/$name")) { name }.readText().trim()

    private fun client(
        executor: ShellCommandExecutor,
        files: Set<String> = emptySet(),
        osName: String = "Mac OS X",
    ) = CliHerdrClient(executor, userHome = "/home/dev", osName = osName, isFile = { it in files })

    private val worktree = "/home/dev/projects/worktrees/TASK-9"

    @Test
    fun `a worktree is opened focused under its repository`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, fixture("worktree-open.json")) }

        val opening = client(executor).openWorktree("/home/dev/projects/main-repo", worktree, "TASK-9")

        assertEquals(HerdrWorktreeOpening(workspaceId = "w5", rootPaneId = "w5:p1", alreadyOpen = false), opening)
        assertEquals(
            listOf(
                "herdr", "worktree", "open", "--cwd", "/home/dev/projects/main-repo", "--path", worktree,
                "--label", "TASK-9", "--focus",
            ),
            executor.executedCommands.single(),
        )
    }

    @Test
    fun `an already open worktree is reported as such`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, fixture("worktree-open-already.json")) }

        val opening = client(executor).openWorktree("/home/dev/projects/main-repo", worktree, "TASK-9")

        assertTrue(opening.alreadyOpen)
        assertEquals("w5:p1", opening.rootPaneId)
    }

    @Test
    fun `a pane is split to the right with its directory and environment`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, fixture("pane-split.json")) }

        val pane = client(executor).splitPane("w5:p1", worktree, listOf("PORT" to "3100", "APP_SUFFIX" to "-a b=c"))

        assertEquals("w5:p2", pane)
        assertEquals(
            listOf(
                "herdr", "pane", "split", "w5:p1", "--direction", "right", "--cwd", worktree,
                "--env", "PORT=3100", "--env", "APP_SUFFIX=-a b=c", "--focus",
            ),
            executor.executedCommands.single(),
        )
    }

    @Test
    fun `close and run pass their arguments as they are`() = runTest {
        val executor = FakeShellCommandExecutor { command ->
            FakeShellCommandExecutor.success(command, if ("close" in command) fixture("pane-close.json") else "")
        }
        val herdr = client(executor)

        herdr.closePane("w5:p1")
        herdr.runInPane("w5:p2", "headroom wrap claude")

        assertEquals(
            listOf(listOf("herdr", "pane", "close", "w5:p1"), listOf("herdr", "pane", "run", "w5:p2", "headroom wrap claude")),
            executor.executedCommands,
        )
    }

    @Test
    fun `a missing binary means Herdr isn't installed`() = runTest {
        val executor = object : ShellCommandExecutor {
            override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult =
                throw CommandExecutionException(command, IOException("No such file"))
        }

        assertFailsWith<WorktreeError.HerdrNotInstalled> { client(executor).closePane("w5:p1") }
    }

    @Test
    fun `Herdr's error message is reported from standard error when a command fails`() = runTest {
        val executor = FakeShellCommandExecutor { CommandResult(it, 1, "", fixture("server-not-running.json")) }

        val error = assertFailsWith<WorktreeError.HerdrCommandFailed> {
            client(executor).openWorktree("/home/dev/projects/main-repo", worktree, "TASK-9")
        }

        assertEquals("no herdr server is running at /home/dev/.config/herdr/herdr.sock; run `herdr` to start or attach it", error.detail)
    }

    @Test
    fun `an error in the JSON on standard output fails too`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, fixture("server-not-running.json")) }

        val error = assertFailsWith<WorktreeError.HerdrCommandFailed> { client(executor).closePane("w5:p1") }

        assertTrue(error.detail.startsWith("no herdr server is running"), error.detail)
    }

    @Test
    fun `standard error is reported when there's no JSON`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.failure(it, stderr = "unknown command") }

        val error = assertFailsWith<WorktreeError.HerdrCommandFailed> { client(executor).closePane("w9:p1") }

        assertEquals("unknown command", error.detail)
    }

    @Test
    fun `responses without ids fail`() = runTest {
        val executor = FakeShellCommandExecutor { FakeShellCommandExecutor.success(it, """{"id":"x","result":{"type":"ok"}}""") }

        assertFailsWith<WorktreeError.HerdrCommandFailed> { client(executor).openWorktree("/r", "/w", "w") }
        assertFailsWith<WorktreeError.HerdrCommandFailed> { client(executor).splitPane("w1:p1", "/w", emptyList()) }
    }

    @Test
    fun `the binary is looked up in the user's bin folders before the PATH`() {
        val executor = FakeShellCommandExecutor()

        assertEquals("herdr", client(executor).executable())
        assertEquals("/home/dev/.cargo/bin/herdr", client(executor, files = setOf("/home/dev/.cargo/bin/herdr")).executable())
        assertEquals(
            "/home/dev/.local/bin/herdr",
            client(executor, files = setOf("/home/dev/.local/bin/herdr", "/home/dev/.cargo/bin/herdr")).executable(),
        )
        assertEquals("/opt/homebrew/bin/herdr", client(executor, files = setOf("/opt/homebrew/bin/herdr")).executable())
        assertEquals(
            "/home/dev/.cargo/bin/herdr",
            client(executor, files = setOf("/opt/homebrew/bin/herdr", "/home/dev/.cargo/bin/herdr")).executable(),
        )
        assertEquals("herdr.exe", client(executor, osName = "Windows 11").executable())
    }
}
