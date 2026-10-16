package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HookCommandsTest {

    private val base = "/home/dev/projects/main-repo"
    private val worktreesRoot = "/home/dev/projects/worktrees"
    private val project = Project(
        id = "p",
        name = "Main repo",
        config = ProjectConfig(baseRepositoryPath = base, worktreesRoot = worktreesRoot, secretFiles = emptyList()),
    )

    private class Executor(
        private val worktreeList: String,
        private val respond: (List<String>) -> CommandResult? = { null },
    ) : ShellCommandExecutor {
        val calls = mutableListOf<Pair<List<String>, String?>>()

        override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult {
            calls += command to workingDirectory
            respond(command)?.let { return it }
            return when {
                "--git-common-dir" in command -> CommandResult(command, 0, "/home/dev/projects/main-repo/.git\n", "")
                command.take(3) == listOf("git", "worktree", "list") -> CommandResult(command, 0, worktreeList, "")
                command.getOrNull(1) == "ls-files" -> CommandResult(command, 0, ".env\u0000", "")
                else -> CommandResult(command, 0, "", "")
            }
        }

        fun ran(vararg args: String) = calls.any { it.first == args.toList() }
    }

    private fun porcelain(vararg linked: Pair<String, String>) = buildString {
        append("worktree $base\nHEAD 1111111111111111111111111111111111111111\nbranch refs/heads/main\n")
        linked.forEach { (path, branch) ->
            append("\nworktree $path\nHEAD 2222222222222222222222222222222222222222\nbranch refs/heads/$branch\n")
        }
    }

    private val out = StringBuilder()
    private val err = StringBuilder()

    private fun cli(
        executor: ShellCommandExecutor,
        input: String,
        projects: List<Project> = listOf(project),
        existing: List<String> = listOf(base),
    ) = ShogunCli(
        executor = executor,
        fileManager = FakeFileManager(existing = existing),
        projectRepository = FakeProjectRepository(projects),
        provisioningSource = FakeRepositoryProvisioningSource(
            byRepository = mapOf(base to RepositoryProvisioning(worktreeIncludePatterns = listOf(".env"))),
        ),
        daemonSource = FakeGradleDaemonSource(),
        gradleUserHome = "/home/dev/.gradle",
        workingDirectory = "/",
        out = out,
        err = err,
        readInput = { input },
        canonicalPath = { it.trimEnd('/') },
    )

    private fun createInput(name: String, cwd: String = base) =
        """{"session_id":"abc123","transcript_path":"/tmp/t.jsonl","cwd":"$cwd","hook_event_name":"WorktreeCreate","name":"$name"}"""

    @Test
    fun `worktree-create creates a provisioned worktree in the project's folder and prints its path`() {
        val executor = Executor(porcelain())

        val exitCode = cli(executor, createInput("TASK-123", cwd = "$base/src")).run(listOf("hook", "worktree-create"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("$worktreesRoot/TASK-123\n", out.toString())
        assertTrue(executor.ran("git", "worktree", "add", "$worktreesRoot/TASK-123", "-b", "feature/TASK-123"))
        assertTrue(executor.calls.any { it.first.getOrNull(1) == "rev-parse" && it.second == "$base/src" }, "resolves the repository from cwd")
    }

    @Test
    fun `worktree-create without a project uses Claude Code's folder`() {
        val executor = Executor(porcelain())

        val exitCode = cli(executor, createInput("bold-oak-a3f2"), projects = emptyList()).run(listOf("hook", "worktree-create"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("$base/.claude/worktrees/bold-oak-a3f2\n", out.toString())
    }

    @Test
    fun `worktree-create reuses an existing worktree with the same name`() {
        val executor = Executor(porcelain("$worktreesRoot/TASK-123" to "feature/TASK-123"))

        val exitCode = cli(executor, createInput("TASK-123")).run(listOf("hook", "worktree-create"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("$worktreesRoot/TASK-123\n", out.toString())
        assertFalse(executor.calls.any { it.first.take(3) == listOf("git", "worktree", "add") })
    }

    @Test
    fun `worktree-create rejects input that isn't the hook's JSON`() {
        val executor = Executor(porcelain())

        assertEquals(2, cli(executor, "not json").run(listOf("hook", "worktree-create")))
        assertTrue(err.contains("expected the hook's JSON"), err.toString())
        err.clear()

        assertEquals(2, cli(executor, """{"cwd":"$base"}""").run(listOf("hook", "worktree-create")))
        assertTrue(err.contains("no \"name\""), err.toString())
        assertTrue(executor.calls.isEmpty())
    }

    @Test
    fun `worktree-create failure leaves standard output empty and exits with 1`() {
        val executor = Executor(porcelain()) { command ->
            if (command.take(3) == listOf("git", "worktree", "add")) CommandResult(command, 128, "", "fatal: cannot lock ref") else null
        }

        val exitCode = cli(executor, createInput("TASK-123")).run(listOf("hook", "worktree-create"))

        assertEquals(1, exitCode)
        assertEquals("", out.toString())
        assertTrue(err.contains("cannot lock ref"), err.toString())
    }

    @Test
    fun `worktree-remove removes the worktree and its merged branch`() {
        val path = "$worktreesRoot/TASK-123"
        val executor = Executor(porcelain(path to "feature/TASK-123"))

        val exitCode = cli(executor, """{"cwd":"$base","worktree_path":"$path"}""", existing = listOf(base, path))
            .run(listOf("hook", "worktree-remove"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(executor.ran("git", "worktree", "remove", "--force", path))
        assertTrue(executor.ran("git", "branch", "-d", "feature/TASK-123"))
        assertEquals("", err.toString())
    }

    @Test
    fun `worktree-remove keeps a branch that isn't merged and still succeeds`() {
        val path = "$worktreesRoot/TASK-123"
        val executor = Executor(porcelain(path to "feature/TASK-123")) { command ->
            if (command.take(3) == listOf("git", "branch", "-d")) CommandResult(command, 1, "", "not fully merged") else null
        }

        val exitCode = cli(executor, """{"worktree_path":"$path"}""", existing = listOf(base, path)).run(listOf("hook", "worktree-remove"))

        assertEquals(0, exitCode)
        assertEquals("shogun: kept branch feature/TASK-123 because it isn't merged\n", err.toString())
    }

    @Test
    fun `worktree-remove refuses the main worktree`() {
        val executor = Executor(porcelain())

        val exitCode = cli(executor, """{"worktree_path":"$base"}""").run(listOf("hook", "worktree-remove"))

        assertEquals(1, exitCode)
        assertFalse(executor.calls.any { it.first.take(3) == listOf("git", "worktree", "remove") })
    }
}
