package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakeHerdrConfigSource
import app.luxion.shogunai.domain.FakeOrcaClient
import app.luxion.shogunai.domain.FakeKmpTestWiring
import app.luxion.shogunai.domain.FakeMachineCapacity
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeResourceLimitsRepository
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.AppInstanceLock
import app.luxion.shogunai.domain.io.AppLauncher
import app.luxion.shogunai.domain.io.AppRequestChannel
import app.luxion.shogunai.domain.model.AppRequest
import app.luxion.shogunai.domain.model.CapacityProfile
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.ResourceSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProjectAndAppCommandsTest {

    private val base = "/home/dev/projects/main-repo"
    private val linked = "/home/dev/projects/main-repo-worktrees/TASK-123"
    private val other = "/home/dev/projects/other-repo"
    private val now = Instant.parse("2026-10-22T10:00:00Z")

    private val registered = Project(
        id = "registered",
        name = "Custom name",
        config = ProjectConfig(
            baseRepositoryPath = base,
            worktreesRoot = "/home/dev/custom-worktrees",
            secretFiles = listOf(".env"),
            mainBranch = "develop",
        ),
    )

    private val out = StringBuilder()
    private val err = StringBuilder()
    private val sent = mutableListOf<AppRequest>()
    private var launches = 0

    private val channel = object : AppRequestChannel {
        override fun send(request: AppRequest) {
            sent += request
        }

        override fun receive(nowMillis: Long): List<AppRequest> = emptyList()
    }

    private class FakeLock(private val running: Boolean) : AppInstanceLock {
        override fun isHeldByAnyInstance() = running
        override fun acquire() = !running
        override fun release() = Unit
    }

    private fun launcher(available: Boolean) = object : AppLauncher {
        override fun isAvailable() = available
        override fun launch() {
            launches++
        }
    }

    private val executor = object : ShellCommandExecutor {
        override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult {
            val repository = when {
                workingDirectory?.startsWith(base) == true || workingDirectory?.startsWith(linked) == true -> base
                workingDirectory?.startsWith(other) == true -> other
                else -> null
            }
            return if (repository == null) {
                CommandResult(command, 128, "", "fatal: not a git repository")
            } else {
                CommandResult(command, 0, "$repository/.git\n", "")
            }
        }
    }

    private fun cli(
        projects: FakeProjectRepository = FakeProjectRepository(),
        workingDirectory: String = base,
        appRunning: Boolean = false,
        appAvailable: Boolean = true,
    ) = ShogunCli(
        executor = executor,
        fileManager = FakeFileManager(existing = listOf(base, other)),
        projectRepository = projects,
        provisioningSource = FakeRepositoryProvisioningSource(),
        daemonSource = FakeGradleDaemonSource(),
        gradleUserHome = "/home/dev/.gradle",
        environmentStateStore = FakeEnvironmentStateStore(),
        portProbe = FakePortProbe(),
        androidSdk = FakeAndroidSdk(),
        appleSimulators = FakeAppleSimulators(),
        kmpTestWiring = FakeKmpTestWiring(),
        resourceLimits = FakeResourceLimitsRepository(ResourceSettings(CapacityProfile.PER_WORKTREE)),
        machineCapacity = FakeMachineCapacity(),
        derivedDataSource = FakeDerivedDataSource(),
        herdrConfig = FakeHerdrConfigSource(),
        orca = FakeOrcaClient(installed = false),
        workingDirectory = workingDirectory,
        out = out,
        err = err,
        canonicalPath = { it.trimEnd('/') },
        now = { now },
        userHome = "/home/dev",
        appRequests = channel,
        appLock = FakeLock(appRunning),
        appLauncher = launcher(appAvailable),
    )

    @Test
    fun `project add registers the current repository with the app's defaults`() {
        val projects = FakeProjectRepository()

        val exitCode = cli(projects).run(listOf("project", "add"))

        assertEquals(0, exitCode, err.toString())
        val project = projects.list().single()
        assertEquals("main-repo", project.name)
        assertEquals(base, project.config.baseRepositoryPath)
        assertEquals("/home/dev/projects/main-repo-worktrees", project.config.worktreesRoot)
        assertEquals("Added project main-repo ($base)\n", out.toString())
    }

    @Test
    fun `project add from a linked worktree registers the main repository`() {
        val projects = FakeProjectRepository()

        cli(projects, workingDirectory = linked).run(listOf("project", "add"))

        assertEquals(base, projects.list().single().config.baseRepositoryPath)
    }

    @Test
    fun `project add with a path registers that repository`() {
        val projects = FakeProjectRepository()

        cli(projects, workingDirectory = "/home/dev").run(listOf("project", "add", other))

        assertEquals(other, projects.list().single().config.baseRepositoryPath)
    }

    @Test
    fun `project add on a registered repository changes nothing and says so`() {
        val projects = FakeProjectRepository(listOf(registered))

        val exitCode = cli(projects).run(listOf("project", "add"))

        assertEquals(0, exitCode)
        assertEquals(listOf(registered), projects.list())
        assertEquals("Project Custom name is already registered ($base)\n", out.toString())
    }

    @Test
    fun `project add json reports the action and the project`() {
        val projects = FakeProjectRepository(listOf(registered))

        cli(projects).run(listOf("project", "add", "--json"))

        val document = Json.parseToJsonElement(out.toString()).jsonObject
        assertEquals("alreadyRegistered", document.getValue("action").jsonPrimitive.content)
        assertEquals("registered", document.getValue("id").jsonPrimitive.content)
        assertEquals(base, document.getValue("repository").jsonPrimitive.content)
    }

    @Test
    fun `project add json reports a new project as added`() {
        cli().run(listOf("project", "add", "--json"))

        assertEquals("added", Json.parseToJsonElement(out.toString()).jsonObject.getValue("action").jsonPrimitive.content)
    }

    @Test
    fun `project add outside a repository fails with 1 and registers nothing`() {
        val projects = FakeProjectRepository()

        val exitCode = cli(projects, workingDirectory = "/home/dev").run(listOf("project", "add", "/home/dev/plain-folder"))

        assertEquals(1, exitCode)
        assertTrue(err.isNotEmpty())
        assertTrue(projects.list().isEmpty())
    }

    @Test
    fun `project list prints each project and works outside a repository`() {
        val projects = FakeProjectRepository(listOf(registered, registered.copy(id = "second", name = "Other", config = registered.config.copy(baseRepositoryPath = other))))

        val exitCode = cli(projects, workingDirectory = "/home/dev").run(listOf("project", "list"))

        assertEquals(0, exitCode)
        assertEquals("Custom name  $base\nOther  $other\n", out.toString())
    }

    @Test
    fun `project list says there are no projects`() {
        assertEquals(0, cli().run(listOf("project", "list")))
        assertEquals("No projects registered.\n", out.toString())
    }

    @Test
    fun `project list json is an empty list without projects`() {
        cli().run(listOf("project", "list", "--json"))

        assertEquals(0, Json.parseToJsonElement(out.toString()).jsonObject.getValue("projects").jsonArray.size)
    }

    @Test
    fun `project remove removes the repository's project only`() {
        val second = registered.copy(id = "second", config = registered.config.copy(baseRepositoryPath = other))
        val projects = FakeProjectRepository(listOf(registered, second))

        val exitCode = cli(projects).run(listOf("project", "remove"))

        assertEquals(0, exitCode)
        assertEquals(listOf(second), projects.list())
        assertEquals("Removed project Custom name ($base)\n", out.toString())
    }

    @Test
    fun `project remove finds a project whose folder is gone`() {
        val gone = registered.copy(config = registered.config.copy(baseRepositoryPath = "/home/dev/projects/deleted-repo"))
        val projects = FakeProjectRepository(listOf(gone))

        val exitCode = cli(projects, workingDirectory = "/home/dev").run(listOf("project", "remove", "/home/dev/projects/deleted-repo"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(projects.list().isEmpty())
    }

    @Test
    fun `project remove without a project fails with 1`() {
        val projects = FakeProjectRepository(listOf(registered))

        val exitCode = cli(projects, workingDirectory = other).run(listOf("project", "remove"))

        assertEquals(1, exitCode)
        assertEquals(listOf(registered), projects.list())
        assertTrue(err.toString().contains("No project is registered"))
    }

    @Test
    fun `app asks the running app to open the project and does not launch it`() {
        val exitCode = cli(FakeProjectRepository(listOf(registered)), appRunning = true).run(listOf("app"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(listOf(AppRequest.openProject(now.toEpochMilli(), "registered", settings = false)), sent)
        assertEquals(0, launches)
    }

    @Test
    fun `app launches the app after leaving the request when it isn't running`() {
        val exitCode = cli(FakeProjectRepository(listOf(registered)), appRunning = false).run(listOf("app", "--settings"))

        assertEquals(0, exitCode)
        assertEquals(listOf(AppRequest.openProject(now.toEpochMilli(), "registered", settings = true)), sent)
        assertEquals(1, launches)
    }

    @Test
    fun `app registers a repository without a project first`() {
        val projects = FakeProjectRepository()

        cli(projects, appRunning = true).run(listOf("app"))

        val project = projects.list().single()
        assertEquals(base, project.config.baseRepositoryPath)
        assertEquals(project.id, sent.single().projectId)
        assertTrue(out.toString().startsWith("Added project main-repo"))
    }

    @Test
    fun `app outside a repository only opens the app`() {
        val exitCode = cli(workingDirectory = "/home/dev", appRunning = true).run(listOf("app"))

        assertEquals(0, exitCode)
        assertEquals(listOf(AppRequest.openApp(now.toEpochMilli())), sent)
    }

    @Test
    fun `app with a path that isn't a repository fails and asks for nothing`() {
        val projects = FakeProjectRepository()

        val exitCode = cli(projects, workingDirectory = "/home/dev", appRunning = true).run(listOf("app", "/home/dev/plain-folder"))

        assertEquals(1, exitCode)
        assertTrue(sent.isEmpty())
        assertTrue(projects.list().isEmpty())
    }

    @Test
    fun `app settings outside a repository is a usage error`() {
        val exitCode = cli(workingDirectory = "/home/dev", appRunning = true).run(listOf("app", "--settings"))

        assertEquals(2, exitCode)
        assertTrue(err.toString().contains("--settings needs a repository"))
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `app without the desktop app fails and leaves nothing behind`() {
        val projects = FakeProjectRepository()

        val exitCode = cli(projects, appRunning = false, appAvailable = false).run(listOf("app"))

        assertEquals(1, exitCode)
        assertTrue(err.toString().contains("desktop app isn't available"))
        assertTrue(sent.isEmpty())
        assertTrue(projects.list().isEmpty())
        assertEquals(0, launches)
    }

    @Test
    fun `app reaches a running app even from an install without the launcher`() {
        val exitCode = cli(FakeProjectRepository(listOf(registered)), appRunning = true, appAvailable = false).run(listOf("app"))

        assertEquals(0, exitCode)
        assertEquals(1, sent.size)
    }

    @Test
    fun `parses the project actions`() {
        assertEquals(CliCommand.Project(ProjectAction.ADD), command("project", "add"))
        assertEquals(CliCommand.Project(ProjectAction.ADD, "../repo", json = true), command("project", "add", "../repo", "--json"))
        assertEquals(CliCommand.Project(ProjectAction.LIST), command("project", "list"))
        assertEquals(CliCommand.Project(ProjectAction.REMOVE, "../repo"), command("project", "remove", "../repo"))
    }

    @Test
    fun `rejects wrong project usage`() {
        listOf(
            listOf("project"),
            listOf("project", "rename"),
            listOf("project", "list", "extra"),
            listOf("project", "add", "a", "b"),
            listOf("project", "add", "--force"),
        ).forEach { assertIs<ParsedArguments.UsageError>(parseArguments(it), it.toString()) }
    }

    @Test
    fun `parses app`() {
        assertEquals(CliCommand.App(), command("app"))
        assertEquals(CliCommand.App("../repo", settings = true), command("app", "--settings", "../repo"))
        assertIs<ParsedArguments.UsageError>(parseArguments(listOf("app", "a", "b")))
        assertIs<ParsedArguments.UsageError>(parseArguments(listOf("app", "--json")))
    }

    private fun command(vararg args: String): CliCommand =
        assertIs<ParsedArguments.Command>(parseArguments(args.toList())).command
}
