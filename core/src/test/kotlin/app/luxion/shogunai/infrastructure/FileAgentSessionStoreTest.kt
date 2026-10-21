package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.AgentSessionRecord
import app.luxion.shogunai.domain.model.AgentState
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileAgentSessionStoreTest {

    private val root: Path = Files.createTempDirectory("agent-sessions")
    private val main: Path = root.resolve("main-repo")
    private val linked: Path = root.resolve("TASK-1")
    private val store = FileAgentSessionStore()

    init {
        Files.createDirectories(main)
        git(main, "init", "-q", "-b", "main")
        git(main, "-c", "user.name=Dev", "-c", "user.email=dev@example.com", "commit", "-q", "--allow-empty", "-m", "initial")
        git(main, "worktree", "add", "-q", linked.toString(), "-b", "feature/TASK-1")
    }

    @AfterTest
    fun cleanUp() {
        root.toFile().deleteRecursively()
    }

    private fun git(directory: Path, vararg args: String): String {
        val process = ProcessBuilder(listOf("git") + args).directory(directory.toFile()).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "git ${args.joinToString(" ")} failed: $output" }
        return output
    }

    private fun record(id: String, state: AgentState = AgentState.WORKING, pid: Long? = 4242) =
        AgentSessionRecord(id, "claude", state, 1_000L, "/home/dev/.claude/projects/t.jsonl", pid)

    @Test
    fun `writes and lists records of a linked worktree under its own git directory`() {
        store.write(linked.toString(), record("s1", AgentState.BLOCKED))
        store.write(linked.toString(), record("s2"))

        assertEquals(
            setOf(record("s1", AgentState.BLOCKED), record("s2")),
            store.list(linked.toString()).toSet(),
        )
        val gitDirectory = Path.of(git(linked, "rev-parse", "--absolute-git-dir").trim())
        assertTrue(gitDirectory.resolve("shogunai/agent-sessions/s1.json").exists())
    }

    @Test
    fun `writes and lists records of the main worktree`() {
        store.write(main.toString(), record("s1"))

        assertEquals(listOf(record("s1")), store.list(main.toString()))
        assertTrue(main.resolve(".git/shogunai/agent-sessions").isDirectory())
        assertEquals(emptyList(), store.list(linked.toString()))
    }

    @Test
    fun `writing again replaces the record of the session`() {
        store.write(main.toString(), record("s1", AgentState.WORKING))
        store.write(main.toString(), record("s1", AgentState.DONE, pid = null))

        assertEquals(listOf(record("s1", AgentState.DONE, pid = null)), store.list(main.toString()))
    }

    @Test
    fun `removes the record of a session`() {
        store.write(main.toString(), record("s1"))
        store.write(main.toString(), record("s2"))

        store.remove(main.toString(), "s1")
        store.remove(main.toString(), "unknown")

        assertEquals(listOf(record("s2")), store.list(main.toString()))
    }

    @Test
    fun `session records stay out of the working tree`() {
        store.write(linked.toString(), record("s1"))
        store.write(main.toString(), record("s2"))

        assertEquals("", git(linked, "status", "--porcelain"))
        assertEquals("", git(main, "status", "--porcelain"))
    }

    @Test
    fun `ignores unreadable records and directories that are not worktrees`() {
        store.write(main.toString(), record("s1"))
        main.resolve(".git/shogunai/agent-sessions/broken.json").toFile().writeText("{")

        assertEquals(listOf(record("s1")), store.list(main.toString()))
        assertEquals(emptyList(), store.list(root.resolve("elsewhere").toString()))
        store.write(root.resolve("elsewhere").toString(), record("s3"))
    }
}
