package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.FakeAgentSessionStore
import app.luxion.shogunai.domain.FakeMachineCapacity
import app.luxion.shogunai.domain.FakeResourceLimitsRepository
import app.luxion.shogunai.domain.model.RepositoryApprovalSubject
import app.luxion.shogunai.domain.model.CapacityProfile
import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeDesktopTurnStore
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeHerdrClient
import app.luxion.shogunai.domain.FakeHerdrConfigSource
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakeKmpTestWiring
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryTrust
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.AgentSessionRecord
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.DesktopTurn
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.parseEnvironmentVariable
import app.luxion.shogunai.domain.model.EnvironmentDeclaration
import app.luxion.shogunai.domain.model.WorktreeError
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HookCommandsTest {

    private val resourceLimits = FakeResourceLimitsRepository(ResourceSettings(CapacityProfile.PER_WORKTREE))

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
                command.last() == "HEAD^{commit}" -> CommandResult(command, 0, "abc123\n", "")
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

    private val androidSdk = FakeAndroidSdk()
    private val appleSimulators = FakeAppleSimulators()
    private val kmpTestWiring = FakeKmpTestWiring()
    private val out = StringBuilder()
    private val err = StringBuilder()

    private val environmentStore = FakeEnvironmentStateStore()
    private val desktopTurnStore = FakeDesktopTurnStore()

    private fun cli(
        executor: ShellCommandExecutor,
        input: String,
        projects: List<Project> = listOf(project),
        existing: List<String> = listOf(base),
        environment: Map<String, String> = emptyMap(),
        provisioning: RepositoryProvisioning = RepositoryProvisioning(worktreeIncludePatterns = listOf(".env")),
        herdr: FakeHerdrClient = FakeHerdrClient(),
        trust: FakeRepositoryTrust = FakeRepositoryTrust(),
        sessionStore: FakeAgentSessionStore = FakeAgentSessionStore(),
    ) = ShogunCli(
        executor = executor,
        fileManager = FakeFileManager(existing = existing),
        projectRepository = FakeProjectRepository(projects),
        provisioningSource = FakeRepositoryProvisioningSource(
            byRepository = mapOf(base to provisioning),
        ),
        daemonSource = FakeGradleDaemonSource(),
        gradleUserHome = "/home/dev/.gradle",
        environmentStateStore = environmentStore,
        portProbe = FakePortProbe(),
        androidSdk = androidSdk,
        appleSimulators = appleSimulators,
        kmpTestWiring = kmpTestWiring,
        resourceLimits = resourceLimits,
        machineCapacity = FakeMachineCapacity(),
        derivedDataSource = FakeDerivedDataSource(),
        herdrConfig = FakeHerdrConfigSource(),
        workingDirectory = "/",
        out = out,
        err = err,
        readInput = { input },
        canonicalPath = { it.trimEnd('/') },
        environmentVariable = { environment[it] },
        userHome = "/home/dev",
        herdr = herdr,
        trust = trust,
        readAnswer = { "y" },
        sessionStore = sessionStore,
        agentProcessId = { 4242L },
        desktopTurnStore = desktopTurnStore,
    )

    private fun createInput(name: String, cwd: String = base) =
        """{"session_id":"abc123","transcript_path":"/tmp/t.jsonl","cwd":"$cwd","hook_event_name":"WorktreeCreate","name":"$name"}"""

    @Test
    fun `worktree-create creates a provisioned worktree in the project's folder and prints its path`() {
        val executor = Executor(porcelain())

        val exitCode = cli(executor, createInput("TASK-123", cwd = "$base/src")).run(listOf("hook", "worktree-create"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("$worktreesRoot/TASK-123\n", out.toString())
        assertTrue(executor.ran("git", "worktree", "add", "--no-track", "$worktreesRoot/TASK-123", "-b", "feature/TASK-123", "abc123"))
        assertTrue(executor.calls.any { it.first.getOrNull(1) == "rev-parse" && it.second == "$base/src" }, "resolves the repository from cwd")
    }

    @Test
    fun `worktree-create keeps the worktree and exits with 0 when a post-creation command fails`() {
        val executor = Executor(porcelain()) { command ->
            if (command.first() == "npm") CommandResult(command, 1, "", "ENOTFOUND") else null
        }
        val provisioning = RepositoryProvisioning(postCreateCommands = listOf(listOf("npm", "install")))

        val trust = FakeRepositoryTrust(mapOf(base to RepositoryApprovalSubject(listOf(listOf("npm", "install")))))

        val exitCode = cli(executor, createInput("TASK-123"), provisioning = provisioning, trust = trust).run(listOf("hook", "worktree-create"))

        assertEquals(0, exitCode)
        assertEquals("$worktreesRoot/TASK-123\n", out.toString())
        assertTrue(err.contains("npm install") && err.contains("ENOTFOUND"), err.toString())
    }

    @Test
    fun `worktree-create skips unapproved commands without asking and says how to approve them`() {
        val executor = Executor(porcelain())
        val provisioning = RepositoryProvisioning(postCreateCommands = listOf(listOf("npm", "install")))

        val exitCode = cli(executor, createInput("TASK-123"), provisioning = provisioning).run(listOf("hook", "worktree-create"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("$worktreesRoot/TASK-123\n", out.toString())
        assertFalse(executor.calls.any { it.first.first() == "npm" })
        assertTrue(err.contains("shogun trust"), err.toString())
    }

    @Test
    fun `worktree-create leaves out unapproved variables and the environment file and says how to approve them`() {
        val executor = Executor(porcelain())
        val provisioning = RepositoryProvisioning(
            environment = EnvironmentDeclaration(listOf(parseEnvironmentVariable("PORT", "\${port}")), ".env.shogunai"),
        )

        val exitCode = cli(executor, createInput("TASK-123"), provisioning = provisioning).run(listOf("hook", "worktree-create"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("$worktreesRoot/TASK-123\n", out.toString())
        assertFalse(executor.calls.any { "--error-unmatch" in it.first }, "doesn't look at the environment file")
        assertTrue(err.contains("(PORT)") && err.contains("shogun trust"), err.toString())
    }

    @Test
    fun `worktree-create from a session in a linked worktree starts at that worktree's HEAD`() {
        val linked = "/home/dev/projects/epic-redesign"
        val executor = Executor(porcelain(linked to "epic/redesign")) { command ->
            if (command == listOf("git", "rev-parse", "--verify", "--quiet", "HEAD^{commit}")) {
                CommandResult(command, 0, "epic1234\n", "")
            } else {
                null
            }
        }

        val exitCode = cli(executor, createInput("TASK-123", cwd = linked)).run(listOf("hook", "worktree-create"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(executor.ran("git", "worktree", "add", "--no-track", "$worktreesRoot/TASK-123", "-b", "feature/TASK-123", "epic1234"))
        assertTrue(executor.calls.any { it.first.getOrNull(2) == "--verify" && it.second == linked })
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
        assertTrue(executor.ran("git", "branch", "-D", "--", "feature/TASK-123"))
        assertEquals("", err.toString())
    }

    @Test
    fun `worktree-remove keeps a branch that isn't merged and still succeeds`() {
        val path = "$worktreesRoot/TASK-123"
        val executor = Executor(porcelain(path to "feature/TASK-123")) { command ->
            if (command.take(3) == listOf("git", "merge-base", "--is-ancestor")) CommandResult(command, 1, "", "") else null
        }

        val exitCode = cli(executor, """{"worktree_path":"$path"}""", existing = listOf(base, path)).run(listOf("hook", "worktree-remove"))

        assertEquals(0, exitCode)
        assertEquals("shogun: kept branch feature/TASK-123 because it isn't merged into the main branch\n", err.toString())
    }

    @Test
    fun `worktree-remove keeps a branch merged only into its upstream`() {
        val path = "$worktreesRoot/TASK-123"
        val executor = Executor(porcelain(path to "feature/TASK-123")) { command ->
            when {
                command.take(3) == listOf("git", "merge-base", "--is-ancestor") && command.last() == "main" ->
                    CommandResult(command, 1, "", "")
                else -> null
            }
        }

        val exitCode = cli(executor, """{"worktree_path":"$path"}""", existing = listOf(base, path)).run(listOf("hook", "worktree-remove"))

        assertEquals(0, exitCode)
        assertTrue(executor.ran("git", "merge-base", "--is-ancestor", "feature/TASK-123", "main"))
        assertTrue(executor.branchDeletions().isEmpty())
        assertEquals("shogun: kept branch feature/TASK-123 because it isn't merged into the main branch\n", err.toString())
    }

    @Test
    fun `worktree-remove closes the Herdr workspace of the removed worktree`() {
        val path = "$worktreesRoot/TASK-123"
        val herdr = FakeHerdrClient(openWorkspaces = mapOf(path to "w2"))

        val exitCode = cli(Executor(porcelain(path to "feature/TASK-123")), """{"worktree_path":"$path"}""", existing = listOf(base, path), herdr = herdr)
            .run(listOf("hook", "worktree-remove"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(listOf("w2"), herdr.closedWorkspaces)
        assertEquals("", err.toString())
    }

    @Test
    fun `worktree-remove works when Herdr isn't available`() {
        val path = "$worktreesRoot/TASK-123"
        val herdr = FakeHerdrClient(listFailure = WorktreeError.HerdrNotInstalled)

        val exitCode = cli(Executor(porcelain(path to "feature/TASK-123")), """{"worktree_path":"$path"}""", existing = listOf(base, path), herdr = herdr)
            .run(listOf("hook", "worktree-remove"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("", err.toString())
    }

    @Test
    fun `worktree-remove reports a failing Herdr close without changing the exit code`() {
        val path = "$worktreesRoot/TASK-123"
        val failure = WorktreeError.HerdrCommandFailed(listOf("herdr"), "workspace w2 not found")
        val herdr = FakeHerdrClient(openWorkspaces = mapOf(path to "w2"), closeFailures = mapOf("w2" to failure))

        val exitCode = cli(Executor(porcelain(path to "feature/TASK-123")), """{"worktree_path":"$path"}""", existing = listOf(base, path), herdr = herdr)
            .run(listOf("hook", "worktree-remove"))

        assertEquals(0, exitCode)
        assertEquals("shogun: couldn't close the Herdr workspace of $path: workspace w2 not found\n", err.toString())
    }

    @Test
    fun `worktree-remove refuses the main worktree`() {
        val executor = Executor(porcelain())

        val exitCode = cli(executor, """{"worktree_path":"$base"}""").run(listOf("hook", "worktree-remove"))

        assertEquals(1, exitCode)
        assertFalse(executor.calls.any { it.first.take(3) == listOf("git", "worktree", "remove") })
    }

    private val herdrWorktree = "/home/dev/.herdr/worktrees/main-repo/feature-task-7"

    private fun herdrEvent(path: String = herdrWorktree) =
        """{"event":"worktree.created","data":{"type":"worktree_created","workspace":{"workspace_id":"w_2","label":"feature-task-7"},""" +
            """"worktree":{"path":"$path","branch":"feature/TASK-7","is_bare":false,"is_detached":false,"is_prunable":false,"is_linked_worktree":true,"label":"feature-task-7"}}}"""

    private fun herdrEnvironment(event: String = "worktree.created", json: String? = herdrEvent()) =
        listOfNotNull("HERDR_PLUGIN_EVENT" to event, json?.let { "HERDR_PLUGIN_EVENT_JSON" to it }).toMap()

    private fun Executor.notifications() = calls.map { it.first }.filter { it.getOrNull(1) == "notification" }

    @Test
    fun `herdr provisions the worktree of a worktree created event`() {
        val executor = Executor(porcelain(herdrWorktree to "feature/TASK-7"))

        val exitCode = cli(executor, "", environment = herdrEnvironment()).run(listOf("hook", "herdr"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(".env\n", out.toString())
        assertTrue(executor.calls.any { it.first.getOrNull(1) == "rev-parse" && it.second == herdrWorktree }, "resolves the repository from the worktree")
    }

    @Test
    fun `herdr skips unapproved commands without asking and says how to approve them`() {
        val executor = Executor(porcelain(herdrWorktree to "feature/TASK-7"))
        val provisioning = RepositoryProvisioning(
            worktreeIncludePatterns = listOf(".env"),
            postCreateCommands = listOf(listOf("npm", "install")),
        )

        val exitCode = cli(executor, "", environment = herdrEnvironment(), provisioning = provisioning).run(listOf("hook", "herdr"))

        assertEquals(0, exitCode, err.toString())
        assertFalse(executor.calls.any { it.first.first() == "npm" })
        assertTrue(err.contains("shogun trust"), err.toString())
    }

    @Test
    fun `herdr runs approved commands`() {
        val executor = Executor(porcelain(herdrWorktree to "feature/TASK-7"))
        val provisioning = RepositoryProvisioning(
            worktreeIncludePatterns = listOf(".env"),
            postCreateCommands = listOf(listOf("npm", "install")),
        )
        val trust = FakeRepositoryTrust(mapOf(base to RepositoryApprovalSubject(listOf(listOf("npm", "install")))))

        val exitCode = cli(executor, "", environment = herdrEnvironment(), provisioning = provisioning, trust = trust)
            .run(listOf("hook", "herdr"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(executor.calls.any { it.first == listOf("npm", "install") })
    }

    @Test
    fun `herdr copies nothing for a repository without ShogunAi configuration`() {
        val executor = Executor(porcelain(herdrWorktree to "feature/TASK-7")) { command ->
            if (command.getOrNull(1) == "ls-files") CommandResult(command, 0, "", "") else null
        }

        val exitCode = cli(executor, "", projects = emptyList(), environment = herdrEnvironment(), provisioning = RepositoryProvisioning())
            .run(listOf("hook", "herdr"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("", out.toString())
        assertTrue(executor.notifications().isEmpty())
    }

    @Test
    fun `herdr notifies through Herdr when the worktree is outside the project's worktrees folder`() {
        val executor = Executor(porcelain(herdrWorktree to "feature/TASK-7"))

        val exitCode = cli(executor, "", environment = herdrEnvironment() + ("HERDR_BIN_PATH" to "/opt/herdr/bin/herdr"))
            .run(listOf("hook", "herdr"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(
            listOf(
                listOf(
                    "/opt/herdr/bin/herdr", "notification", "show", "ShogunAi",
                    "--body", "feature-task-7 is outside ~/projects/worktrees; create worktrees from ShogunAi",
                ),
            ),
            executor.notifications(),
        )
    }

    @Test
    fun `herdr notifies with the herdr command when HERDR_BIN_PATH isn't set`() {
        val executor = Executor(porcelain(herdrWorktree to "feature/TASK-7"))

        cli(executor, "", environment = herdrEnvironment()).run(listOf("hook", "herdr"))

        assertEquals("herdr", executor.notifications().single().first())
    }

    @Test
    fun `herdr doesn't notify for a worktree inside the project's worktrees folder`() {
        val path = "$worktreesRoot/TASK-7"
        val executor = Executor(porcelain(path to "feature/TASK-7"))

        val exitCode = cli(executor, "", environment = herdrEnvironment(json = herdrEvent(path))).run(listOf("hook", "herdr"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(executor.notifications().isEmpty())
    }

    @Test
    fun `herdr notes a failing notification and still succeeds`() {
        val executor = Executor(porcelain(herdrWorktree to "feature/TASK-7")) { command ->
            if (command.getOrNull(1) == "notification") CommandResult(command, 1, "", "server_not_running") else null
        }

        val exitCode = cli(executor, "", environment = herdrEnvironment()).run(listOf("hook", "herdr"))

        assertEquals(0, exitCode)
        assertEquals(".env\n", out.toString())
        assertTrue(err.contains("couldn't show Herdr's notification: server_not_running"), err.toString())
    }

    @Test
    fun `herdr notes a notification command that can't start and still succeeds`() {
        val executor = Executor(porcelain(herdrWorktree to "feature/TASK-7")) { command ->
            if (command.getOrNull(1) == "notification") throw CommandExecutionException(command, IOException("No such file")) else null
        }

        val exitCode = cli(executor, "", environment = herdrEnvironment()).run(listOf("hook", "herdr"))

        assertEquals(0, exitCode)
        assertTrue(err.contains("couldn't show Herdr's notification"), err.toString())
    }

    @Test
    fun `herdr ignores other events`() {
        val executor = Executor(porcelain())

        val exitCode = cli(executor, "", environment = herdrEnvironment(event = "worktree.removed")).run(listOf("hook", "herdr"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(executor.calls.isEmpty())
    }

    @Test
    fun `herdr rejects a missing or invalid event`() {
        val executor = Executor(porcelain())

        assertEquals(2, cli(executor, "").run(listOf("hook", "herdr")))
        assertTrue(err.contains("HERDR_PLUGIN_EVENT isn't set"), err.toString())
        err.clear()

        assertEquals(2, cli(executor, "", environment = herdrEnvironment(json = null)).run(listOf("hook", "herdr")))
        assertTrue(err.contains("HERDR_PLUGIN_EVENT_JSON isn't set"), err.toString())
        err.clear()

        assertEquals(2, cli(executor, "", environment = herdrEnvironment(json = "nope")).run(listOf("hook", "herdr")))
        assertTrue(err.contains("isn't JSON"), err.toString())
        err.clear()

        assertEquals(2, cli(executor, "", environment = herdrEnvironment(json = """{"event":"worktree.created","data":{}}""")).run(listOf("hook", "herdr")))
        assertTrue(err.contains("data.worktree.path"), err.toString())
        assertTrue(executor.calls.isEmpty())
    }

    @Test
    fun `herdr fails with 1 when the path isn't a worktree of its repository`() {
        val executor = Executor(porcelain())

        val exitCode = cli(executor, "", environment = herdrEnvironment()).run(listOf("hook", "herdr"))

        assertEquals(1, exitCode)
        assertTrue(err.contains("is not a linked worktree"), err.toString())
        assertEquals("", out.toString())
    }

    private val removedPath = "/home/dev/projects/worktrees/TASK-9"

    private fun removedEvent() = checkNotNull(javaClass.getResource("/herdr/worktree-removed-event.json")).readText().trim()

    private fun removedEnvironment(json: String? = removedEvent()) = herdrEnvironment(event = "worktree.removed", json = json)

    private fun Executor.branchDeletions() = calls.map { it.first }.filter { it.take(3) == listOf("git", "branch", "-D") }

    @Test
    fun `herdr deletes the merged branch of a removed worktree`() {
        val executor = Executor(porcelain())

        val exitCode = cli(executor, "", environment = removedEnvironment()).run(listOf("hook", "herdr"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(listOf(listOf("git", "branch", "-D", "--", "feature/TASK-9")), executor.branchDeletions())
        assertEquals(base, executor.calls.single { it.first.take(3) == listOf("git", "branch", "-D") }.second)
        assertEquals("", err.toString())
    }

    @Test
    fun `herdr keeps the branch of a removed worktree that isn't merged`() {
        val executor = Executor(porcelain()) { command ->
            if (command.take(3) == listOf("git", "merge-base", "--is-ancestor")) CommandResult(command, 1, "", "") else null
        }

        val exitCode = cli(executor, "", environment = removedEnvironment()).run(listOf("hook", "herdr"))

        assertEquals(0, exitCode)
        assertEquals("shogun: kept branch feature/TASK-9 because it isn't merged into the main branch\n", err.toString())
    }

    @Test
    fun `herdr leaves the branches of repositories without a ShogunAi project alone`() {
        val executor = Executor(porcelain())

        val exitCode = cli(executor, "", projects = emptyList(), environment = removedEnvironment()).run(listOf("hook", "herdr"))

        assertEquals(0, exitCode)
        assertTrue(executor.branchDeletions().isEmpty())
    }

    @Test
    fun `herdr does nothing for a removed detached worktree`() {
        val executor = Executor(porcelain())
        val detached = removedEvent().replace("\"branch\":\"feature/TASK-9\",", "")

        val exitCode = cli(executor, "", environment = removedEnvironment(detached)).run(listOf("hook", "herdr"))

        assertEquals(0, exitCode)
        assertTrue(executor.branchDeletions().isEmpty())
    }

    @Test
    fun `herdr finds the repository from the projects when the event has no workspace`() {
        val executor = Executor(porcelain())
        val withoutWorkspace = """{"event":"worktree_removed","data":{"type":"worktree_removed","worktree":{"path":"$removedPath","branch":"feature/TASK-9"}}}"""

        val exitCode = cli(executor, "", environment = removedEnvironment(withoutWorkspace)).run(listOf("hook", "herdr"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(listOf(listOf("git", "branch", "-D", "--", "feature/TASK-9")), executor.branchDeletions())
    }

    @Test
    fun `herdr notes when the repository of a removed worktree can't be found`() {
        val executor = Executor(porcelain())
        val elsewhere = """{"event":"worktree_removed","data":{"type":"worktree_removed","worktree":{"path":"/home/dev/elsewhere/TASK-9","branch":"feature/TASK-9"}}}"""

        val exitCode = cli(executor, "", environment = removedEnvironment(elsewhere)).run(listOf("hook", "herdr"))

        assertEquals(0, exitCode)
        assertTrue(err.contains("couldn't find the repository"), err.toString())
        assertTrue(executor.branchDeletions().isEmpty())
    }

    @Test
    fun `herdr fails with 2 when a removed event has no path`() {
        val executor = Executor(porcelain())

        val exitCode = cli(executor, "", environment = removedEnvironment("""{"event":"worktree_removed","data":{}}""")).run(listOf("hook", "herdr"))

        assertEquals(2, exitCode)
        assertTrue(err.contains("data.worktree.path"), err.toString())
    }

    @Test
    fun `herdr never calls Herdr when handling a removed worktree`() {
        val executor = Executor(porcelain())
        val herdr = FakeHerdrClient()

        cli(executor, "", environment = removedEnvironment(), herdr = herdr).run(listOf("hook", "herdr"))

        assertEquals(0, herdr.listings)
        assertTrue(executor.notifications().isEmpty())
    }

    private val transcript = "/home/dev/.claude/projects/main-repo/963d9441.jsonl"

    private fun stateInput(event: String, extra: String = "", cwd: String = base) =
        """{"session_id":"963d9441","transcript_path":"$transcript","cwd":"$cwd","prompt_id":"cf942c2e","hook_event_name":"$event"$extra}"""

    private fun runAgentState(input: String, store: FakeAgentSessionStore = FakeAgentSessionStore()): Pair<Int, FakeAgentSessionStore> {
        val executor = Executor(porcelain()) { command ->
            CommandResult(command, 0, "$base/.git\n$base/.git\n", "").takeIf { "--git-dir" in command }
        }
        val exitCode = cli(executor, input, sessionStore = store).run(listOf("hook", "agent-state"))
        return exitCode to store
    }

    private fun recorded(store: FakeAgentSessionStore) = store.records[base].orEmpty().singleOrNull()

    @Test
    fun `agent-state records working on UserPromptSubmit`() {
        val (exitCode, store) = runAgentState(stateInput("UserPromptSubmit", ""","permission_mode":"auto","prompt":"fix it""""))

        assertEquals(0, exitCode, err.toString())
        assertEquals("", out.toString())
        assertEquals(AgentSessionRecord("963d9441", "claude", AgentState.WORKING, recorded(store)!!.updatedAt, transcript, 4242L), recorded(store))
    }

    private fun holdEmulator() {
        environmentStore.writePorts("$base/.git", mapOf("emulator" to 5554))
    }

    @Test
    fun `agent-state renews the lease of the devices a worktree holds when the agent is working`() {
        holdEmulator()
        environmentStore.writeLastUsed("$base/.git", mapOf("emulator" to 5L))

        val (exitCode, _) = runAgentState(stateInput("UserPromptSubmit", ""","prompt":"fix it""""))

        assertEquals(0, exitCode, err.toString())
        assertTrue(environmentStore.readLeases("$base/.git").getValue("emulator") > 5L)
        assertEquals(5L, environmentStore.readLastUsed("$base/.git")["emulator"])
    }

    @Test
    fun `agent-state does not renew the lease when the agent stops or waits`() {
        holdEmulator()

        runAgentState(stateInput("Stop", ""","stop_hook_active":false"""))
        runAgentState(stateInput("Notification", ""","notification_type":"permission_prompt""""))

        assertTrue(environmentStore.readLeases("$base/.git").isEmpty())
    }

    @Test
    fun `agent-state releases the desktop turn when the holder's response or session ends`() {
        listOf(stateInput("Stop", ""","stop_hook_active":false"""), stateInput("SessionEnd", ""","reason":"exit"""")).forEach { input ->
            desktopTurnStore.turn = DesktopTurn("$base/.git", base, 0, Long.MAX_VALUE)

            val (exitCode, _) = runAgentState(input)

            assertEquals(0, exitCode, err.toString())
            assertEquals("", out.toString())
            assertNull(desktopTurnStore.turn)
        }
    }

    @Test
    fun `agent-state keeps the desktop turn on a permission prompt and for another worktree's session`() {
        val own = DesktopTurn("$base/.git", base, 0, Long.MAX_VALUE)
        desktopTurnStore.turn = own

        runAgentState(stateInput("Notification", ""","notification_type":"permission_prompt""""))
        assertEquals(own, desktopTurnStore.turn)

        val other = DesktopTurn("$base/.git/worktrees/TASK-2", "/home/dev/projects/worktrees/TASK-2", 0, Long.MAX_VALUE)
        desktopTurnStore.turn = other
        runAgentState(stateInput("Stop", ""","stop_hook_active":false"""))
        assertEquals(other, desktopTurnStore.turn)
    }

    @Test
    fun `agent-state acquires and records nothing for a worktree without devices`() {
        runAgentState(stateInput("UserPromptSubmit", ""","prompt":"fix it""""))

        assertTrue(environmentStore.portsByAdminDirectory.isEmpty())
        assertTrue(environmentStore.leasesByAdminDirectory.isEmpty())
    }

    @Test
    fun `agent-state records blocked on a permission prompt`() {
        val (exitCode, store) = runAgentState(
            stateInput("Notification", ""","message":"Claude needs your permission to use Bash","notification_type":"permission_prompt""""),
        )

        assertEquals(0, exitCode, err.toString())
        assertEquals("", out.toString())
        assertEquals(AgentState.BLOCKED, recorded(store)?.state)
    }

    @Test
    fun `agent-state records blocked on a notification without a type`() {
        val (_, store) = runAgentState(stateInput("Notification", ""","message":"Claude needs your attention""""))

        assertEquals(AgentState.BLOCKED, recorded(store)?.state)
    }

    @Test
    fun `agent-state records done when the prompt has been idle`() {
        val (_, store) = runAgentState(
            stateInput("Notification", ""","message":"Claude is waiting for your input","notification_type":"idle_prompt""""),
        )

        assertEquals(AgentState.DONE, recorded(store)?.state)
    }

    @Test
    fun `agent-state ignores notifications that are not about waiting`() {
        val (exitCode, store) = runAgentState(stateInput("Notification", ""","notification_type":"auth_success""""))

        assertEquals(0, exitCode)
        assertEquals(null, recorded(store))
    }

    @Test
    fun `agent-state records done on Stop`() {
        val (_, store) = runAgentState(
            stateInput("Stop", ""","permission_mode":"auto","stop_hook_active":false,"last_assistant_message":"ok","background_tasks":[]"""),
        )

        assertEquals(AgentState.DONE, recorded(store)?.state)
    }

    @Test
    fun `agent-state removes the record when the session ends`() {
        val store = FakeAgentSessionStore(
            mapOf(base to listOf(AgentSessionRecord("963d9441", "claude", AgentState.DONE, 1L, transcript, 4242L))),
        )

        val (exitCode, _) = runAgentState(stateInput("SessionEnd", ""","reason":"other""""), store)

        assertEquals(0, exitCode, err.toString())
        assertEquals(null, recorded(store))
    }

    @Test
    fun `agent-state records the session in the worktree containing cwd`() {
        val linked = "/home/dev/projects/worktrees/TASK-1"
        val executor = Executor(porcelain(linked to "feature/TASK-1"))
        val store = FakeAgentSessionStore()

        cli(executor, stateInput("Stop", cwd = "$linked/src"), sessionStore = store).run(listOf("hook", "agent-state"))

        assertEquals(setOf(linked), store.records.keys)
    }

    @Test
    fun `agent-state outside a repository records nothing and exits with 0`() {
        val executor = Executor(porcelain()) { command ->
            if ("--git-common-dir" in command) CommandResult(command, 128, "", "fatal: not a git repository") else null
        }
        val store = FakeAgentSessionStore()

        val exitCode = cli(executor, stateInput("Stop", cwd = "/tmp/elsewhere"), sessionStore = store).run(listOf("hook", "agent-state"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(store.records.isEmpty())
        assertEquals("", out.toString() + err.toString())
    }

    @Test
    fun `agent-state ignores unknown events`() {
        val (exitCode, store) = runAgentState(stateInput("PreToolUse"))

        assertEquals(0, exitCode)
        assertTrue(store.records.isEmpty())
    }

    @Test
    fun `agent-state with invalid input prints the error and exits with 2`() {
        val exitCode = cli(Executor(porcelain()), "not json").run(listOf("hook", "agent-state"))

        assertEquals(2, exitCode)
        assertTrue(err.contains("agent-state"), err.toString())
    }
}
