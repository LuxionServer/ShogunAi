package app.luxion.shogunai.cli

import app.luxion.shogunai.BuildInfo
import app.luxion.shogunai.domain.FakeFileManager
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

class ShogunCliTest {

    private val base = "/home/dev/projects/main-repo"
    private val worktreesRoot = "/home/dev/projects/worktrees"
    private val merged = "$worktreesRoot/TASK-1"
    private val dirty = "$base/.claude/worktrees/spike"
    private val project = Project(
        id = "p",
        name = "Main repo",
        config = ProjectConfig(baseRepositoryPath = base, worktreesRoot = worktreesRoot, secretFiles = listOf("local.properties")),
    )

    private val porcelain = """
        worktree $base
        HEAD 1111111111111111111111111111111111111111
        branch refs/heads/main

        worktree $merged
        HEAD 2222222222222222222222222222222222222222
        branch refs/heads/feature/TASK-1

        worktree $dirty
        HEAD 3333333333333333333333333333333333333333
        branch refs/heads/worktree-spike
        locked claude session
    """.trimIndent()

    private class Executor(private val respond: (List<String>, String?) -> CommandResult?) : ShellCommandExecutor {
        val calls = mutableListOf<Pair<List<String>, String?>>()

        override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult {
            calls += command to workingDirectory
            return respond(command, workingDirectory) ?: ok(command)
        }

        fun ran(vararg args: String) = calls.any { it.first == args.toList() }

        companion object {
            fun ok(command: List<String>, stdout: String = "") = CommandResult(command, 0, stdout, "")
            fun fail(command: List<String>, stderr: String = "boom", exitCode: Int = 1) = CommandResult(command, exitCode, "", stderr)
        }
    }

    private fun repositoryExecutor(
        extra: (List<String>, String?) -> CommandResult? = { _, _ -> null },
    ) = Executor { command, directory ->
        extra(command, directory) ?: when {
            command.containsAll(listOf("rev-parse", "--git-common-dir")) -> Executor.ok(command, "$base/.git\n")
            command.take(3) == listOf("git", "worktree", "list") -> Executor.ok(command, porcelain)
            command.take(2) == listOf("git", "symbolic-ref") -> Executor.fail(command)
            command.take(2) == listOf("git", "rev-parse") && command.last() == "refs/heads/main" -> Executor.ok(command)
            command.take(2) == listOf("git", "rev-parse") -> Executor.fail(command)
            "status" in command && directory == dirty -> Executor.ok(command, " M a.kt\n M b.kt\n")
            "rev-list" in command && command.last() == "@{upstream}...HEAD" -> Executor.fail(command)
            "rev-list" in command && directory == merged -> Executor.ok(command, "3\t0")
            "rev-list" in command -> Executor.ok(command, "0\t2")
            else -> null
        }
    }

    private val out = StringBuilder()
    private val err = StringBuilder()

    private fun cli(
        executor: ShellCommandExecutor,
        projects: List<Project> = listOf(project),
        fileManager: FakeFileManager = FakeFileManager(existing = listOf(base, "$base/local.properties")),
        provisioning: FakeRepositoryProvisioningSource = FakeRepositoryProvisioningSource(),
        workingDirectory: String = base,
        canonicalPath: (String) -> String = { it.trimEnd('/') },
    ) = ShogunCli(
        executor = executor,
        fileManager = fileManager,
        projectRepository = FakeProjectRepository(projects),
        provisioningSource = provisioning,
        workingDirectory = workingDirectory,
        out = out,
        err = err,
        canonicalPath = canonicalPath,
    )

    @Test
    fun `new prints only the worktree path and uses the project's folder`() {
        val executor = repositoryExecutor()

        val exitCode = cli(executor).run(listOf("new", "TASK-123", "--fix"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("$worktreesRoot/TASK-123\n", out.toString())
        assertTrue(executor.ran("git", "worktree", "add", "$worktreesRoot/TASK-123", "-b", "fix/TASK-123"))
    }

    @Test
    fun `new from an existing branch`() {
        val executor = repositoryExecutor { command, _ ->
            if (command.take(3) == listOf("git", "branch", "--list")) Executor.ok(command, "  hotfix/login\n") else null
        }

        val exitCode = cli(executor).run(listOf("new", "--branch", "hotfix/login"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("$worktreesRoot/hotfix-login\n", out.toString())
    }

    @Test
    fun `new without a project needs a worktrees root`() {
        val executor = repositoryExecutor()

        val exitCode = cli(executor, projects = emptyList()).run(listOf("new", "TASK-123"))

        assertEquals(2, exitCode)
        assertTrue(err.contains("--worktrees-root"))
        assertFalse(executor.calls.any { "add" in it.first })
    }

    @Test
    fun `a relative worktrees root is resolved against the current directory`() {
        val executor = repositoryExecutor()

        val exitCode = cli(executor, projects = emptyList()).run(listOf("new", "TASK-123", "--worktrees-root", "../wt"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("/home/dev/projects/wt/TASK-123\n", out.toString())
    }

    @Test
    fun `the project is found by canonical path`() {
        val aliased = project.copy(config = project.config.copy(baseRepositoryPath = "/link/main-repo"))
        val executor = repositoryExecutor()
        val canonical = { path: String -> if (path == "/link/main-repo") base else path }

        val fileManager = FakeFileManager(existing = listOf("/link/main-repo", "/link/main-repo/local.properties"))

        val exitCode = cli(executor, projects = listOf(aliased), fileManager = fileManager, canonicalPath = canonical)
            .run(listOf("new", "TASK-9"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("$worktreesRoot/TASK-9\n", out.toString())
    }

    @Test
    fun `a failed creation reports the error and exits with 1`() {
        val executor = repositoryExecutor()
        val fileManager = FakeFileManager(existing = listOf(base, "$base/local.properties", "$worktreesRoot/TASK-1"))

        val exitCode = cli(executor, fileManager = fileManager).run(listOf("new", "TASK-1"))

        assertEquals(1, exitCode)
        assertTrue(err.startsWith("shogun: Worktree directory already exists"), err.toString())
        assertEquals("", out.toString())
    }

    @Test
    fun `outside a repository every command fails with 1`() {
        val executor = Executor { command, _ -> Executor.fail(command, "fatal: not a git repository", 128) }

        val exitCode = cli(executor, workingDirectory = "/home/dev").run(listOf("status"))

        assertEquals(1, exitCode)
        assertEquals("shogun: No Git repository found at /home/dev\n", err.toString())
    }

    @Test
    fun `provision copies into the worktree containing the given path and lists the files`() {
        val executor = repositoryExecutor { command, _ ->
            if ("ls-files" in command) Executor.ok(command, ".env\u0000") else null
        }
        val provisioning = FakeRepositoryProvisioningSource(byRepository = mapOf(base to RepositoryProvisioning(copyPatterns = listOf(".env"))))

        val exitCode = cli(executor, provisioning = provisioning, workingDirectory = worktreesRoot).run(listOf("provision", "TASK-1"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("local.properties\n.env\n", out.toString())
    }

    @Test
    fun `provision refuses a directory that is not a linked worktree`() {
        val exitCode = cli(repositoryExecutor()).run(listOf("provision"))

        assertEquals(1, exitCode)
        assertTrue(err.contains("is not a linked worktree"), err.toString())
    }

    @Test
    fun `status prints one line per worktree`() {
        val exitCode = cli(repositoryExecutor()).run(listOf("status"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(
            listOf(
                "$base  main  main  changes=0",
                "$merged  feature/TASK-1  origin=shogunai  changes=0  merged",
                "$dirty  worktree-spike  origin=claude-code  locked  changes=2",
            ),
            out.lines().filter { it.isNotEmpty() },
        )
    }

    @Test
    fun `clean without yes only lists the candidates`() {
        val executor = repositoryExecutor()

        val exitCode = cli(executor).run(listOf("clean"))

        assertEquals(0, exitCode)
        assertEquals("would remove $merged\nRun 'shogun clean --yes' to remove it.\n", out.toString())
        assertFalse(executor.calls.any { it.first.take(3) == listOf("git", "worktree", "remove") })
    }

    @Test
    fun `clean with yes removes and deletes merged branches`() {
        val executor = repositoryExecutor()

        val exitCode = cli(executor).run(listOf("clean", "--yes", "--delete-branches"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(executor.ran("git", "worktree", "remove", merged))
        assertTrue(out.startsWith("removed $merged\n"), out.toString())
    }

    @Test
    fun `clean reports failures and exits with 1`() {
        val executor = repositoryExecutor { command, _ ->
            if (command.take(3) == listOf("git", "worktree", "remove")) Executor.fail(command, "is dirty") else null
        }

        val exitCode = cli(executor).run(listOf("clean", "--yes"))

        assertEquals(1, exitCode)
        assertTrue(err.contains("couldn't remove $merged"), err.toString())
    }

    @Test
    fun `clean with nothing to remove`() {
        val executor = repositoryExecutor { command, directory ->
            if ("rev-list" in command && directory == merged) Executor.ok(command, "0\t1") else null
        }

        val exitCode = cli(executor).run(listOf("clean"))

        assertEquals(0, exitCode)
        assertEquals("Nothing to clean up.\n", out.toString())
    }

    @Test
    fun `help and version`() {
        assertEquals(0, cli(repositoryExecutor()).run(listOf("help")))
        assertTrue(out.startsWith("Usage: shogun <command>"))
        out.clear()

        assertEquals(0, cli(repositoryExecutor()).run(listOf("--version")))
        assertEquals("shogun ${BuildInfo.VERSION}\n", out.toString())
    }

    @Test
    fun `usage errors exit with 2 and point to help`() {
        val exitCode = cli(repositoryExecutor()).run(listOf("frobnicate"))

        assertEquals(2, exitCode)
        assertEquals("shogun: unknown command 'frobnicate'\nRun 'shogun help' for usage.\n", err.toString())
    }
}
