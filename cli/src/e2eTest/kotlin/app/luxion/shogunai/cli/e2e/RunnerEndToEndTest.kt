package app.luxion.shogunai.cli.e2e

import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunnerEndToEndTest {

    private val sandbox = Sandbox()
    private val repository = sandbox.repository("main-repo")
    private val worktree = sandbox.root.resolve("main-repo-worktrees/TASK-1")

    init {
        FakeRunners.install(sandbox)
        sandbox.shogunSucceeds(repository, "project", "add")
        sandbox.shogunSucceeds(repository, "new", "TASK-1")
    }

    @AfterTest
    fun cleanUp() = sandbox.close()

    @Test
    fun `open in Herdr opens the worktree and runs the project's agent in it`() {
        sandbox.shogunSucceeds(repository, "open", "TASK-1", "--herdr")

        val calls = FakeRunners.calls(sandbox, "herdr")
        val open = calls.single { it.take(2) == listOf("worktree", "open") }
        assertEquals(repository, open.pathAfter("--cwd"))
        assertEquals(worktree, open.pathAfter("--path"))
        val run = calls.single { it.take(2) == listOf("pane", "run") }
        assertTrue(run[3].startsWith("claude"), run.toString())
    }

    @Test
    fun `open in Herdr twice focuses the agent already running`() {
        sandbox.shogunSucceeds(repository, "open", "TASK-1", "--herdr")

        sandbox.shogunSucceeds(repository, "open", "TASK-1", "--herdr")

        val calls = FakeRunners.calls(sandbox, "herdr")
        assertEquals(1, calls.count { it.take(2) == listOf("pane", "run") }, calls.toString())
        assertEquals(1, calls.count { it.take(2) == listOf("agent", "focus") }, calls.toString())
    }

    @Test
    fun `open in Orca adds the repository and creates the agent's terminal in the worktree`() {
        sandbox.shogunSucceeds(repository, "open", "TASK-1", "--orca")

        val calls = FakeRunners.calls(sandbox, "orca")
        assertEquals(repository, calls.single { it.take(2) == listOf("repo", "add") }.pathAfter("--path"))
        val terminal = calls.single { it.take(2) == listOf("terminal", "create") }
        assertEquals(worktree, Path.of(terminal.after("--worktree").removePrefix("path:")).toRealPath())
        assertTrue(terminal.after("--command").contains("claude"), terminal.toString())
    }

    private fun List<String>.after(option: String): String = this[indexOf(option) + 1]

    private fun List<String>.pathAfter(option: String): Path = Path.of(after(option)).toRealPath()
}
