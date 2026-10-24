package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.FakeMachineCapacity
import app.luxion.shogunai.domain.FakeResourceLimitsRepository
import app.luxion.shogunai.domain.model.RepositoryApprovalSubject
import app.luxion.shogunai.domain.model.CapacityProfile
import app.luxion.shogunai.domain.model.DerivedDataQuotaMode
import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.BuildInfo
import app.luxion.shogunai.domain.FakeAgentSessionStore
import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeComposeProjectRegistry
import app.luxion.shogunai.domain.FakeComposeProjectSource
import app.luxion.shogunai.domain.io.ComposeRegistryEntry
import app.luxion.shogunai.domain.model.ComposeAction
import app.luxion.shogunai.domain.model.ComposeResource
import app.luxion.shogunai.domain.model.ComposeResourceKind
import app.luxion.shogunai.domain.FakeMavenDaemonSource
import app.luxion.shogunai.domain.model.ComposeProject
import app.luxion.shogunai.domain.model.DockerComposeProvider
import app.luxion.shogunai.domain.model.MavenDaemonProcess
import app.luxion.shogunai.domain.model.MavenProvider
import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeHerdrClient
import app.luxion.shogunai.domain.FakeHerdrConfigSource
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakeKmpTestWiring
import app.luxion.shogunai.domain.FakeGradleDaemonSource.Companion.gradleDaemon
import app.luxion.shogunai.domain.FakeGradleDaemonSource.Companion.kotlinDaemon
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryTrust
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.EnvironmentDeclaration
import app.luxion.shogunai.domain.model.parseEnvironmentVariable
import app.luxion.shogunai.domain.model.EmulatorProvider
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.HerdrAgent
import app.luxion.shogunai.domain.model.AgentSessionRecord
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.GradleProvider
import app.luxion.shogunai.domain.model.IosSimulatorProvider
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.XcodeProvider
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShogunCliTest {

    private val resourceLimits = FakeResourceLimitsRepository(ResourceSettings(CapacityProfile.PER_WORKTREE))

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
            command.take(2) == listOf("git", "rev-parse") && command.last() == "HEAD^{commit}" -> Executor.ok(command, "abc123\n")
            command.take(2) == listOf("git", "rev-parse") -> Executor.fail(command)
            "status" in command && directory == dirty -> Executor.ok(command, " M a.kt\n M b.kt\n")
            "rev-list" in command && command.last() == "@{upstream}...HEAD" -> Executor.fail(command)
            "rev-list" in command && directory == merged -> Executor.ok(command, "3\t0")
            "rev-list" in command -> Executor.ok(command, "0\t2")
            else -> null
        }
    }

    private val environmentStore = FakeEnvironmentStateStore()

    private val androidSdk = FakeAndroidSdk()
    private val appleSimulators = FakeAppleSimulators()
    private val kmpTestWiring = FakeKmpTestWiring()
    private val out = StringBuilder()
    private val err = StringBuilder()

    private fun cli(
        executor: ShellCommandExecutor,
        projects: List<Project> = listOf(project),
        fileManager: FakeFileManager = FakeFileManager(existing = listOf(base, "$base/local.properties")),
        provisioning: FakeRepositoryProvisioningSource = FakeRepositoryProvisioningSource(),
        workingDirectory: String = base,
        canonicalPath: (String) -> String = { it.trimEnd('/') },
        daemons: FakeGradleDaemonSource = FakeGradleDaemonSource(),
        derivedDataSource: FakeDerivedDataSource = FakeDerivedDataSource(),
        herdrWorktreesRoot: String = "/home/dev/.herdr/worktrees",
        herdr: FakeHerdrClient = FakeHerdrClient(),
        trust: FakeRepositoryTrust = FakeRepositoryTrust(),
        answer: String? = null,
        projectRepository: ProjectRepository = FakeProjectRepository(projects),
        sessionStore: FakeAgentSessionStore = FakeAgentSessionStore(),
        environment: Map<String, String> = emptyMap(),
        mavenDaemons: FakeMavenDaemonSource = FakeMavenDaemonSource(),
        composeProjects: FakeComposeProjectSource = FakeComposeProjectSource(),
        composeRegistry: FakeComposeProjectRegistry = FakeComposeProjectRegistry(),
    ) = ShogunCli(
        executor = executor,
        fileManager = fileManager,
        projectRepository = projectRepository,
        provisioningSource = provisioning,
        daemonSource = daemons,
        gradleUserHome = gradleUserHome,
        environmentStateStore = environmentStore,
        portProbe = FakePortProbe(),
        androidSdk = androidSdk,
        appleSimulators = appleSimulators,
        kmpTestWiring = kmpTestWiring,
        resourceLimits = resourceLimits,
        machineCapacity = FakeMachineCapacity(),
        derivedDataSource = derivedDataSource,
        herdrConfig = FakeHerdrConfigSource(herdrWorktreesRoot),
        workingDirectory = workingDirectory,
        out = out,
        err = err,
        canonicalPath = canonicalPath,
        now = { now },
        herdr = herdr,
        trust = trust,
        readAnswer = { answer },
        environmentVariable = { environment[it] },
        userHome = "/home/dev",
        sessionStore = sessionStore,
        mavenDaemonSource = mavenDaemons,
        composeSource = composeProjects,
        composeRegistry = composeRegistry,
    )

    private val gradleUserHome = "/home/dev/.gradle"
    private val now = Instant.parse("2026-10-16T11:00:00Z")
    private val idleSinceTenMinutes = DaemonState.Idle(Instant.parse("2026-10-16T10:50:00Z"))


    private fun declaring(providers: RepositoryProviders) = FakeRepositoryProvisioningSource(
        byRepository = mapOf(base to RepositoryProvisioning(providers = providers)),
    )

    @Test
    fun `stacks lists the detected stacks with their suggested providers`() {
        val fileManager = FakeFileManager(existing = listOf(base, "$base/pom.xml"))

        val exitCode = cli(repositoryExecutor(), fileManager = fileManager).run(listOf("stacks"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("Maven  evidence=pom.xml  maven=suggested\n", out.toString())
    }

    @Test
    fun `stacks shows a declared provider as declared`() {
        val fileManager = FakeFileManager(existing = listOf(base, "$base/pom.xml"))

        cli(repositoryExecutor(), fileManager = fileManager, provisioning = declaring(RepositoryProviders(maven = MavenProvider())))
            .run(listOf("stacks"))

        assertEquals("Maven  evidence=pom.xml  maven=declared\n", out.toString())
    }

    @Test
    fun `stacks says so and exits with 0 when nothing is detected`() {
        val exitCode = cli(repositoryExecutor(), fileManager = FakeFileManager(existing = listOf(base, "$base/README.md"))).run(listOf("stacks"))

        assertEquals(0, exitCode)
        assertEquals("No known stack detected.\n", out.toString())
    }

    @Test
    fun `stacks outside a repository fails with 1`() {
        val executor = Executor { command, _ -> Executor.fail(command, "fatal: not a git repository", 128) }

        val exitCode = cli(executor, workingDirectory = "/tmp").run(listOf("stacks"))

        assertEquals(1, exitCode)
        assertTrue(err.isNotEmpty())
    }

    @Test
    fun `stacks json lists the stacks with their providers`() {
        val fileManager = FakeFileManager(existing = listOf(base, "$base/pom.xml"))

        cli(repositoryExecutor(), fileManager = fileManager).run(listOf("stacks", "--json"))

        val stack = Json.parseToJsonElement(out.toString()).jsonObject.getValue("stacks").jsonArray.single().jsonObject
        assertEquals("maven", stack.getValue("stack").jsonPrimitive.content)
        assertEquals(false, stack.getValue("providers").jsonArray.single().jsonObject.getValue("declared").jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `maven lists the daemons with their memory and worktree`() {
        val daemons = FakeMavenDaemonSource(
            listOf(
                MavenDaemonProcess(20, 1_073_741_824, "$merged/service"),
                MavenDaemonProcess(21, 1_073_741_824, "/home/dev/other"),
            ),
        )
        val fileManager = FakeFileManager(existing = listOf(base, merged, "/home/dev/other"))

        val exitCode = cli(repositoryExecutor(), fileManager = fileManager, mavenDaemons = daemons).run(listOf("maven"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(
            """
            20  memory=1.0 GB  worktree=$merged
            21  memory=1.0 GB  worktree=other-project
            total  memory=2.0 GB  daemons=2

            """.trimIndent(),
            out.toString(),
        )
    }

    @Test
    fun `maven without daemons says so`() {
        assertEquals(0, cli(repositoryExecutor()).run(listOf("maven")))
        assertEquals("No Maven daemons running.\n", out.toString())
    }

    @Test
    fun `maven works outside a repository`() {
        val executor = Executor { command, _ -> Executor.fail(command, "not a git repository", 128) }
        val daemons = FakeMavenDaemonSource(listOf(MavenDaemonProcess(20, 1_048_576, "/x")))

        val exitCode = cli(executor, mavenDaemons = daemons, workingDirectory = "/tmp").run(listOf("maven"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("20  memory=1.0 MB\ntotal  memory=1.0 MB  daemons=1\n", out.toString())
    }

    private val composeDeclared = declaring(RepositoryProviders(dockerCompose = DockerComposeProvider()))

    private fun composeProject(name: String, file: String) = ComposeProject(name, "running(1)", listOf(file))

    @Test
    fun `compose lists the projects of the worktrees with their status`() {
        val projects = FakeComposeProjectSource(
            listOf(composeProject("main-repo-task-1", "$merged/compose.yaml"), composeProject("main-repo-task-9", "$worktreesRoot/TASK-9/compose.yaml")),
        )

        val exitCode = cli(repositoryExecutor(), provisioning = composeDeclared, composeProjects = projects).run(listOf("compose"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(
            "main-repo-task-1  status=running(1)  worktree=$merged\nmain-repo-task-9  status=running(1)  worktree=removed\n",
            out.toString(),
        )
    }

    @Test
    fun `compose down takes down the projects of removed worktrees keeping volumes`() {
        val projects = FakeComposeProjectSource(
            listOf(composeProject("main-repo-task-1", "$merged/compose.yaml"), composeProject("main-repo-task-9", "$worktreesRoot/TASK-9/compose.yaml")),
        )

        val exitCode = cli(repositoryExecutor(), provisioning = composeDeclared, composeProjects = projects).run(listOf("compose", "down"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(listOf("main-repo-task-9" to false), projects.downs)
        assertEquals("took down main-repo-task-9 (volumes kept)\n", out.toString())
    }

    @Test
    fun `compose down --volumes removes the volumes too`() {
        val projects = FakeComposeProjectSource(listOf(composeProject("main-repo-task-9", "$worktreesRoot/TASK-9/compose.yaml")))

        cli(repositoryExecutor(), provisioning = composeDeclared, composeProjects = projects).run(listOf("compose", "down", "--volumes"))

        assertEquals(listOf("main-repo-task-9" to true), projects.downs)
    }

    @Test
    fun `compose down of a given worktree takes down its project`() {
        val projects = FakeComposeProjectSource()

        val exitCode = cli(repositoryExecutor(), provisioning = composeDeclared, composeProjects = projects)
            .run(listOf("compose", "down", "TASK-1"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(listOf("main-repo-task-1" to false), projects.downs)
    }

    @Test
    fun `compose down of a worktree that doesn't exist fails with 1`() {
        val projects = FakeComposeProjectSource()

        val exitCode = cli(repositoryExecutor(), provisioning = composeDeclared, composeProjects = projects)
            .run(listOf("compose", "down", "TASK-404"))

        assertEquals(1, exitCode)
        assertTrue(projects.downs.isEmpty())
    }

    @Test
    fun `compose without the provider declared fails with 1 and never runs docker`() {
        val projects = FakeComposeProjectSource()

        val listed = cli(repositoryExecutor(), composeProjects = projects).run(listOf("compose"))
        val down = cli(repositoryExecutor(), composeProjects = projects).run(listOf("compose", "down"))

        assertEquals(1, listed)
        assertEquals(1, down)
        assertTrue(err.contains("docker-compose"), err.toString())
        assertTrue(projects.listings.isEmpty() && projects.downs.isEmpty())
    }

    private fun cleanableExecutor() = repositoryExecutor()

    private fun environmentExecutor() = repositoryExecutor { command, directory ->
        if (command.contains("--path-format=absolute") && directory != null) {
            val admin = if (directory == base) "$base/.git" else "$base/.git/worktrees/${directory.substringAfterLast('/')}"
            Executor.ok(command, "$admin\n$base/.git\n")
        } else {
            null
        }
    }

    private val removedRegistry = FakeComposeProjectRegistry(mapOf(base to listOf(ComposeRegistryEntry("main-repo-task-9", "$worktreesRoot/TASK-9"))))

    private fun leftover(kind: ComposeResourceKind, name: String) = ComposeResource(kind, name, name, "main-repo-task-9")

    @Test
    fun `compose up starts the current worktree's services with its environment`() {
        val projects = FakeComposeProjectSource()

        val exitCode = cli(environmentExecutor(), provisioning = composeDeclared, composeProjects = projects, workingDirectory = merged)
            .run(listOf("compose", "up"))

        assertEquals(0, exitCode, err.toString())
        val run = projects.runs.single()
        assertEquals(merged, run.workingDirectory)
        assertEquals("main-repo-task-1", run.environment["COMPOSE_PROJECT_NAME"])
        assertEquals(ComposeAction.Up(), run.action)
        assertTrue(out.contains("started Compose project main-repo-task-1 for $merged"), out.toString())
        assertTrue(err.contains("Container main-repo-task-1-api Started"), err.toString())
    }

    @Test
    fun `compose up of a given worktree passes --build and the arguments after --`() {
        val projects = FakeComposeProjectSource()

        val exitCode = cli(environmentExecutor(), provisioning = composeDeclared, composeProjects = projects)
            .run(listOf("compose", "up", "TASK-1", "--build", "--", "api", "--json"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(ComposeAction.Up(build = true, extraArguments = listOf("api", "--json")), projects.runs.single().action)
    }

    @Test
    fun `compose build builds with the worktree's environment`() {
        val projects = FakeComposeProjectSource()

        val exitCode = cli(environmentExecutor(), provisioning = composeDeclared, composeProjects = projects, workingDirectory = merged)
            .run(listOf("compose", "build"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(ComposeAction.Build(), projects.runs.single().action)
        assertTrue(out.contains("built Compose project main-repo-task-1"), out.toString())
    }

    @Test
    fun `compose up of an unknown worktree fails with 1 without running docker`() {
        val projects = FakeComposeProjectSource()

        val exitCode = cli(environmentExecutor(), provisioning = composeDeclared, composeProjects = projects).run(listOf("compose", "up", "TASK-404"))

        assertEquals(1, exitCode)
        assertTrue(projects.runs.isEmpty())
    }

    @Test
    fun `a failing compose up prints the reason and exits with 1`() {
        val projects = FakeComposeProjectSource(failingRun = "port is already allocated")

        val exitCode = cli(environmentExecutor(), provisioning = composeDeclared, composeProjects = projects, workingDirectory = merged)
            .run(listOf("compose", "up"))

        assertEquals(1, exitCode)
        assertTrue(err.contains("port is already allocated"), err.toString())
    }

    @Test
    fun `compose up --json prints one object and sends compose output to standard error`() {
        val projects = FakeComposeProjectSource()

        val exitCode = cli(environmentExecutor(), provisioning = composeDeclared, composeProjects = projects, workingDirectory = merged)
            .run(listOf("compose", "up", "--json"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("{\"worktree\":\"$merged\",\"project\":\"main-repo-task-1\",\"succeeded\":true}\n", out.toString())
        assertTrue(err.contains("Started"), err.toString())
    }

    @Test
    fun `compose lists the leftovers of removed worktrees`() {
        val projects = FakeComposeProjectSource(resources = listOf(leftover(ComposeResourceKind.VOLUME, "main-repo-task-9_db-data")))

        val exitCode = cli(repositoryExecutor(), provisioning = composeDeclared, composeProjects = projects, composeRegistry = removedRegistry)
            .run(listOf("compose"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(out.contains("leftover volume main-repo-task-9_db-data  project=main-repo-task-9"), out.toString())
        assertTrue(out.contains("shogun compose prune --volumes"), out.toString())
    }

    @Test
    fun `compose prune without --yes lists what it would remove and removes nothing`() {
        val volume = leftover(ComposeResourceKind.VOLUME, "main-repo-task-9_db-data")
        val image = leftover(ComposeResourceKind.IMAGE, "main-repo-task-9-api")
        val projects = FakeComposeProjectSource(resources = listOf(volume, image))

        val exitCode = cli(repositoryExecutor(), provisioning = composeDeclared, composeProjects = projects, composeRegistry = removedRegistry)
            .run(listOf("compose", "prune"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(projects.removals.isEmpty())
        assertTrue(out.contains("would remove image main-repo-task-9-api"), out.toString())
        assertTrue(out.contains("would keep volume main-repo-task-9_db-data"), out.toString())
    }

    @Test
    fun `compose prune --yes removes networks and images and keeps volumes`() {
        val volume = leftover(ComposeResourceKind.VOLUME, "main-repo-task-9_db-data")
        val network = leftover(ComposeResourceKind.NETWORK, "main-repo-task-9_default")
        val projects = FakeComposeProjectSource(resources = listOf(volume, network))

        val exitCode = cli(repositoryExecutor(), provisioning = composeDeclared, composeProjects = projects, composeRegistry = removedRegistry)
            .run(listOf("compose", "prune", "--yes"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(listOf(network), projects.removals)
        assertEquals("removed network main-repo-task-9_default\nkept volume main-repo-task-9_db-data\n", out.toString())
    }

    @Test
    fun `compose prune --yes --volumes removes the volumes too`() {
        val volume = leftover(ComposeResourceKind.VOLUME, "main-repo-task-9_db-data")
        val projects = FakeComposeProjectSource(resources = listOf(volume))

        cli(repositoryExecutor(), provisioning = composeDeclared, composeProjects = projects, composeRegistry = removedRegistry)
            .run(listOf("compose", "prune", "--yes", "--volumes"))

        assertEquals(listOf(volume), projects.removals)
    }

    @Test
    fun `compose prune with nothing left says so`() {
        val exitCode = cli(repositoryExecutor(), provisioning = composeDeclared).run(listOf("compose", "prune", "--yes"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("Nothing to remove.\n", out.toString())
    }

    @Test
    fun `compose prune --yes --json lists removed resources and failures`() {
        val image = leftover(ComposeResourceKind.IMAGE, "main-repo-task-9-api")
        val network = leftover(ComposeResourceKind.NETWORK, "main-repo-task-9_default")
        val projects = FakeComposeProjectSource(resources = listOf(image, network), failingRemovals = mapOf(image.id to "in use"))

        val exitCode = cli(repositoryExecutor(), provisioning = composeDeclared, composeProjects = projects, composeRegistry = removedRegistry)
            .run(listOf("compose", "prune", "--yes", "--json"))

        assertEquals(1, exitCode)
        val json = out.toString()
        assertTrue(json.contains("\"removed\":[{\"kind\":\"network\",\"name\":\"main-repo-task-9_default\",\"project\":\"main-repo-task-9\"}]"), json)
        assertTrue(json.contains("\"message\":\"in use\""), json)
    }

    @Test
    fun `clean --yes --down-compose-projects removes built images and keeps volumes unless asked`() {
        val image = ComposeResource(ComposeResourceKind.IMAGE, "sha256:aa", "main-repo-task-1-api", "main-repo-task-1")
        val volume = ComposeResource(ComposeResourceKind.VOLUME, "main-repo-task-1_db", "main-repo-task-1_db", "main-repo-task-1")
        val kept = FakeComposeProjectSource(resources = listOf(image, volume))
        val removed = FakeComposeProjectSource(resources = listOf(image, volume))

        cli(cleanableExecutor(), provisioning = composeDeclared, composeProjects = kept).run(listOf("clean", "--yes", "--down-compose-projects"))
        cli(cleanableExecutor(), provisioning = composeDeclared, composeProjects = removed)
            .run(listOf("clean", "--yes", "--down-compose-projects", "--compose-volumes"))

        assertEquals(listOf(image), kept.removals)
        assertEquals(listOf(image, volume), removed.removals)
        assertTrue(out.contains("removed image main-repo-task-1-api"), out.toString())
        assertTrue(out.contains("kept volume main-repo-task-1_db"), out.toString())
    }

    @Test
    fun `clean --compose-volumes without --down-compose-projects is a usage error`() {
        val projects = FakeComposeProjectSource()

        val exitCode = cli(cleanableExecutor(), provisioning = composeDeclared, composeProjects = projects).run(listOf("clean", "--yes", "--compose-volumes"))

        assertEquals(2, exitCode)
        assertTrue(err.contains("--compose-volumes needs --down-compose-projects"), err.toString())
        assertTrue(projects.removals.isEmpty())
    }

    @Test
    fun `clean --yes --down-compose-projects takes down the projects of the removed worktrees`() {
        val projects = FakeComposeProjectSource(listOf(composeProject("main-repo-task-1", "$merged/compose.yaml")))

        val exitCode = cli(cleanableExecutor(), provisioning = composeDeclared, composeProjects = projects)
            .run(listOf("clean", "--yes", "--down-compose-projects"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(listOf("main-repo-task-1" to false), projects.downs)
        assertTrue(out.contains("took down Compose project main-repo-task-1"), out.toString())
    }

    @Test
    fun `clean --yes without the option takes down no project`() {
        val projects = FakeComposeProjectSource(listOf(composeProject("main-repo-task-1", "$merged/compose.yaml")))

        cli(cleanableExecutor(), provisioning = composeDeclared, composeProjects = projects).run(listOf("clean", "--yes"))

        assertTrue(projects.downs.isEmpty())
        assertTrue(projects.listings.isEmpty())
    }

    @Test
    fun `clean --down-compose-projects without --yes only lists what would be taken down`() {
        val projects = FakeComposeProjectSource(listOf(composeProject("main-repo-task-1", "$merged/compose.yaml")))

        val exitCode = cli(cleanableExecutor(), provisioning = composeDeclared, composeProjects = projects)
            .run(listOf("clean", "--down-compose-projects"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(projects.downs.isEmpty())
        assertTrue(out.contains("would take down Compose project main-repo-task-1"), out.toString())
    }

    @Test
    fun `gradle lists daemons with their state, worktree and total memory`() {
        val daemons = FakeGradleDaemonSource(
            listOf(
                gradleDaemon(10, idleSinceTenMinutes, lastBuildDirectory = merged, memoryBytes = 1_073_741_824),
                gradleDaemon(11, DaemonState.Busy, lastBuildDirectory = "/home/dev/projects/gone", memoryBytes = 1_073_741_824),
                kotlinDaemon(12, memoryBytes = 536_870_912),
            ),
        )
        val fileManager = FakeFileManager(existing = listOf(base, merged))

        val exitCode = cli(repositoryExecutor(), fileManager = fileManager, daemons = daemons).run(listOf("gradle"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(
            """
            10  gradle 9.6.1  memory=1.0 GB  state=idle  idle-for=10m  worktree=$merged
            11  gradle 9.6.1  memory=1.0 GB  state=busy  worktree=removed
            12  kotlin  memory=512.0 MB
            total  memory=2.5 GB  daemons=3

            """.trimIndent(),
            out.toString(),
        )
    }

    @Test
    fun `gradle without daemons says so`() {
        val exitCode = cli(repositoryExecutor()).run(listOf("gradle"))

        assertEquals(0, exitCode)
        assertEquals("No Gradle or Kotlin daemons running.\n", out.toString())
    }

    @Test
    fun `gradle works outside a repository without a worktree column`() {
        val executor = Executor { command, _ ->
            if (command.containsAll(listOf("rev-parse", "--git-common-dir"))) Executor.fail(command, "not a git repository") else null
        }
        val daemons = FakeGradleDaemonSource(listOf(gradleDaemon(10, DaemonState.Busy, lastBuildDirectory = merged)))

        val exitCode = cli(executor, daemons = daemons, workingDirectory = "/tmp").run(listOf("gradle"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(out.startsWith("10  gradle 9.6.1  memory=953.7 MB  state=busy\n"), out.toString())
    }

    @Test
    fun `gradle prints the build cache notice for a gradle repository without it`() {
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(gradle = GradleProvider()))),
        )

        cli(repositoryExecutor(), provisioning = provisioning).run(listOf("gradle"))

        assertTrue(out.endsWith(ShogunCli.BUILD_CACHE_NOTICE + "\n"), out.toString())
    }

    @Test
    fun `gradle omits the notice when the cache is on or the provider isn't declared`() {
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(gradle = GradleProvider()))),
        )
        val cached = FakeFileManager(
            existing = listOf(base),
            properties = mapOf("$base/gradle.properties" to mapOf("org.gradle.caching" to "true")),
        )

        cli(repositoryExecutor(), provisioning = provisioning, fileManager = cached).run(listOf("gradle"))
        cli(repositoryExecutor()).run(listOf("gradle"))

        assertFalse(out.contains(ShogunCli.BUILD_CACHE_NOTICE), out.toString())
    }

    @Test
    fun `gradle stop stops idle daemons and keeps busy ones`() {
        val daemons = FakeGradleDaemonSource(
            listOf(
                gradleDaemon(10, idleSinceTenMinutes),
                gradleDaemon(11, idleSinceTenMinutes),
                gradleDaemon(12, DaemonState.Busy),
            ),
        )
        val executor = repositoryExecutor()

        val exitCode = cli(executor, daemons = daemons).run(listOf("gradle", "stop"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("stopped 10  gradle 9.6.1\nstopped 11  gradle 9.6.1\nkept 12  gradle  (busy)\n", out.toString())
        assertTrue(executor.ran("kill", "10"))
        assertFalse(executor.ran("kill", "12"))
    }

    @Test
    fun `gradle stop of a busy daemon keeps it and exits with 0`() {
        val daemons = FakeGradleDaemonSource(listOf(gradleDaemon(4242, DaemonState.Busy)))

        val exitCode = cli(repositoryExecutor(), daemons = daemons).run(listOf("gradle", "stop", "4242"))

        assertEquals(0, exitCode)
        assertEquals("kept 4242  gradle  (busy)\n", out.toString())
    }

    @Test
    fun `gradle stop of a process that isn't a daemon exits with 1`() {
        val exitCode = cli(repositoryExecutor()).run(listOf("gradle", "stop", "99999"))

        assertEquals(1, exitCode)
        assertEquals("shogun: 99999 isn't a Gradle or Kotlin daemon\n", err.toString())
    }

    @Test
    fun `clean stops the idle daemons of removed worktrees`() {
        val daemons = FakeGradleDaemonSource(
            listOf(gradleDaemon(10, idleSinceTenMinutes, lastBuildDirectory = merged), gradleDaemon(11, idleSinceTenMinutes, lastBuildDirectory = base)),
        )
        val executor = repositoryExecutor()

        val exitCode = cli(executor, daemons = daemons).run(listOf("clean", "--yes", "--stop-gradle-daemons"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(out.contains("removed $merged\n"), out.toString())
        assertTrue(out.contains("stopped 10  gradle 9.6.1\n"), out.toString())
        assertTrue(executor.ran("kill", "10"))
        assertFalse(executor.ran("kill", "11"))
    }

    @Test
    fun `clean without the option stops no daemon`() {
        val daemons = FakeGradleDaemonSource(listOf(gradleDaemon(10, idleSinceTenMinutes, lastBuildDirectory = merged)))
        val executor = repositoryExecutor()

        cli(executor, daemons = daemons).run(listOf("clean", "--yes"))

        assertFalse(executor.calls.any { it.first.first() == "kill" })
        assertEquals(0, daemons.listings)
    }

    @Test
    fun `clean with the option but without yes lists the daemons it would stop`() {
        val daemons = FakeGradleDaemonSource(listOf(gradleDaemon(10, idleSinceTenMinutes, lastBuildDirectory = merged)))
        val executor = repositoryExecutor()

        val exitCode = cli(executor, daemons = daemons).run(listOf("clean", "--stop-gradle-daemons"))

        assertEquals(0, exitCode)
        assertEquals(
            "would remove $merged\nwould stop Gradle daemon 10\nRun 'shogun clean --stop-gradle-daemons --yes' to remove the worktree.\n",
            out.toString(),
        )
        assertFalse(executor.calls.any { it.first.first() == "kill" })
    }

    @Test
    fun `new prints only the worktree path and uses the project's folder`() {
        val executor = repositoryExecutor()

        val exitCode = cli(executor).run(listOf("new", "TASK-123", "--fix"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("$worktreesRoot/TASK-123\n", out.toString())
        assertTrue(executor.ran("git", "worktree", "add", "--no-track", "$worktreesRoot/TASK-123", "-b", "fix/TASK-123", "abc123"))
    }

    @Test
    fun `new from a linked worktree starts at that worktree's HEAD`() {
        val executor = repositoryExecutor { command, directory ->
            if (command == listOf("git", "rev-parse", "--verify", "--quiet", "HEAD^{commit}") && directory == merged) {
                Executor.ok(command, "epic1234\n")
            } else {
                null
            }
        }

        val exitCode = cli(executor, workingDirectory = merged).run(listOf("new", "TASK-123"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(executor.ran("git", "worktree", "add", "--no-track", "$worktreesRoot/TASK-123", "-b", "feature/TASK-123", "epic1234"))
    }

    @Test
    fun `new with from starts at the ref`() {
        val executor = repositoryExecutor { command, _ ->
            if (command == listOf("git", "rev-parse", "--verify", "--quiet", "epic/redesign^{commit}")) {
                Executor.ok(command, "epic1234\n")
            } else {
                null
            }
        }

        val exitCode = cli(executor).run(listOf("new", "TASK-123", "--from", "epic/redesign"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(executor.ran("git", "worktree", "add", "--no-track", "$worktreesRoot/TASK-123", "-b", "feature/TASK-123", "epic1234"))
    }

    @Test
    fun `new with an unknown from creates nothing and exits with 1`() {
        val executor = repositoryExecutor()

        val exitCode = cli(executor).run(listOf("new", "TASK-123", "--from", "missing-branch"))

        assertEquals(1, exitCode)
        assertTrue(err.contains("missing-branch"))
        assertFalse(executor.calls.any { "add" in it.first })
    }

    @Test
    fun `new with a remote only branch creates a tracking branch`() {
        val executor = repositoryExecutor { command, _ ->
            if (command.take(2) == listOf("git", "show-ref") && command.last() != "refs/remotes/origin/feature/TASK-9") {
                Executor.fail(command)
            } else {
                null
            }
        }

        val exitCode = cli(executor).run(listOf("new", "--branch", "feature/TASK-9"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("$worktreesRoot/TASK-9\n", out.toString())
        assertTrue(
            executor.ran(
                "git", "worktree", "add", "--track", "-b", "feature/TASK-9", "$worktreesRoot/TASK-9", "origin/feature/TASK-9",
            ),
        )
    }

    @Test
    fun `new with a branch that exists nowhere creates nothing and exits with 1`() {
        val executor = repositoryExecutor { command, _ ->
            if (command.take(2) == listOf("git", "show-ref")) Executor.fail(command) else null
        }

        val exitCode = cli(executor).run(listOf("new", "--branch", "feature/missing"))

        assertEquals(1, exitCode)
        assertTrue(err.contains("Branch not found: feature/missing"), err.toString())
        assertFalse(executor.calls.any { "add" in it.first })
    }

    @Test
    fun `new with fetch fetches origin before resolving the start point`() {
        val executor = repositoryExecutor { command, _ ->
            if (command == listOf("git", "rev-parse", "--verify", "--quiet", "origin/develop^{commit}")) {
                Executor.ok(command, "fetched1\n")
            } else {
                null
            }
        }

        val exitCode = cli(executor).run(listOf("new", "TASK-123", "--from", "origin/develop", "--fetch"))

        assertEquals(0, exitCode, err.toString())
        val commands = executor.calls.map { it.first }
        val fetch = commands.indexOf(listOf("git", "fetch", "--prune", "origin"))
        val resolve = commands.indexOf(listOf("git", "rev-parse", "--verify", "--quiet", "origin/develop^{commit}"))
        assertTrue(fetch in 0 until resolve, commands.toString())
        assertTrue(executor.ran("git", "worktree", "add", "--no-track", "$worktreesRoot/TASK-123", "-b", "feature/TASK-123", "fetched1"))
    }

    @Test
    fun `new with fetch that fails creates nothing and exits with 1`() {
        val executor = repositoryExecutor { command, _ ->
            if (command.take(2) == listOf("git", "fetch")) Executor.fail(command, stderr = "Could not resolve host", exitCode = 128) else null
        }

        val exitCode = cli(executor).run(listOf("new", "TASK-123", "--fetch"))

        assertEquals(1, exitCode)
        assertTrue(err.contains("Could not resolve host"), err.toString())
        assertFalse(executor.calls.any { "add" in it.first })
        assertEquals("", out.toString())
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
    fun `new in a repository without commits asks for a first commit`() {
        val executor = repositoryExecutor { command, _ ->
            if (command.last() == "HEAD^{commit}") Executor.fail(command, "", 1) else null
        }
        val fileManager = FakeFileManager(existing = listOf(base, "$base/local.properties"))

        val exitCode = cli(executor, fileManager = fileManager).run(listOf("new", "TASK-1"))

        assertEquals(1, exitCode)
        assertEquals("shogun: The repository has no commits yet; make a first commit and try again\n", err.toString())
    }

    @Test
    fun `new in a bare repository says it is not supported yet`() {
        val executor = Executor { command, _ ->
            if (command.contains("--git-common-dir")) Executor.ok(command, "$base.git\ntrue\n") else Executor.fail(command)
        }

        val exitCode = cli(executor).run(listOf("new", "TASK-1"))

        assertEquals(1, exitCode)
        assertTrue(err.contains("bare repositories aren't supported yet"), err.toString())
    }

    @Test
    fun `new explains that the branch already exists and how to use it`() {
        val executor = repositoryExecutor { command, _ ->
            if (command.take(3) == listOf("git", "worktree", "add")) {
                Executor.fail(command, "fatal: a branch named 'feature/TASK-1' already exists", 128)
            } else {
                null
            }
        }
        val fileManager = FakeFileManager(existing = listOf(base, "$base/local.properties"))

        val exitCode = cli(executor, fileManager = fileManager).run(listOf("new", "TASK-1"))

        assertEquals(1, exitCode)
        assertTrue(err.contains("fatal: a branch named 'feature/TASK-1' already exists"), err.toString())
        assertTrue(err.contains("hint: The branch feature/TASK-1 already exists. Use another task id, or run 'shogun new --branch feature/TASK-1' to use it."), err.toString())
    }

    @Test
    fun `a corrupted projects file is reported in one line with exit code 1`() {
        val corrupted = object : ProjectRepository {
            override fun list(): List<Project> = throw WorktreeError.LocalFileUnreadable("/home/dev/.shogunai/projects.json", "unexpected token")
            override fun save(project: Project) = Unit
            override fun delete(id: String) = Unit
        }

        val exitCode = cli(repositoryExecutor(), projectRepository = corrupted).run(listOf("status"))

        assertEquals(1, exitCode)
        assertEquals("shogun: Can't read /home/dev/.shogunai/projects.json: unexpected token\n", err.toString())
    }

    @Test
    fun `an unexpected exception is reported in one line instead of a stack trace`() {
        val executor = repositoryExecutor { _, _ -> throw IllegalStateException("boom\n\tat somewhere") }

        val exitCode = cli(executor).run(listOf("status"))

        assertEquals(1, exitCode)
        assertEquals("shogun: boom\n", err.toString())
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

    private val linkingProvisioning = FakeRepositoryProvisioningSource(
        byRepository = mapOf(
            base to RepositoryProvisioning(
                copyPatterns = listOf(".env"),
                linkPatterns = listOf("token.txt"),
                postCreateCommands = listOf(listOf("npm", "install")),
            ),
        ),
    )

    private val installCommands = listOf(listOf("npm", "install"))

    private val approvedInstall get() = FakeRepositoryTrust(mapOf(base to RepositoryApprovalSubject(installCommands)))

    private fun linkingExecutor(hook: (List<String>) -> CommandResult? = { null }) = repositoryExecutor { command, _ ->
        when {
            "ls-files" in command && "token.txt" in command -> Executor.ok(command, "token.txt\u0000")
            "ls-files" in command -> Executor.ok(command, ".env\u0000")
            command.first() == "npm" -> hook(command)
            else -> null
        }
    }

    @Test
    fun `provision marks linked files and files copied instead of linking`() {
        val fileManager = FakeFileManager(existing = listOf(base, "$base/local.properties", "$base/token.txt"))

        val exitCode = cli(linkingExecutor(), provisioning = linkingProvisioning, fileManager = fileManager, workingDirectory = merged)
            .run(listOf("provision"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("local.properties\n.env\ntoken.txt (linked)\n", out.toString())

        out.clear()
        fileManager.symbolicLinksSupported = false
        cli(linkingExecutor(), provisioning = linkingProvisioning, fileManager = fileManager, workingDirectory = merged)
            .run(listOf("provision"))
        assertTrue(out.contains("token.txt (copied: symbolic links aren't available)"), out.toString())
    }

    @Test
    fun `provision fails with exit code 1 when a post-creation command fails`() {
        val executor = linkingExecutor { Executor.fail(it, stderr = "ENOTFOUND", exitCode = 4) }

        val exitCode = cli(executor, provisioning = linkingProvisioning, workingDirectory = merged, trust = approvedInstall)
            .run(listOf("provision"))

        assertEquals(1, exitCode)
        assertTrue(err.contains("npm install") && err.contains("exit code 4") && err.contains("ENOTFOUND"), err.toString())
    }

    @Test
    fun `new prints the kept path and the failure when a post-creation command fails`() {
        val executor = linkingExecutor { Executor.fail(it, stderr = "ENOTFOUND", exitCode = 4) }

        val exitCode = cli(executor, provisioning = linkingProvisioning, trust = approvedInstall).run(listOf("new", "TASK-123"))

        assertEquals(1, exitCode)
        assertEquals("$worktreesRoot/TASK-123\n", out.toString())
        assertTrue(err.contains("npm install") && err.contains("exit code 4") && err.contains("ENOTFOUND"), err.toString())
    }

    @Test
    fun `provision check reports a stale copy and exits with 3 without changing anything`() {
        val fileManager = FakeFileManager(existing = listOf(base, "$base/local.properties", "$merged/local.properties", "$base/token.txt", "$merged/.env", "$merged/token.txt"))
        fileManager.contents["$base/.env"] = "KEY=1"
        fileManager.contents["$base/local.properties"] = "A=1"
        fileManager.contents["$merged/local.properties"] = "A=1"
        fileManager.contents["$merged/.env"] = "KEY=0"
        fileManager.symbolicLinks["$merged/token.txt"] = "$base/token.txt"

        val exitCode = cli(linkingExecutor(), provisioning = linkingProvisioning, fileManager = fileManager, workingDirectory = merged)
            .run(listOf("provision", "--check"))

        assertEquals(3, exitCode, err.toString())
        assertEquals(".env: differs\n", out.toString())
        assertTrue(fileManager.copied.isEmpty())
    }

    @Test
    fun `provision check prints nothing and exits with 0 when everything matches`() {
        val fileManager = FakeFileManager(existing = listOf(base, "$base/local.properties", "$merged/local.properties", "$base/token.txt", "$merged/.env", "$merged/token.txt"))
        fileManager.contents["$base/.env"] = "KEY=1"
        fileManager.contents["$base/local.properties"] = "A=1"
        fileManager.contents["$merged/local.properties"] = "A=1"
        fileManager.contents["$merged/.env"] = "KEY=1"
        fileManager.symbolicLinks["$merged/token.txt"] = "$base/token.txt"

        val exitCode = cli(linkingExecutor(), provisioning = linkingProvisioning, fileManager = fileManager, workingDirectory = merged)
            .run(listOf("provision", "--check"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("", out.toString())
    }

    @Test
    fun `provision check refuses the main worktree`() {
        val exitCode = cli(linkingExecutor(), provisioning = linkingProvisioning).run(listOf("provision", "--check"))

        assertEquals(1, exitCode)
        assertTrue(err.contains("is not a linked worktree"), err.toString())
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

    private fun statusUnreadable(path: String) = repositoryExecutor { command, directory ->
        if (directory == path && "status" in command) Executor.fail(command, "fatal: not a git repository: $path/.git", 128) else null
    }

    @Test
    fun `status says why a worktree's status is unavailable`() {
        val exitCode = cli(statusUnreadable(merged)).run(listOf("status"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(
            out.contains("$merged  feature/TASK-1  origin=shogunai  status-unavailable (fatal: not a git repository: $merged/.git)\n"),
            out.toString(),
        )
    }

    @Test
    fun `clean lists the worktrees it skipped because their status couldn't be read`() {
        val exitCode = cli(statusUnreadable(merged)).run(listOf("clean"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(
            "skipped $merged (status unavailable: fatal: not a git repository: $merged/.git)\nNothing to clean up.\n",
            out.toString(),
        )
    }

    @Test
    fun `the dry run hint repeats the given flags and names only what would be removed`() {
        val orphan = FakeDerivedDataSource.derivedData("Gone-hash", "/home/dev/projects/gone/App.xcodeproj")
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(xcode = XcodeProvider()))),
        )
        val executor = repositoryExecutor { command, directory ->
            if ("rev-list" in command && directory == merged) Executor.ok(command, "0\t1") else null
        }

        cli(executor, provisioning = provisioning, derivedDataSource = FakeDerivedDataSource(existing = listOf(orphan)))
            .run(listOf("clean", "--delete-branches", "--delete-derived-data"))

        assertTrue(
            out.endsWith("Run 'shogun clean --delete-branches --delete-derived-data --yes' to remove the orphaned DerivedData.\n"),
            out.toString(),
        )
        assertFalse(out.contains("worktree."), out.toString())
    }

    @Test
    fun `status shows worktrees inside Herdr's folder as created by Herdr`() {
        val exitCode = cli(repositoryExecutor(), herdrWorktreesRoot = worktreesRoot).run(listOf("status"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(out.lines().contains("$merged  feature/TASK-1  origin=herdr  changes=0  merged"), out.toString())
    }

    private fun claudeProcessExecutor() = repositoryExecutor { command, _ ->
        when (command.first()) {
            "lsof" -> Executor.ok(command, "p4242\ncclaude\nfcwd\nn$merged\n")
            "ps" -> Executor.ok(command, "4242 /home/dev/.local/bin/claude\n")
            else -> null
        }
    }

    private fun blockedSession() = FakeAgentSessionStore(
        mapOf(merged to listOf(AgentSessionRecord("s1", "claude", AgentState.BLOCKED, now.toEpochMilli(), null, 4242L))),
    )

    @Test
    fun `status shows the state Claude Code's hooks report`() {
        val exitCode = cli(claudeProcessExecutor(), sessionStore = blockedSession()).run(listOf("status"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(
            out.lines().contains("$merged  feature/TASK-1  origin=shogunai  agents=claude(blocked)  changes=0  merged"),
            out.toString(),
        )
    }

    @Test
    fun `status json reports the state and its source`() {
        cli(claudeProcessExecutor(), sessionStore = blockedSession()).run(listOf("status", "--json"))

        val worktrees = Json.parseToJsonElement(out.toString()).jsonObject.getValue("worktrees").jsonArray
        assertEquals(
            Json.parseToJsonElement("""[{"name": "claude", "state": "blocked", "stateSource": "claude-code", "herdrState": null}]"""),
            worktrees[1].jsonObject.getValue("agents"),
        )
    }

    @Test
    fun `status shows the state Herdr reports for each agent`() {
        val herdr = FakeHerdrClient(agents = listOf(HerdrAgent("claude", AgentState.BLOCKED, merged, "w1:p1")))

        val exitCode = cli(repositoryExecutor(), herdr = herdr).run(listOf("status"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(
            out.lines().contains("$merged  feature/TASK-1  origin=shogunai  agents=claude(blocked)  changes=0  merged"),
            out.toString(),
        )
    }

    @Test
    fun `status json adds the agents with their Herdr state`() {
        val herdr = FakeHerdrClient(
            agents = listOf(
                HerdrAgent("claude", AgentState.BLOCKED, merged, "w1:p1"),
                HerdrAgent("aider", null, dirty, "w2:p1"),
            ),
        )

        val exitCode = cli(repositoryExecutor(), herdr = herdr).run(listOf("status", "--json"))

        assertEquals(0, exitCode, err.toString())
        val worktrees = Json.parseToJsonElement(out.toString()).jsonObject.getValue("worktrees").jsonArray
        assertEquals(
            Json.parseToJsonElement("""[{"name": "claude", "state": "blocked", "stateSource": "herdr", "herdrState": "blocked"}]"""),
            worktrees[1].jsonObject.getValue("agents"),
        )
        assertEquals(
            Json.parseToJsonElement("""[{"name": "aider", "state": null, "stateSource": null, "herdrState": null}]"""),
            worktrees[2].jsonObject.getValue("agents"),
        )
    }

    @Test
    fun `status shows a branch that never had commits as not started instead of merged`() {
        val executor = repositoryExecutor { command, directory ->
            when {
                command == listOf("git", "rev-parse", "HEAD") && directory == merged -> Executor.ok(command, "2222\n")
                command.take(3) == listOf("git", "reflog", "show") && directory == merged -> Executor.ok(command, "2222\n")
                else -> null
            }
        }

        val exitCode = cli(executor).run(listOf("status"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(out.lines().contains("$merged  feature/TASK-1  origin=shogunai  changes=0  not-started"), out.toString())
    }

    @Test
    fun `status json reports when a worktree was created`() {
        val fileManager = FakeFileManager(existing = listOf(base, "$base/local.properties"))
        fileManager.writtenTexts["$merged/.git"] = "gitdir: $base/.git/worktrees/TASK-1\n"
        fileManager.modifiedAt["$base/.git/worktrees/TASK-1/commondir"] = 1_760_000_000_000

        val exitCode = cli(repositoryExecutor(), fileManager = fileManager).run(listOf("status", "--json"))

        assertEquals(0, exitCode, err.toString())
        val worktrees = Json.parseToJsonElement(out.toString()).jsonObject.getValue("worktrees").jsonArray
        assertEquals("2025-10-09T08:53:20Z", worktrees[1].jsonObject.getValue("status").jsonObject.getValue("createdAt").jsonPrimitive.content)
        assertEquals(JsonNull, worktrees[0].jsonObject.getValue("status").jsonObject.getValue("createdAt"))
    }

    @Test
    fun `status json prints one compact document`() {
        val exitCode = cli(repositoryExecutor()).run(listOf("status", "--json"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(1, out.lines().count { it.isNotEmpty() })
        assertTrue(out.endsWith("\n"))
        val expected = Json.parseToJsonElement(
            """
            {
              "repository": "$base",
              "mainBranch": "main",
              "worktrees": [
                {
                  "path": "$base", "branch": "main", "head": "1111111111111111111111111111111111111111",
                  "main": true, "locked": false, "folderMissing": false, "origin": null, "agents": [],
                  "status": {
                    "uncommittedChanges": 0, "upstream": null, "mainBranch": null,
                    "merged": false, "lastCommitAt": null, "createdAt": null
                  }
                },
                {
                  "path": "$merged", "branch": "feature/TASK-1", "head": "2222222222222222222222222222222222222222",
                  "main": false, "locked": false, "folderMissing": false, "origin": "shogunai", "agents": [],
                  "status": {
                    "uncommittedChanges": 0, "upstream": null, "mainBranch": {"ahead": 0, "behind": 3},
                    "merged": true, "lastCommitAt": null, "createdAt": null
                  }
                },
                {
                  "path": "$dirty", "branch": "worktree-spike", "head": "3333333333333333333333333333333333333333",
                  "main": false, "locked": true, "folderMissing": false, "origin": "claude-code", "agents": [],
                  "status": {
                    "uncommittedChanges": 2, "upstream": null, "mainBranch": {"ahead": 2, "behind": 0},
                    "merged": false, "lastCommitAt": null, "createdAt": null
                  }
                }
              ]
            }
            """,
        )
        assertEquals(expected, Json.parseToJsonElement(out.toString()))
    }

    @Test
    fun `status json reports missing folders detached heads and Herdr origin`() {
        val executor = Executor { command, directory ->
            if (command.take(3) == listOf("git", "worktree", "list")) {
                Executor.ok(
                    command,
                    """
                    worktree $base
                    HEAD 1111111111111111111111111111111111111111
                    branch refs/heads/main

                    worktree $merged
                    HEAD 2222222222222222222222222222222222222222
                    detached

                    worktree $dirty
                    HEAD 3333333333333333333333333333333333333333
                    branch refs/heads/worktree-spike
                    prunable gitdir file points to non-existent location
                    """.trimIndent(),
                )
            } else {
                runBlocking { repositoryExecutor().execute(command, directory) }
            }
        }

        val exitCode = cli(executor, herdrWorktreesRoot = worktreesRoot).run(listOf("status", "--json"))

        assertEquals(0, exitCode, err.toString())
        val worktrees = Json.parseToJsonElement(out.toString()).jsonObject.getValue("worktrees").jsonArray.map { it.jsonObject }
        assertEquals(JsonNull, worktrees[1].getValue("branch"))
        assertEquals(JsonPrimitive("herdr"), worktrees[1].getValue("origin"))
        assertEquals(JsonPrimitive(true), worktrees[2].getValue("folderMissing"))
        assertEquals(JsonNull, worktrees[2].getValue("status"))
    }

    @Test
    fun `status json prints nothing on standard output outside a repository`() {
        val executor = Executor { command, _ -> Executor.fail(command, "fatal: not a git repository", 128) }

        val exitCode = cli(executor, workingDirectory = "/home/dev").run(listOf("status", "--json"))

        assertEquals(1, exitCode)
        assertEquals("", out.toString())
        assertTrue(err.startsWith("shogun: "), err.toString())
    }

    @Test
    fun `clean without yes lists the Herdr workspaces it would close`() {
        val herdr = FakeHerdrClient(openWorkspaces = mapOf(merged to "w2"))

        val exitCode = cli(repositoryExecutor(), herdr = herdr).run(listOf("clean"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(
            "would remove $merged\nwould close Herdr workspace of $merged\nRun 'shogun clean --yes' to remove the worktree.\n",
            out.toString(),
        )
        assertTrue(herdr.closedWorkspaces.isEmpty())
    }

    @Test
    fun `clean without yes prints nothing about Herdr when it isn't available`() {
        val herdr = FakeHerdrClient(listFailure = WorktreeError.HerdrNotInstalled)

        cli(repositoryExecutor(), herdr = herdr).run(listOf("clean"))

        assertEquals("would remove $merged\nRun 'shogun clean --yes' to remove the worktree.\n", out.toString())
    }

    @Test
    fun `clean with yes closes the Herdr workspaces of the removed worktrees`() {
        val herdr = FakeHerdrClient(openWorkspaces = mapOf(merged to "w2"))

        val exitCode = cli(repositoryExecutor(), herdr = herdr).run(listOf("clean", "--yes"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(listOf("w2"), herdr.closedWorkspaces)
    }

    @Test
    fun `clean with yes reports a failing Herdr close and still exits with 0`() {
        val failure = WorktreeError.HerdrCommandFailed(listOf("herdr"), "workspace w2 not found")
        val herdr = FakeHerdrClient(openWorkspaces = mapOf(merged to "w2"), closeFailures = mapOf("w2" to failure))

        val exitCode = cli(repositoryExecutor(), herdr = herdr).run(listOf("clean", "--yes"))

        assertEquals(0, exitCode)
        assertTrue(err.contains("shogun: couldn't close the Herdr workspace of $merged: workspace w2 not found\n"), err.toString())
    }

    @Test
    fun `clean without yes only lists the candidates`() {
        val executor = repositoryExecutor()

        val exitCode = cli(executor).run(listOf("clean"))

        assertEquals(0, exitCode)
        assertEquals("would remove $merged\nRun 'shogun clean --yes' to remove the worktree.\n", out.toString())
        assertFalse(executor.calls.any { it.first.take(3) == listOf("git", "worktree", "remove") })
    }

    @Test
    fun `clean stops the emulators of removed worktrees`() {
        val adminOfMerged = "$base/.git/worktrees/TASK-1"
        environmentStore.portsByAdminDirectory[adminOfMerged] = mapOf("emulator" to 5556)
        environmentStore.portsByAdminDirectory["$base/.git/worktrees/spike"] = mapOf("emulator" to 5558)
        androidSdk.states["emulator-5556"] = EmulatorState.RUNNING
        androidSdk.states["emulator-5558"] = EmulatorState.RUNNING
        val executor = repositoryExecutor { command, directory ->
            if ("--git-dir" in command) {
                val admin = when (directory) {
                    merged -> adminOfMerged
                    dirty -> "$base/.git/worktrees/spike"
                    else -> "$base/.git"
                }
                Executor.ok(command, "$admin\n$base/.git\n")
            } else {
                null
            }
        }
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(emulator = EmulatorProvider()))),
        )

        cli(executor, provisioning = provisioning).run(listOf("clean", "--stop-emulators"))
        assertTrue(out.contains("would stop emulator emulator-5556\n"), out.toString())
        assertTrue(androidSdk.stopped.isEmpty())
        out.clear()

        val exitCode = cli(executor, provisioning = provisioning).run(listOf("clean", "--yes", "--stop-emulators"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(out.contains("stopped emulator emulator-5556\n"), out.toString())
        assertEquals(listOf("emulator-5556"), androidSdk.stopped)
    }

    @Test
    fun `clean deletes the simulators of removed worktrees and orphans`() {
        environmentStore.resourcesByAdminDirectory["$base/.git/worktrees/TASK-1"] = mapOf("ios-simulator" to "SIM-MERGED")
        environmentStore.resourcesByAdminDirectory["$base/.git/worktrees/spike"] = mapOf("ios-simulator" to "SIM-SPIKE")
        appleSimulators.addExisting("SIM-MERGED", "ShogunAi · main-repo · TASK-1")
        appleSimulators.addExisting("SIM-SPIKE", "ShogunAi · main-repo · spike")
        appleSimulators.addExisting("SIM-ORPHAN", "ShogunAi · main-repo · TASK-0")
        appleSimulators.addExisting("SIM-USER", "iPhone 16")
        val executor = repositoryExecutor { command, directory ->
            if ("--git-dir" in command) {
                val admin = when (directory) {
                    merged -> "$base/.git/worktrees/TASK-1"
                    dirty -> "$base/.git/worktrees/spike"
                    else -> "$base/.git"
                }
                Executor.ok(command, "$admin\n$base/.git\n")
            } else {
                null
            }
        }
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(iosSimulator = IosSimulatorProvider()))),
        )

        cli(executor, provisioning = provisioning).run(listOf("clean", "--delete-simulators"))
        assertTrue(out.contains("would delete simulator ShogunAi · main-repo · TASK-1 (SIM-MERGED)"), out.toString())
        assertTrue(out.contains("would delete orphaned simulator ShogunAi · main-repo · TASK-0 (SIM-ORPHAN)"), out.toString())
        assertTrue(appleSimulators.deleted.isEmpty())
        out.clear()

        val exitCode = cli(executor, provisioning = provisioning).run(listOf("clean", "--yes", "--delete-simulators"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(listOf("SIM-MERGED", "SIM-ORPHAN"), appleSimulators.deleted)
        assertTrue(out.contains("deleted simulator SIM-ORPHAN"), out.toString())
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

    private fun agentRunningIn(path: String) = repositoryExecutor { command, _ ->
        when (command.first()) {
            "lsof" -> Executor.ok(command, "p4242\nn$path\n")
            "ps" -> Executor.ok(command, "4242 /usr/local/bin/claude\n")
            else -> null
        }
    }

    @Test
    fun `clean keeps a worktree where an agent is running and says which one`() {
        val executor = agentRunningIn(merged)

        val exitCode = cli(executor).run(listOf("clean", "--yes"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("kept $merged (claude is running)\nNothing to clean up.\n", out.toString())
        assertFalse(executor.calls.any { it.first.take(3) == listOf("git", "worktree", "remove") })
    }

    @Test
    fun `clean says when running agents couldn't be checked and goes on`() {
        val executor = repositoryExecutor { command, _ ->
            if (command.first() == "lsof") Executor.fail(command, "lsof: not found", 127) else null
        }

        val exitCode = cli(executor).run(listOf("clean"))

        assertEquals(0, exitCode)
        assertTrue(err.startsWith("shogun: couldn't check for running agents"), err.toString())
        assertTrue(out.contains("would remove $merged"), out.toString())
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
        assertTrue(out.contains("new <task-id> [--fix] [--from <ref>] [--fetch]"))
        out.clear()

        assertEquals(0, cli(repositoryExecutor()).run(listOf("--version")))
        assertEquals("shogun ${BuildInfo.VERSION}\n", out.toString())
    }

    @Test
    fun `help for a command prints only its usage and exits with 0`() {
        val exitCode = cli(repositoryExecutor()).run(listOf("new", "--help"))

        assertEquals(0, exitCode)
        assertTrue(out.startsWith("Usage: shogun new [options]\n"), out.toString())
        listOf("--fix", "--branch", "--from", "--fetch", "--worktrees-root").forEach { assertTrue(out.contains(it), it) }
        assertFalse(out.contains("provision"), out.toString())
        assertEquals("", err.toString())
    }

    @Test
    fun `the global help lists every command with aligned descriptions`() {
        cli(repositoryExecutor()).run(listOf("help"))

        val hookLines = out.lines().filter { it.trimStart().startsWith("hook ") }
        assertEquals(5, hookLines.size)
        assertEquals(1, hookLines.map { it.indexOf("  ", it.indexOf("hook") + 5).let { gap -> it.substring(gap).indexOfFirst { c -> c != ' ' } + gap } }.distinct().size)
    }

    @Test
    fun `usage errors exit with 2 and point to help`() {
        val exitCode = cli(repositoryExecutor()).run(listOf("frobnicate"))

        assertEquals(2, exitCode)
        assertEquals("shogun: unknown command 'frobnicate'\nRun 'shogun help' for usage.\n", err.toString())
    }

    @Test
    fun `xcode lists derived data for worktrees and orphans summary`() {
        val firstProject = "$base/iosApp/iosApp.xcodeproj"
        val secondProject = "$merged/iosApp/iosApp.xcodeproj"
        val orphanProject = "/home/dev/projects/gone/Gone.xcodeproj"
        val derivedData = FakeDerivedDataSource(
            existing = listOf(
                FakeDerivedDataSource.derivedData("Main-hash", firstProject, sizeBytes = 1_073_741_824L),
                FakeDerivedDataSource.derivedData("Merged-hash", secondProject, sizeBytes = 536_870_912L),
                FakeDerivedDataSource.derivedData("Gone-hash", orphanProject, sizeBytes = 2_147_483_648L),
            ),
            existingProjects = setOf(firstProject, secondProject),
        )
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(xcode = XcodeProvider()))),
        )
        val fileManager = FakeFileManager(existing = listOf(base, merged))

        val exitCode = cli(repositoryExecutor(), fileManager = fileManager, provisioning = provisioning, derivedDataSource = derivedData)
            .run(listOf("xcode"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(
            """
            $base  1.0 GB
            $merged  512.0 MB
            orphans: 1 (2.0 GB)
            """.trimIndent() + "\n",
            out.toString(),
        )
    }

    @Test
    fun `xcode reports provider is not active when not declared`() {
        val exitCode = cli(repositoryExecutor()).run(listOf("xcode"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("The Xcode provider isn't active.\n", out.toString())
    }

    @Test
    fun `xcode lists compilation cache size when declared`() {
        val derivedData = FakeDerivedDataSource(existing = emptyList(), cacheBytes = 1_073_741_824L)
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(xcode = XcodeProvider(compilationCache = true)))),
        )

        val exitCode = cli(repositoryExecutor(), provisioning = provisioning, derivedDataSource = derivedData)
            .run(listOf("xcode"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(
            """
            orphans: 0 (0 B)
            compilation cache: 1.0 GB
            """.trimIndent() + "\n",
            out.toString(),
        )
    }

    @Test
    fun `xcode lists folders on unavailable volumes`() {
        val folder = FakeDerivedDataSource.derivedData("Vol-hash", "/Volumes/Work/App.xcodeproj")
        val derivedData = FakeDerivedDataSource(
            existing = listOf(folder),
            unavailableVolumes = setOf("/Volumes/Work"),
        )
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(xcode = XcodeProvider()))),
        )

        val exitCode = cli(repositoryExecutor(), provisioning = provisioning, derivedDataSource = derivedData)
            .run(listOf("xcode"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(
            """
            orphans: 0 (0 B)
            folders on unavailable volumes: 1
            """.trimIndent() + "\n",
            out.toString(),
        )
    }

    @Test
    fun `xcode clean deletes orphaned derived data folders and reports space freed`() {
        val first = FakeDerivedDataSource.derivedData("Gone1-hash", "/home/dev/projects/gone1/App.xcodeproj", 1_073_741_824L)
        val second = FakeDerivedDataSource.derivedData("Gone2-hash", "/home/dev/projects/gone2/App.xcodeproj", 1_073_741_824L)
        val derivedData = FakeDerivedDataSource(existing = listOf(first, second))
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(xcode = XcodeProvider()))),
        )

        val exitCode = cli(repositoryExecutor(), provisioning = provisioning, derivedDataSource = derivedData)
            .run(listOf("xcode", "clean"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(listOf(first.path, second.path), derivedData.deleted)
        assertEquals(
            """
            deleted ${first.path}
            deleted ${second.path}
            freed 2.0 GB
            """.trimIndent() + "\n",
            out.toString(),
        )
    }

    @Test
    fun `xcode clean reports nothing to delete when no folder is orphaned`() {
        val derivedData = FakeDerivedDataSource(existing = emptyList())
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(xcode = XcodeProvider()))),
        )

        val exitCode = cli(repositoryExecutor(), provisioning = provisioning, derivedDataSource = derivedData)
            .run(listOf("xcode", "clean"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("Nothing to delete.\n", out.toString())
        assertTrue(derivedData.deleted.isEmpty())
    }

    @Test
    fun `xcode clean reports deletion failures on stderr and exits with 1`() {
        val orphan = FakeDerivedDataSource.derivedData("Stuck-hash", "/home/dev/projects/gone/App.xcodeproj")
        val derivedData = FakeDerivedDataSource(existing = listOf(orphan), failDeletionFor = setOf(orphan.path))
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(xcode = XcodeProvider()))),
        )

        val exitCode = cli(repositoryExecutor(), provisioning = provisioning, derivedDataSource = derivedData)
            .run(listOf("xcode", "clean"))

        assertEquals(1, exitCode)
        assertEquals("shogun: couldn't delete ${orphan.path}: can't delete ${orphan.path}\n", err.toString())
    }

    private fun trimSetup(
        quotaGigabytes: Int?,
        vararg folders: app.luxion.shogunai.domain.model.DerivedDataFolder,
        mode: DerivedDataQuotaMode? = null,
        freeBytes: Long? = null,
    ): Pair<FakeDerivedDataSource, FakeRepositoryProvisioningSource> {
        resourceLimits.settings = ResourceSettings(derivedDataQuotaGigabytes = quotaGigabytes, derivedDataQuotaMode = mode)
        val derivedData = FakeDerivedDataSource(existing = folders.toList(), existingProjects = setOf("$base/App.xcodeproj"), free = freeBytes)
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(xcode = XcodeProvider()))),
        )
        return derivedData to provisioning
    }

    @Test
    fun `xcode trim releases orphans over the quota and reports usage`() {
        val gigabyte = 1_073_741_824L
        val orphan = FakeDerivedDataSource.derivedData("Gone-hash", "/home/dev/projects/gone/App.xcodeproj", 15 * gigabyte)
        val live = FakeDerivedDataSource.derivedData("Live-hash", "$base/App.xcodeproj", 10 * gigabyte)
        val (derivedData, provisioning) = trimSetup(12, orphan, live)

        val exitCode = cli(repositoryExecutor(), provisioning = provisioning, derivedDataSource = derivedData)
            .run(listOf("xcode", "trim"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(listOf(orphan.path), derivedData.deleted)
        assertEquals(
            """
            released ${orphan.path}  15.0 GB
            freed 15.0 GB
            usage 10.0 GB of 12.0 GB
            """.trimIndent() + "\n",
            out.toString(),
        )
    }

    @Test
    fun `xcode trim dry run lists candidates and deletes nothing`() {
        val gigabyte = 1_073_741_824L
        val orphan = FakeDerivedDataSource.derivedData("Gone-hash", "/home/dev/projects/gone/App.xcodeproj", 15 * gigabyte)
        val (derivedData, provisioning) = trimSetup(5, orphan)

        val exitCode = cli(repositoryExecutor(), provisioning = provisioning, derivedDataSource = derivedData)
            .run(listOf("xcode", "trim", "--dry-run"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(derivedData.deleted.isEmpty())
        assertTrue(out.toString().startsWith("would release ${orphan.path}  15.0 GB\nwould free 15.0 GB\n"), out.toString())
    }

    @Test
    fun `xcode trim applies the automatic quota computed from the free disk space`() {
        val gigabyte = 1_073_741_824L
        val orphan = FakeDerivedDataSource.derivedData("Gone-hash", "/home/dev/projects/gone/App.xcodeproj", 30 * gigabyte)
        val live = FakeDerivedDataSource.derivedData("Live-hash", "$base/App.xcodeproj", 30 * gigabyte)
        val (derivedData, provisioning) = trimSetup(null, orphan, live, freeBytes = 100 * gigabyte)

        val exitCode = cli(repositoryExecutor(), provisioning = provisioning, derivedDataSource = derivedData)
            .run(listOf("xcode", "trim"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(listOf(orphan.path), derivedData.deleted)
        assertTrue(out.toString().endsWith("usage 30.0 GB of 40.0 GB (automatic)\n"), out.toString())
    }

    @Test
    fun `xcode trim json reports the automatic quota mode and size`() {
        val gigabyte = 1_073_741_824L
        val live = FakeDerivedDataSource.derivedData("Live-hash", "$base/App.xcodeproj", 30 * gigabyte)
        val (derivedData, provisioning) = trimSetup(null, live, freeBytes = 100 * gigabyte)

        val exitCode = cli(repositoryExecutor(), provisioning = provisioning, derivedDataSource = derivedData)
            .run(listOf("xcode", "trim", "--json"))

        assertEquals(0, exitCode, err.toString())
        val json = Json.parseToJsonElement(out.toString()).jsonObject
        assertEquals("automatic", json.getValue("quotaMode").jsonPrimitive.content)
        assertEquals((130 * gigabyte / 4).toString(), json.getValue("quotaBytes").jsonPrimitive.content)
    }

    @Test
    fun `xcode trim says the automatic quota is unknown when the free space can't be read`() {
        val orphan = FakeDerivedDataSource.derivedData("Gone-hash", "/home/dev/projects/gone/App.xcodeproj")
        val (derivedData, provisioning) = trimSetup(null, orphan)

        val exitCode = cli(repositoryExecutor(), provisioning = provisioning, derivedDataSource = derivedData)
            .run(listOf("xcode", "trim"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(derivedData.deleted.isEmpty())
        assertEquals("The automatic DerivedData quota couldn't be computed: the free disk space is unknown.\n", out.toString())
    }

    @Test
    fun `xcode trim says no quota is set and deletes nothing`() {
        val orphan = FakeDerivedDataSource.derivedData("Gone-hash", "/home/dev/projects/gone/App.xcodeproj")
        val (derivedData, provisioning) = trimSetup(50, orphan, mode = DerivedDataQuotaMode.NONE)

        val exitCode = cli(repositoryExecutor(), provisioning = provisioning, derivedDataSource = derivedData)
            .run(listOf("xcode", "trim"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(derivedData.deleted.isEmpty())
        assertTrue(out.toString().startsWith("No DerivedData quota is set"), out.toString())
    }

    @Test
    fun `xcode trim reports that the quota is still exceeded when only foreign folders remain`() {
        val gigabyte = 1_073_741_824L
        val other = FakeDerivedDataSource.derivedData("Other-hash", "/home/dev/projects/other/Other.xcodeproj", 20 * gigabyte)
        val (derivedData, provisioning) = trimSetup(5, other)
        derivedData.existingProjects = derivedData.existingProjects + "/home/dev/projects/other/Other.xcodeproj"

        val exitCode = cli(repositoryExecutor(), provisioning = provisioning, derivedDataSource = derivedData)
            .run(listOf("xcode", "trim"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(derivedData.deleted.isEmpty())
        assertEquals("usage 20.0 GB of 5.0 GB\nquota still exceeded\n", out.toString())
    }

    @Test
    fun `xcode trim reports the provider is not active when not declared`() {
        resourceLimits.settings = ResourceSettings(derivedDataQuotaGigabytes = 5)

        val exitCode = cli(repositoryExecutor()).run(listOf("xcode", "trim"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("The Xcode provider isn't active.\n", out.toString())
    }

    @Test
    fun `clean with delete derived data removes worktrees and deletes their derived data`() {
        val mergedProject = "$merged/iosApp/iosApp.xcodeproj"
        val folder = FakeDerivedDataSource.derivedData("Merged-hash", mergedProject)
        val derivedData = FakeDerivedDataSource(
            existing = listOf(folder),
            existingProjects = setOf(mergedProject),
        )
        val executor = repositoryExecutor { command, _ ->
            if (command.take(3) == listOf("git", "worktree", "remove")) {
                derivedData.existingProjects -= mergedProject
                Executor.ok(command)
            } else {
                null
            }
        }
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(xcode = XcodeProvider()))),
        )

        val exitCode = cli(executor, provisioning = provisioning, derivedDataSource = derivedData)
            .run(listOf("clean", "--yes", "--delete-derived-data"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(out.contains("removed $merged\n"), out.toString())
        assertTrue(out.contains("deleted DerivedData ${folder.path}\n"), out.toString())
        assertEquals(listOf(folder.path), derivedData.deleted)
    }

    @Test
    fun `clean without delete derived data touches no derived data`() {
        val mergedProject = "$merged/iosApp/iosApp.xcodeproj"
        val folder = FakeDerivedDataSource.derivedData("Merged-hash", mergedProject)
        val derivedData = FakeDerivedDataSource(existing = listOf(folder))
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(xcode = XcodeProvider()))),
        )

        cli(repositoryExecutor(), provisioning = provisioning, derivedDataSource = derivedData)
            .run(listOf("clean", "--yes"))

        assertTrue(derivedData.deleted.isEmpty())
    }

    @Test
    fun `clean with delete derived data without yes lists orphans that would be deleted`() {
        val orphan = FakeDerivedDataSource.derivedData("Gone-hash", "/home/dev/projects/gone/App.xcodeproj")
        val derivedData = FakeDerivedDataSource(existing = listOf(orphan))
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(xcode = XcodeProvider()))),
        )

        val exitCode = cli(repositoryExecutor(), provisioning = provisioning, derivedDataSource = derivedData)
            .run(listOf("clean", "--delete-derived-data"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(out.contains("would delete orphaned DerivedData ${orphan.path}\n"), out.toString())
        assertTrue(derivedData.deleted.isEmpty())
    }

    @Test
    fun `new runs and approves the commands when the user answers yes`() {
        val executor = linkingExecutor()
        val trust = FakeRepositoryTrust()

        val exitCode = cli(executor, provisioning = linkingProvisioning, trust = trust, answer = "y").run(listOf("new", "TASK-123"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(executor.calls.any { it.first == listOf("npm", "install") })
        assertTrue(trust.isApproved(base, RepositoryApprovalSubject(installCommands)))
        assertEquals("$worktreesRoot/TASK-123\n", out.toString())
        assertTrue(err.contains("npm install"), err.toString())
    }

    @Test
    fun `new skips the commands when the user answers no or just presses Enter`() {
        listOf("n", "").forEach { answer ->
            out.clear()
            err.clear()
            val executor = linkingExecutor()
            val trust = FakeRepositoryTrust()

            val exitCode = cli(executor, provisioning = linkingProvisioning, trust = trust, answer = answer).run(listOf("new", "TASK-123"))

            assertEquals(0, exitCode, err.toString())
            assertFalse(executor.calls.any { it.first == listOf("npm", "install") })
            assertFalse(trust.isApproved(base, RepositoryApprovalSubject(installCommands)))
            assertTrue(err.contains("shogun trust"), err.toString())
        }
    }

    @Test
    fun `new skips the commands with a note when there is no console to ask on`() {
        val executor = linkingExecutor()

        val exitCode = cli(executor, provisioning = linkingProvisioning, answer = null).run(listOf("new", "TASK-123"))

        assertEquals(0, exitCode, err.toString())
        assertFalse(executor.calls.any { it.first == listOf("npm", "install") })
        assertTrue(err.contains("skipped 1 post-creation command not approved on this machine"), err.toString())
        assertTrue(err.contains("shogun trust"), err.toString())
    }

    @Test
    fun `provision asks before running unapproved commands`() {
        val executor = linkingExecutor()
        val trust = FakeRepositoryTrust()

        val exitCode = cli(executor, provisioning = linkingProvisioning, workingDirectory = merged, trust = trust, answer = "yes")
            .run(listOf("provision"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(executor.calls.any { it.first == listOf("npm", "install") })
        assertTrue(trust.isApproved(base, RepositoryApprovalSubject(installCommands)))
    }

    @Test
    fun `trust shows the commands and approves them with --yes`() {
        val trust = FakeRepositoryTrust()

        val exitCode = cli(linkingExecutor(), provisioning = linkingProvisioning, trust = trust).run(listOf("trust", "--yes"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(out.contains("npm install"), out.toString())
        assertTrue(out.contains("not approved on this machine"), out.toString())
        assertTrue(trust.isApproved(base, RepositoryApprovalSubject(installCommands)))
    }

    @Test
    fun `trust asks on a console and approves only on yes`() {
        val declined = FakeRepositoryTrust()
        cli(linkingExecutor(), provisioning = linkingProvisioning, trust = declined, answer = "n").run(listOf("trust"))
        assertFalse(declined.isApproved(base, RepositoryApprovalSubject(installCommands)))

        val accepted = FakeRepositoryTrust()
        cli(linkingExecutor(), provisioning = linkingProvisioning, trust = accepted, answer = "y").run(listOf("trust"))
        assertTrue(accepted.isApproved(base, RepositoryApprovalSubject(installCommands)))
    }

    @Test
    fun `trust reports commands that are already approved`() {
        val exitCode = cli(linkingExecutor(), provisioning = linkingProvisioning, trust = approvedInstall).run(listOf("trust"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(out.contains("approved on this machine") && !out.contains("not approved"), out.toString())
    }

    @Test
    fun `trust --revoke removes the approval`() {
        val trust = approvedInstall

        val exitCode = cli(linkingExecutor(), provisioning = linkingProvisioning, trust = trust).run(listOf("trust", "--revoke"))

        assertEquals(0, exitCode, err.toString())
        assertFalse(trust.isApproved(base, RepositoryApprovalSubject(installCommands)))
    }

    private val portProvisioning = FakeRepositoryProvisioningSource(
        byRepository = mapOf(
            base to RepositoryProvisioning(environment = EnvironmentDeclaration(listOf(parseEnvironmentVariable("PORT", "\${port}")))),
        ),
    )

    private val portSubject = RepositoryApprovalSubject(variables = listOf("PORT" to "\${port}"))

    private fun newWorktreeExecutor() = repositoryExecutor { command, _ ->
        if ("--git-dir" in command) Executor.ok(command, "$base/.git/worktrees/TASK-123\n$base/.git\n") else null
    }

    @Test
    fun `trust shows declared variables with their templates and asks to approve them`() {
        val trust = FakeRepositoryTrust()

        val exitCode = cli(linkingExecutor(), provisioning = portProvisioning, trust = trust, answer = "y").run(listOf("trust"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(out.contains("Environment variables declared by $base:\n  PORT = \${port}\n"), out.toString())
        assertTrue(out.contains("not approved on this machine"), out.toString())
        assertTrue(err.contains("Approve them on this machine? [y/N]"), err.toString())
        assertTrue(trust.isApproved(base, portSubject))
    }

    @Test
    fun `trust json lists declared variables and the environment file`() {
        val exitCode = cli(linkingExecutor(), provisioning = portProvisioning).run(listOf("trust", "--json"))

        assertEquals(0, exitCode, err.toString())
        val json = jsonOut()
        val variable = json.getValue("env").jsonArray.single().jsonObject
        assertEquals("PORT", (variable.getValue("name") as JsonPrimitive).content)
        assertEquals("\${port}", (variable.getValue("template") as JsonPrimitive).content)
        assertEquals(JsonNull, json.getValue("envFile"))
        assertEquals(JsonPrimitive(false), json.getValue("approved"))
    }

    @Test
    fun `trust --yes approves the declared variables`() {
        val trust = FakeRepositoryTrust()

        cli(linkingExecutor(), provisioning = portProvisioning, trust = trust).run(listOf("trust", "--yes"))

        assertTrue(trust.isApproved(base, portSubject))
    }

    @Test
    fun `new prints declared variables before asking and answering no reserves no port`() {
        val exitCode = cli(newWorktreeExecutor(), provisioning = portProvisioning, answer = "n").run(listOf("new", "TASK-123"))

        assertEquals(0, exitCode, err.toString())
        val printed = err.indexOf("  PORT = \${port}")
        assertTrue(printed >= 0 && printed < err.indexOf("Approve them on this machine? [y/N]"), err.toString())
        assertTrue(environmentStore.portsByAdminDirectory.values.none { it.containsKey("PORT") })
        assertTrue(err.contains("skipped environment variables not approved on this machine (PORT)"), err.toString())
    }

    @Test
    fun `new reserves the declared port once approved on the terminal`() {
        val trust = FakeRepositoryTrust()

        val exitCode = cli(newWorktreeExecutor(), provisioning = portProvisioning, trust = trust, answer = "y").run(listOf("new", "TASK-123"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(trust.isApproved(base, portSubject))
        assertTrue(environmentStore.readPorts("$base/.git/worktrees/TASK-123").containsKey("PORT"))
    }

    @Test
    fun `new json lists skipped variables and nothing when approved`() {
        cli(newWorktreeExecutor(), provisioning = portProvisioning, answer = "y").run(listOf("new", "TASK-123", "--json"))
        assertEquals(listOf("PORT"), jsonOut().strings("skippedVariables"))
        out.clear()

        cli(newWorktreeExecutor(), provisioning = portProvisioning, trust = FakeRepositoryTrust(mapOf(base to portSubject)))
            .run(listOf("new", "TASK-456", "--json"))
        assertEquals(emptyList(), jsonOut().strings("skippedPostCreateCommands"))
        assertEquals(emptyList(), jsonOut().strings("skippedVariables"))
    }

    @Test
    fun `trust says so when the repository declares no commands`() {
        val exitCode = cli(linkingExecutor()).run(listOf("trust"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(out.contains("declares no post-creation commands"), out.toString())
    }

    @Test
    fun `trust rejects an unknown option and combining yes with revoke`() {
        assertEquals(2, cli(linkingExecutor()).run(listOf("trust", "--bogus")))
        assertEquals(2, cli(linkingExecutor()).run(listOf("trust", "--yes", "--revoke")))
    }

    private val emulatorProvisioning = FakeRepositoryProvisioningSource(
        byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(emulator = EmulatorProvider()))),
    )

    private fun executorWithAdminDirectories(extra: (List<String>, String?) -> CommandResult? = { _, _ -> null }) =
        repositoryExecutor { command, directory ->
            extra(command, directory) ?: if ("--git-dir" in command) {
                val admin = if (directory == merged) "$base/.git/worktrees/TASK-1" else "$base/.git"
                Executor.ok(command, "$admin\n$base/.git\n")
            } else {
                null
            }
        }

    @Test
    fun `open in herdr opens the worktree with its environment and the default agent`() {
        val herdr = FakeHerdrClient()
        val provisioning = FakeRepositoryProvisioningSource(
            byRepository = mapOf(
                base to RepositoryProvisioning(environment = EnvironmentDeclaration(listOf(parseEnvironmentVariable("APP", "app")))),
            ),
        )

        val trust = FakeRepositoryTrust(mapOf(base to RepositoryApprovalSubject(variables = listOf("APP" to "app"))))

        val exitCode = cli(executorWithAdminDirectories(), provisioning = provisioning, herdr = herdr, trust = trust)
            .run(listOf("open", "TASK-1", "--herdr"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("opened $merged in Herdr with Claude\n", out.toString())
        assertEquals(listOf(Triple(base, merged, "TASK-1")), herdr.opened)
        assertEquals(listOf(FakeHerdrClient.Split("w1:p1", merged, listOf("APP" to "app"))), herdr.splits)
        assertEquals(listOf("w1:p2" to "headroom wrap claude"), herdr.ran)
    }

    @Test
    fun `open in herdr focuses a worktree herdr already has open`() {
        val herdr = FakeHerdrClient(alreadyOpen = setOf(merged))

        val exitCode = cli(repositoryExecutor(), herdr = herdr).run(listOf("open", "feature/TASK-1", "--herdr"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("focused $merged in Herdr (already open)\n", out.toString())
        assertTrue(herdr.ran.isEmpty())
    }

    @Test
    fun `open in herdr runs the agent named with --agent`() {
        val herdr = FakeHerdrClient()
        val codex = AgentLaunchConfig(name = "Codex", agentCommand = "codex", useHeadroom = false)
        val withCodex = project.copy(config = project.config.copy(additionalAgents = listOf(codex)))

        val exitCode = cli(repositoryExecutor(), projects = listOf(withCodex), herdr = herdr)
            .run(listOf("open", "TASK-1", "--herdr", "--agent", "codex"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(listOf("w1:p1" to "codex"), herdr.ran)
    }

    @Test
    fun `open in herdr refuses an unknown agent and lists the project's agents`() {
        val herdr = FakeHerdrClient()

        val exitCode = cli(repositoryExecutor(), herdr = herdr).run(listOf("open", "TASK-1", "--herdr", "--agent", "missing"))

        assertEquals(2, exitCode)
        assertEquals("shogun: no agent named 'missing'; this project's agents are Claude\n", err.toString())
        assertTrue(herdr.opened.isEmpty())
    }

    @Test
    fun `open in herdr accepts the main worktree`() {
        val herdr = FakeHerdrClient()

        val exitCode = cli(repositoryExecutor(), herdr = herdr).run(listOf("open", base, "--herdr"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(listOf(Triple(base, base, "main-repo")), herdr.opened)
    }

    @Test
    fun `open in herdr prints json`() {
        val exitCode = cli(repositoryExecutor()).run(listOf("open", "TASK-1", "--herdr", "--json"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(
            Json.parseToJsonElement("""{"path": "$merged", "branch": "feature/TASK-1", "herdr": "opened", "agent": "Claude"}"""),
            Json.parseToJsonElement(out.toString()),
        )
    }

    @Test
    fun `open in herdr prints a null agent in json when only focused`() {
        val herdr = FakeHerdrClient(alreadyOpen = setOf(merged))

        cli(repositoryExecutor(), herdr = herdr).run(listOf("open", "TASK-1", "--herdr", "--json"))

        val result = Json.parseToJsonElement(out.toString()).jsonObject
        assertEquals(JsonPrimitive("focused"), result.getValue("herdr"))
        assertEquals(JsonNull, result.getValue("agent"))
    }

    @Test
    fun `open in herdr fails with an unknown target`() {
        val herdr = FakeHerdrClient()

        val exitCode = cli(repositoryExecutor(), herdr = herdr).run(listOf("open", "TASK-404", "--herdr"))

        assertEquals(1, exitCode)
        assertTrue(herdr.opened.isEmpty())
    }

    @Test
    fun `open in herdr says when herdr isn't installed`() {
        val herdr = FakeHerdrClient(failure = WorktreeError.HerdrNotInstalled)

        val exitCode = cli(repositoryExecutor(), herdr = herdr).run(listOf("open", "TASK-1", "--herdr", "--json"))

        assertEquals(1, exitCode)
        assertEquals("", out.toString())
        assertTrue(err.contains("https://herdr.dev"), err.toString())
    }

    @Test
    fun `remove by task id removes a clean worktree, keeps its branch and stops its emulator`() {
        environmentStore.portsByAdminDirectory["$base/.git/worktrees/TASK-1"] = mapOf("emulator" to 5556)
        androidSdk.states["emulator-5556"] = EmulatorState.RUNNING
        val executor = executorWithAdminDirectories()

        val exitCode = cli(executor, provisioning = emulatorProvisioning).run(listOf("remove", "TASK-1"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("removed $merged\nstopped emulator emulator-5556\n", out.toString())
        assertTrue(executor.ran("git", "worktree", "remove", merged))
        assertFalse(executor.calls.any { it.first.take(2) == listOf("git", "branch") })
        assertEquals(listOf("emulator-5556"), androidSdk.stopped)
    }

    @Test
    fun `remove by path and by branch resolve the same worktree`() {
        listOf(merged, "feature/TASK-1").forEach { target ->
            val executor = repositoryExecutor()

            val exitCode = cli(executor).run(listOf("remove", target))

            assertEquals(0, exitCode, err.toString())
            assertTrue(executor.ran("git", "worktree", "remove", merged), target)
        }
    }

    @Test
    fun `remove refuses a worktree with changes and suggests force`() {
        val executor = repositoryExecutor { command, _ ->
            if (command.take(3) == listOf("git", "worktree", "remove")) {
                Executor.fail(command, "fatal: '$merged' contains modified or untracked files, use --force to delete it")
            } else {
                null
            }
        }

        val exitCode = cli(executor, provisioning = emulatorProvisioning).run(listOf("remove", "TASK-1"))

        assertEquals(1, exitCode)
        assertEquals("shogun: $merged has uncommitted or untracked changes; use --force to remove it anyway\n", err.toString())
        assertEquals("", out.toString())
        assertTrue(androidSdk.stopped.isEmpty())
    }

    @Test
    fun `remove with force passes force to git`() {
        val executor = repositoryExecutor()

        val exitCode = cli(executor).run(listOf("remove", "TASK-1", "--force"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(executor.ran("git", "worktree", "remove", "--force", merged))
    }

    @Test
    fun `remove refuses a worktree with a running agent unless forced`() {
        val executor = agentRunningIn(merged)

        val exitCode = cli(executor).run(listOf("remove", "TASK-1"))

        assertEquals(1, exitCode)
        assertEquals("shogun: claude is running in $merged; use --force to remove it anyway\n", err.toString())
        assertFalse(executor.calls.any { it.first.take(3) == listOf("git", "worktree", "remove") })
        err.clear()

        assertEquals(0, cli(executor).run(listOf("remove", "TASK-1", "--force")), err.toString())
        assertTrue(executor.ran("git", "worktree", "remove", "--force", merged))
    }

    @Test
    fun `remove with delete-branch deletes a merged branch`() {
        val executor = repositoryExecutor()

        val exitCode = cli(executor).run(listOf("remove", "TASK-1", "--delete-branch"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("removed $merged\ndeleted branch feature/TASK-1\n", out.toString())
        assertTrue(executor.ran("git", "branch", "-D", "--", "feature/TASK-1"))
    }

    @Test
    fun `remove with delete-branch keeps a branch that isn't merged`() {
        val executor = repositoryExecutor { command, _ ->
            if (command.take(2) == listOf("git", "merge-base")) Executor.fail(command) else null
        }

        val exitCode = cli(executor).run(listOf("remove", "TASK-1", "--delete-branch"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("removed $merged\nkept branch feature/TASK-1 (not merged into the main branch)\n", out.toString())
        assertFalse(executor.ran("git", "branch", "-D", "--", "feature/TASK-1"))
    }

    @Test
    fun `remove with force-delete-branch deletes a branch that isn't merged`() {
        val executor = repositoryExecutor { command, _ ->
            if (command.take(2) == listOf("git", "merge-base")) Executor.fail(command) else null
        }

        val exitCode = cli(executor).run(listOf("remove", "TASK-1", "--force-delete-branch"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("removed $merged\ndeleted branch feature/TASK-1\n", out.toString())
        assertTrue(executor.ran("git", "branch", "-D", "--", "feature/TASK-1"))
    }

    @Test
    fun `remove json says why the branch was kept`() {
        val executor = repositoryExecutor { command, _ ->
            if (command.take(2) == listOf("git", "merge-base")) Executor.fail(command) else null
        }

        val exitCode = cli(executor).run(listOf("remove", "TASK-1", "--delete-branch", "--json"))

        assertEquals(0, exitCode, err.toString())
        val json = jsonOut()
        assertEquals(JsonNull, json.getValue("branchDeleted"))
        assertEquals("not merged into the main branch", (json.getValue("branchKeptReason") as JsonPrimitive).content)
    }

    @Test
    fun `remove fails without removing anything for an unknown target or the main worktree`() {
        val executor = repositoryExecutor()

        assertEquals(1, cli(executor).run(listOf("remove", "TASK-99")))
        assertEquals("shogun: No worktree matches \"TASK-99\"\n", err.toString())
        err.clear()

        assertEquals(1, cli(executor).run(listOf("remove", base)))
        assertTrue(err.contains("is the main worktree"), err.toString())
        assertFalse(executor.calls.any { it.first.take(3) == listOf("git", "worktree", "remove") })
    }

    @Test
    fun `remove lists the candidates when the target is ambiguous`() {
        val twin = "$worktreesRoot/TASK-2"
        val executor = repositoryExecutor { command, _ ->
            if (command.take(3) == listOf("git", "worktree", "list")) {
                Executor.ok(command, porcelain + "\n\nworktree $twin\nHEAD 4444444444444444444444444444444444444444\nbranch refs/heads/feature/TASK-1\n")
            } else {
                null
            }
        }

        val exitCode = cli(executor).run(listOf("remove", "feature/TASK-1"))

        assertEquals(1, exitCode)
        assertTrue(err.contains("  $merged\n") && err.contains("  $twin\n"), err.toString())
        assertFalse(executor.calls.any { it.first.take(3) == listOf("git", "worktree", "remove") })
    }

    @Test
    fun `repair re-links the worktrees that moved with the worktrees folder`() {
        val moved = "/home/dev/old-place/TASK-1"
        val executor = repositoryExecutor { command, _ ->
            if (command.take(3) == listOf("git", "worktree", "list")) {
                Executor.ok(command, porcelain.replace(merged, moved))
            } else {
                null
            }
        }
        val fileManager = FakeFileManager(existing = listOf(base, merged))

        val exitCode = cli(executor, fileManager = fileManager).run(listOf("repair"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("repaired $merged\n", out.toString())
        assertTrue(executor.ran("git", "worktree", "repair", merged))
    }

    @Test
    fun `repair says so when nothing moved`() {
        val exitCode = cli(repositoryExecutor()).run(listOf("repair"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("Nothing to repair.\n", out.toString())
    }

    private fun jsonOut(): kotlinx.serialization.json.JsonObject {
        val text = out.toString()
        assertTrue(text.endsWith("\n") && text.trimEnd('\n').lines().size == 1, text)
        return Json.parseToJsonElement(text).jsonObject
    }

    private fun kotlinx.serialization.json.JsonObject.strings(key: String) = getValue(key).jsonArray.map { (it as JsonPrimitive).content }

    @Test
    fun `new json reports the worktree and lists the unapproved commands without asking`() {
        val executor = linkingExecutor()

        val exitCode = cli(executor, provisioning = linkingProvisioning, answer = "y").run(listOf("new", "TASK-123", "--json"))

        assertEquals(0, exitCode, err.toString())
        val json = jsonOut()
        assertEquals(setOf("path", "branch", "created", "skippedPostCreateCommands", "skippedVariables", "postCreateFailure"), json.keys)
        assertEquals("$worktreesRoot/TASK-123", (json.getValue("path") as JsonPrimitive).content)
        assertEquals("feature/TASK-123", (json.getValue("branch") as JsonPrimitive).content)
        assertEquals(JsonPrimitive(true), json.getValue("created"))
        assertEquals(listOf("npm", "install"), json.getValue("skippedPostCreateCommands").jsonArray.single().jsonArray.map { (it as JsonPrimitive).content })
        assertEquals(JsonNull, json.getValue("postCreateFailure"))
        assertFalse(executor.calls.any { it.first.first() == "npm" })
        assertFalse(err.contains("Approve them"), err.toString())
    }

    @Test
    fun `new json is accepted before the task id too`() {
        val exitCode = cli(repositoryExecutor()).run(listOf("new", "--json", "TASK-123"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("$worktreesRoot/TASK-123", (jsonOut().getValue("path") as JsonPrimitive).content)
    }

    @Test
    fun `new json still prints the object when a post-creation command fails`() {
        val executor = linkingExecutor { Executor.fail(it, stderr = "ENOTFOUND", exitCode = 2) }

        val exitCode = cli(executor, provisioning = linkingProvisioning, trust = approvedInstall).run(listOf("new", "TASK-123", "--json"))

        assertEquals(1, exitCode)
        val json = jsonOut()
        assertEquals("$worktreesRoot/TASK-123", (json.getValue("path") as JsonPrimitive).content)
        val failure = json.getValue("postCreateFailure").jsonObject
        assertEquals(setOf("command", "exitCode", "stderr"), failure.keys)
        assertEquals(listOf("npm", "install"), failure.strings("command"))
        assertEquals(JsonPrimitive(2), failure.getValue("exitCode"))
        assertEquals("ENOTFOUND", (failure.getValue("stderr") as JsonPrimitive).content)
    }

    @Test
    fun `provision json lists the copied and linked files`() {
        val executor = linkingExecutor()
        val fileManager = FakeFileManager(existing = listOf(base, "$base/local.properties", "$base/token.txt"))

        val exitCode = cli(executor, provisioning = linkingProvisioning, fileManager = fileManager, workingDirectory = merged, trust = approvedInstall)
            .run(listOf("provision", "--json"))

        assertEquals(0, exitCode, err.toString())
        val json = jsonOut()
        assertEquals(
            setOf("path", "copied", "linked", "copiedInsteadOfLinking", "skippedPostCreateCommands", "skippedVariables", "postCreateFailure"),
            json.keys,
        )
        assertEquals(merged, (json.getValue("path") as JsonPrimitive).content)
        assertEquals(listOf("local.properties", ".env"), json.strings("copied"))
        assertEquals(listOf("token.txt"), json.strings("linked"))
        assertEquals(emptyList(), json.strings("copiedInsteadOfLinking"))
        assertTrue(executor.ran("npm", "install"))
    }

    @Test
    fun `provision json skips unapproved commands instead of asking`() {
        val executor = linkingExecutor()

        val exitCode = cli(executor, provisioning = linkingProvisioning, workingDirectory = merged, answer = "y")
            .run(listOf("provision", "--json"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(1, jsonOut().getValue("skippedPostCreateCommands").jsonArray.size)
        assertFalse(executor.calls.any { it.first.first() == "npm" })
        assertFalse(err.contains("Approve them"), err.toString())
    }

    @Test
    fun `provision json reports the failed command with empty file lists`() {
        val executor = linkingExecutor { Executor.fail(it, stderr = "ENOTFOUND", exitCode = 4) }

        val exitCode = cli(executor, provisioning = linkingProvisioning, workingDirectory = merged, trust = approvedInstall)
            .run(listOf("provision", "--json"))

        assertEquals(1, exitCode)
        assertEquals(JsonPrimitive(4), jsonOut().getValue("postCreateFailure").jsonObject.getValue("exitCode"))
    }

    @Test
    fun `provision check json lists the stale file and exits with 3`() {
        val fileManager = FakeFileManager(existing = listOf(base, "$base/local.properties", "$merged/local.properties", "$base/token.txt", "$merged/.env", "$merged/token.txt"))
        fileManager.contents["$base/.env"] = "KEY=1"
        fileManager.contents["$base/local.properties"] = "A=1"
        fileManager.contents["$merged/local.properties"] = "A=1"
        fileManager.contents["$merged/.env"] = "KEY=0"
        fileManager.symbolicLinks["$merged/token.txt"] = "$base/token.txt"

        val exitCode = cli(linkingExecutor(), provisioning = linkingProvisioning, fileManager = fileManager, workingDirectory = merged)
            .run(listOf("provision", "--check", "--json"))

        assertEquals(3, exitCode, err.toString())
        val json = jsonOut()
        assertEquals(setOf("path", "drift"), json.keys)
        val drift = json.getValue("drift").jsonArray.single().jsonObject
        assertEquals(setOf("path", "state"), drift.keys)
        assertEquals(".env", (drift.getValue("path") as JsonPrimitive).content)
        assertEquals("differs", (drift.getValue("state") as JsonPrimitive).content)
    }

    @Test
    fun `provision check json with nothing stale prints an empty list and exits with 0`() {
        val fileManager = FakeFileManager(existing = listOf(base, "$base/local.properties", "$merged/local.properties", "$base/token.txt", "$merged/.env", "$merged/token.txt"))
        fileManager.contents["$base/.env"] = "KEY=1"
        fileManager.contents["$base/local.properties"] = "A=1"
        fileManager.contents["$merged/local.properties"] = "A=1"
        fileManager.contents["$merged/.env"] = "KEY=1"
        fileManager.symbolicLinks["$merged/token.txt"] = "$base/token.txt"

        val exitCode = cli(linkingExecutor(), provisioning = linkingProvisioning, fileManager = fileManager, workingDirectory = merged)
            .run(listOf("provision", "--check", "--json"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(emptyList(), jsonOut().getValue("drift").jsonArray.toList())
    }

    @Test
    fun `trust json only reports commands that aren't approved`() {
        val trust = FakeRepositoryTrust()

        val exitCode = cli(linkingExecutor(), provisioning = linkingProvisioning, trust = trust, answer = "y").run(listOf("trust", "--json"))

        assertEquals(0, exitCode, err.toString())
        val json = jsonOut()
        assertEquals(setOf("repository", "commands", "env", "envFile", "approved", "changed"), json.keys)
        assertEquals(base, (json.getValue("repository") as JsonPrimitive).content)
        assertEquals(JsonPrimitive(false), json.getValue("approved"))
        assertEquals(JsonPrimitive(false), json.getValue("changed"))
        assertEquals(listOf("npm", "install"), json.getValue("commands").jsonArray.single().jsonArray.map { (it as JsonPrimitive).content })
        assertFalse(trust.isApproved(base, RepositoryApprovalSubject(installCommands)))
        assertFalse(err.contains("Approve them"), err.toString())
    }

    @Test
    fun `trust json with yes approves and says it changed`() {
        val trust = FakeRepositoryTrust()

        val exitCode = cli(linkingExecutor(), provisioning = linkingProvisioning, trust = trust).run(listOf("trust", "--yes", "--json"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(JsonPrimitive(true), jsonOut().getValue("approved"))
        assertEquals(JsonPrimitive(true), jsonOut().getValue("changed"))
        assertTrue(trust.isApproved(base, RepositoryApprovalSubject(installCommands)))
    }

    @Test
    fun `trust json reports already approved commands and revoking`() {
        val trust = approvedInstall

        cli(linkingExecutor(), provisioning = linkingProvisioning, trust = trust).run(listOf("trust", "--json"))
        assertEquals(JsonPrimitive(true), jsonOut().getValue("approved"))
        assertEquals(JsonPrimitive(false), jsonOut().getValue("changed"))
        out.clear()

        cli(linkingExecutor(), provisioning = linkingProvisioning, trust = trust).run(listOf("trust", "--revoke", "--json"))
        assertEquals(JsonPrimitive(false), jsonOut().getValue("approved"))
        assertEquals(JsonPrimitive(true), jsonOut().getValue("changed"))
        assertFalse(trust.isApproved(base, RepositoryApprovalSubject(installCommands)))
    }

    @Test
    fun `trust json without declared commands is approved with an empty list`() {
        val exitCode = cli(linkingExecutor()).run(listOf("trust", "--json"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(JsonPrimitive(true), jsonOut().getValue("approved"))
        assertEquals(emptyList(), jsonOut().getValue("commands").jsonArray.toList())
    }

    @Test
    fun `clean json dry run lists the candidates and removes nothing`() {
        val executor = repositoryExecutor()

        val exitCode = cli(executor).run(listOf("clean", "--json"))

        assertEquals(0, exitCode, err.toString())
        val json = jsonOut()
        assertEquals(
            setOf("dryRun", "candidates", "keptForAgents", "skipped", "removed", "deletedBranches", "failures", "providers"),
            json.keys,
        )
        assertEquals(JsonPrimitive(true), json.getValue("dryRun"))
        val candidate = json.getValue("candidates").jsonArray.single().jsonObject
        assertEquals(setOf("path", "branch", "reason"), candidate.keys)
        assertEquals(merged, (candidate.getValue("path") as JsonPrimitive).content)
        assertEquals("feature/TASK-1", (candidate.getValue("branch") as JsonPrimitive).content)
        assertEquals("merged", (candidate.getValue("reason") as JsonPrimitive).content)
        assertEquals(emptyList(), json.strings("removed"))
        assertEquals(
            setOf("gradleDaemonsStopped", "emulatorsStopped", "simulatorsDeleted", "derivedDataDeleted"),
            json.getValue("providers").jsonObject.keys,
        )
        assertFalse(executor.calls.any { it.first.take(3) == listOf("git", "worktree", "remove") })
    }

    @Test
    fun `clean json lists a worktree kept because an agent runs in it`() {
        val exitCode = cli(agentRunningIn(merged)).run(listOf("clean", "--json"))

        assertEquals(0, exitCode, err.toString())
        val json = jsonOut()
        assertEquals(emptyList(), json.getValue("candidates").jsonArray.toList())
        val kept = json.getValue("keptForAgents").jsonArray.single().jsonObject
        assertEquals(merged, (kept.getValue("path") as JsonPrimitive).content)
        assertEquals(listOf("claude"), kept.strings("agents"))
    }

    @Test
    fun `clean json lists the worktrees whose status couldn't be read`() {
        val executor = repositoryExecutor { command, directory ->
            if ("status" in command && directory == merged) Executor.fail(command, "fatal: not a git repository") else null
        }

        cli(executor).run(listOf("clean", "--json"))

        val skipped = jsonOut().getValue("skipped").jsonArray.single().jsonObject
        assertEquals(setOf("path", "reason"), skipped.keys)
        assertEquals(merged, (skipped.getValue("path") as JsonPrimitive).content)
    }

    @Test
    fun `clean json with yes reports the removal and the deleted branches`() {
        val executor = repositoryExecutor()

        val exitCode = cli(executor).run(listOf("clean", "--yes", "--delete-branches", "--json"))

        assertEquals(0, exitCode, err.toString())
        val json = jsonOut()
        assertEquals(JsonPrimitive(false), json.getValue("dryRun"))
        assertEquals(listOf(merged), json.strings("removed"))
        assertEquals(listOf("feature/TASK-1"), json.strings("deletedBranches"))
        assertTrue(executor.ran("git", "worktree", "remove", merged))
    }

    @Test
    fun `clean json reports the failures and exits with 1`() {
        val executor = repositoryExecutor { command, _ ->
            if (command.take(3) == listOf("git", "worktree", "remove")) Executor.fail(command, "is dirty") else null
        }

        val exitCode = cli(executor).run(listOf("clean", "--yes", "--json"))

        assertEquals(1, exitCode)
        val failure = jsonOut().getValue("failures").jsonArray.single().jsonObject
        assertEquals(setOf("path", "message"), failure.keys)
        assertEquals(merged, (failure.getValue("path") as JsonPrimitive).content)
        assertEquals(emptyList(), jsonOut().strings("removed"))
    }

    @Test
    fun `clean json with nothing to remove prints empty lists`() {
        val executor = repositoryExecutor { command, _ ->
            if (command.take(3) == listOf("git", "worktree", "list")) Executor.ok(command, porcelain.substringBefore("worktree $merged")) else null
        }

        val exitCode = cli(executor).run(listOf("clean", "--json"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(emptyList(), jsonOut().getValue("candidates").jsonArray.toList())
    }

    @Test
    fun `clean json stops the emulators of removed worktrees`() {
        environmentStore.portsByAdminDirectory["$base/.git/worktrees/TASK-1"] = mapOf("emulator" to 5556)
        androidSdk.states["emulator-5556"] = EmulatorState.RUNNING

        val exitCode = cli(executorWithAdminDirectories(), provisioning = emulatorProvisioning)
            .run(listOf("clean", "--yes", "--stop-emulators", "--json"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(listOf("emulator-5556"), jsonOut().getValue("providers").jsonObject.strings("emulatorsStopped"))
    }

    @Test
    fun `clean json outside a repository prints nothing on standard output`() {
        val executor = Executor { command, _ -> Executor.fail(command, "fatal: not a git repository", 128) }

        val exitCode = cli(executor, workingDirectory = "/tmp/elsewhere").run(listOf("clean", "--json"))

        assertEquals(1, exitCode)
        assertEquals("", out.toString())
        assertTrue(err.startsWith("shogun: "), err.toString())
    }

    @Test
    fun `gradle json lists the daemons with their state and owner`() {
        val daemons = FakeGradleDaemonSource(
            listOf(
                gradleDaemon(10, idleSinceTenMinutes, lastBuildDirectory = merged, memoryBytes = 1_073_741_824),
                gradleDaemon(11, DaemonState.Busy, lastBuildDirectory = "/home/dev/projects/gone", memoryBytes = 1_073_741_824),
                kotlinDaemon(12, memoryBytes = 536_870_912),
            ),
        )
        val fileManager = FakeFileManager(existing = listOf(base, merged))

        val exitCode = cli(repositoryExecutor(), fileManager = fileManager, daemons = daemons).run(listOf("gradle", "--json"))

        assertEquals(0, exitCode, err.toString())
        val json = jsonOut()
        assertEquals(setOf("daemons", "totalMemoryBytes"), json.keys)
        assertEquals(JsonPrimitive(2_684_354_560L), json.getValue("totalMemoryBytes"))
        val first = json.getValue("daemons").jsonArray[0].jsonObject
        assertEquals(setOf("pid", "kind", "version", "memoryBytes", "state", "idleSince", "worktree"), first.keys)
        assertEquals(JsonPrimitive(10), first.getValue("pid"))
        assertEquals("idle", (first.getValue("state") as JsonPrimitive).content)
        assertEquals(merged, (first.getValue("worktree") as JsonPrimitive).content)
        assertEquals("removed", (json.getValue("daemons").jsonArray[1].jsonObject.getValue("worktree") as JsonPrimitive).content)
        val kotlin = json.getValue("daemons").jsonArray[2].jsonObject
        assertEquals("kotlin", (kotlin.getValue("kind") as JsonPrimitive).content)
        assertEquals(JsonNull, kotlin.getValue("idleSince"))
    }

    @Test
    fun `gradle json without daemons prints an empty list`() {
        val exitCode = cli(repositoryExecutor()).run(listOf("gradle", "--json"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(emptyList(), jsonOut().getValue("daemons").jsonArray.toList())
        assertEquals(JsonPrimitive(0), jsonOut().getValue("totalMemoryBytes"))
    }

    @Test
    fun `gradle stop json lists the stopped and kept daemons`() {
        val daemons = FakeGradleDaemonSource(
            listOf(
                gradleDaemon(10, idleSinceTenMinutes),
                gradleDaemon(12, DaemonState.Busy),
            ),
        )

        val exitCode = cli(repositoryExecutor(), daemons = daemons).run(listOf("gradle", "stop", "--json"))

        assertEquals(0, exitCode, err.toString())
        val json = jsonOut()
        assertEquals(setOf("stopped", "kept", "notDaemons", "failures"), json.keys)
        assertEquals(listOf(JsonPrimitive(10)), json.getValue("stopped").jsonArray.toList())
        val kept = json.getValue("kept").jsonArray.single().jsonObject
        assertEquals(setOf("pid", "kind", "reason"), kept.keys)
        assertEquals("busy", (kept.getValue("reason") as JsonPrimitive).content)
    }

    @Test
    fun `gradle stop json of a process that isn't a daemon exits with 1`() {
        val exitCode = cli(repositoryExecutor()).run(listOf("gradle", "stop", "99", "--json"))

        assertEquals(1, exitCode)
        assertEquals(listOf(JsonPrimitive(99)), jsonOut().getValue("notDaemons").jsonArray.toList())
    }

    private fun xcodeProvisioning() = FakeRepositoryProvisioningSource(
        byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(xcode = XcodeProvider(compilationCache = true)))),
    )

    @Test
    fun `xcode json lists the folders of the worktrees and the orphans`() {
        val firstProject = "$base/iosApp/iosApp.xcodeproj"
        val orphanProject = "/home/dev/projects/gone/Gone.xcodeproj"
        val derivedData = FakeDerivedDataSource(
            existing = listOf(
                FakeDerivedDataSource.derivedData("Main-hash", firstProject, sizeBytes = 1_073_741_824L),
                FakeDerivedDataSource.derivedData("Gone-hash", orphanProject, sizeBytes = 2_147_483_648L),
            ),
            existingProjects = setOf(firstProject),
        )
        val fileManager = FakeFileManager(existing = listOf(base, merged))

        val exitCode = cli(repositoryExecutor(), fileManager = fileManager, provisioning = xcodeProvisioning(), derivedDataSource = derivedData)
            .run(listOf("xcode", "--json"))

        assertEquals(0, exitCode, err.toString())
        val json = jsonOut()
        assertEquals(setOf("active", "folders", "orphans", "unavailableVolumeFolders", "compilationCacheBytes"), json.keys)
        assertEquals(JsonPrimitive(true), json.getValue("active"))
        val folder = json.getValue("folders").jsonArray.single().jsonObject
        assertEquals(setOf("path", "sizeBytes", "worktree"), folder.keys)
        assertEquals(base, (folder.getValue("worktree") as JsonPrimitive).content)
        assertEquals(JsonPrimitive(1_073_741_824L), folder.getValue("sizeBytes"))
        val orphan = json.getValue("orphans").jsonArray.single().jsonObject
        assertEquals(setOf("path", "sizeBytes"), orphan.keys)
        assertEquals(JsonPrimitive(2_147_483_648L), orphan.getValue("sizeBytes"))
    }

    @Test
    fun `xcode json says when the provider isn't active`() {
        val exitCode = cli(repositoryExecutor()).run(listOf("xcode", "--json"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(JsonPrimitive(false), jsonOut().getValue("active"))
        assertEquals(JsonNull, jsonOut().getValue("compilationCacheBytes"))
    }

    @Test
    fun `xcode clean json lists the deleted folders and the freed bytes`() {
        val first = FakeDerivedDataSource.derivedData("Gone1-hash", "/home/dev/projects/gone1/App.xcodeproj", 1_073_741_824L)
        val second = FakeDerivedDataSource.derivedData("Gone2-hash", "/home/dev/projects/gone2/App.xcodeproj", 1_073_741_824L)
        val derivedData = FakeDerivedDataSource(existing = listOf(first, second))

        val exitCode = cli(repositoryExecutor(), provisioning = xcodeProvisioning(), derivedDataSource = derivedData)
            .run(listOf("xcode", "clean", "--json"))

        assertEquals(0, exitCode, err.toString())
        val json = jsonOut()
        assertEquals(setOf("active", "deleted", "freedBytes", "failures"), json.keys)
        assertEquals(listOf(first.path, second.path), json.strings("deleted"))
        assertEquals(JsonPrimitive(2_147_483_648L), json.getValue("freedBytes"))
    }

    @Test
    fun `remove json reports the removal and what was released`() {
        environmentStore.portsByAdminDirectory["$base/.git/worktrees/TASK-1"] = mapOf("emulator" to 5556)
        androidSdk.states["emulator-5556"] = EmulatorState.RUNNING

        val exitCode = cli(executorWithAdminDirectories(), provisioning = emulatorProvisioning).run(listOf("remove", "TASK-1", "--delete-branch", "--json"))

        assertEquals(0, exitCode, err.toString())
        val json = jsonOut()
        assertEquals(setOf("path", "branch", "branchDeleted", "branchKeptReason", "providers", "failures"), json.keys)
        assertEquals(merged, (json.getValue("path") as JsonPrimitive).content)
        assertEquals("feature/TASK-1", (json.getValue("branchDeleted") as JsonPrimitive).content)
        assertEquals(
            setOf("emulatorsStopped", "simulatorsStopped", "gradleDaemonsStopped"),
            json.getValue("providers").jsonObject.keys,
        )
        assertEquals(listOf("emulator-5556"), json.getValue("providers").jsonObject.strings("emulatorsStopped"))
    }

    @Test
    fun `remove json leaves the branch null when it isn't deleted`() {
        val exitCode = cli(executorWithAdminDirectories()).run(listOf("remove", "TASK-1", "--json"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(JsonNull, jsonOut().getValue("branchDeleted"))
        assertEquals(JsonNull, jsonOut().getValue("branchKeptReason"))
    }

    @Test
    fun `remove json prints nothing on standard output when the worktree has changes`() {
        val exitCode = cli(executorWithAdminDirectories()).run(listOf("remove", "spike", "--json"))

        assertEquals(1, exitCode)
        assertEquals("", out.toString())
    }

    @Test
    fun `repair json lists the re-linked worktrees`() {
        val moved = "/home/dev/old-place/TASK-1"
        val executor = repositoryExecutor { command, _ ->
            if (command.take(3) == listOf("git", "worktree", "list")) Executor.ok(command, porcelain.replace(merged, moved)) else null
        }
        val fileManager = FakeFileManager(existing = listOf(base, merged))

        val exitCode = cli(executor, fileManager = fileManager).run(listOf("repair", "--json"))

        assertEquals(0, exitCode, err.toString())
        val json = jsonOut()
        assertEquals(setOf("repaired"), json.keys)
        assertEquals(listOf(merged), json.strings("repaired"))
    }

    @Test
    fun `repair json with nothing to repair prints an empty list`() {
        val exitCode = cli(repositoryExecutor()).run(listOf("repair", "--json"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(emptyList(), jsonOut().strings("repaired"))
    }

    private val userSettings = "/home/dev/.claude/settings.json"
    private val projectSettings = "$base/.claude/settings.json"

    @Test
    fun `hooks install writes the hooks after confirming`() {
        val files = FakeFileManager(existing = listOf(base))

        val exitCode = cli(repositoryExecutor(), fileManager = files, answer = "y").run(listOf("hooks", "install"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(out.contains("+ Stop: shogun hook agent-state"), out.toString())
        assertTrue(err.contains("Install them? [y/N]"), err.toString())
        assertTrue(files.writtenTexts.getValue(userSettings).contains("shogun hook agent-state"))
        assertEquals(setOf(userSettings), files.writtenTexts.keys)
    }

    @Test
    fun `hooks install declined writes nothing and exits with 0`() {
        val files = FakeFileManager(existing = listOf(base))

        val exitCode = cli(repositoryExecutor(), fileManager = files, answer = "n").run(listOf("hooks", "install"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(files.writtenTexts.isEmpty())
        assertTrue(out.contains("Nothing written."), out.toString())
    }

    @Test
    fun `hooks install with yes does not ask`() {
        val files = FakeFileManager(existing = listOf(base))

        val exitCode = cli(repositoryExecutor(), fileManager = files).run(listOf("hooks", "install", "--yes"))

        assertEquals(0, exitCode, err.toString())
        assertFalse(err.contains("[y/N]"), err.toString())
        assertTrue(files.writtenTexts.containsKey(userSettings))
    }

    @Test
    fun `hooks install in a repository writes the project settings only`() {
        val files = FakeFileManager(existing = listOf(base))

        val exitCode = cli(repositoryExecutor(), fileManager = files).run(listOf("hooks", "install", "--project", "--yes"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(setOf(projectSettings), files.writtenTexts.keys)
    }

    @Test
    fun `hooks follow CLAUDE_CONFIG_DIR`() {
        val files = FakeFileManager(existing = listOf(base))

        cli(repositoryExecutor(), fileManager = files, environment = mapOf("CLAUDE_CONFIG_DIR" to "/custom/claude"))
            .run(listOf("hooks", "install", "--yes"))

        assertEquals(setOf("/custom/claude/settings.json"), files.writtenTexts.keys)
    }

    @Test
    fun `hooks install with broken settings exits with 1 and leaves the file`() {
        val files = FakeFileManager(existing = listOf(base))
        files.writeText(userSettings, "{ nope")

        val exitCode = cli(repositoryExecutor(), fileManager = files).run(listOf("hooks", "install", "--yes"))

        assertEquals(1, exitCode)
        assertTrue(err.contains(userSettings), err.toString())
        assertEquals("{ nope", files.writtenTexts[userSettings])
    }

    @Test
    fun `hooks uninstall removes the hooks`() {
        val files = FakeFileManager(existing = listOf(base))
        cli(repositoryExecutor(), fileManager = files).run(listOf("hooks", "install", "--yes"))
        out.clear()

        val exitCode = cli(repositoryExecutor(), fileManager = files).run(listOf("hooks", "uninstall", "--yes"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("{}\n", files.writtenTexts.getValue(userSettings))
    }

    @Test
    fun `hooks status names the missing events`() {
        val files = FakeFileManager(existing = listOf(base))
        files.writeText(userSettings, """{"hooks":{"WorktreeCreate":[{"hooks":[{"type":"command","command":"shogun hook worktree-create"}]}]}}""")

        val exitCode = cli(repositoryExecutor(), fileManager = files).run(listOf("hooks", "status"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(out.lines().any { it.startsWith("user: outdated (WorktreeRemove, SessionStart, UserPromptSubmit, Notification, Stop, SessionEnd)") }, out.toString())
        assertTrue(out.lines().any { it.startsWith("project: missing") }, out.toString())
    }

    @Test
    fun `hooks status json describes both scopes`() {
        cli(repositoryExecutor(), fileManager = FakeFileManager(existing = listOf(base))).run(listOf("hooks", "status", "--json"))

        val json = Json.parseToJsonElement(out.toString()).jsonObject
        assertEquals("missing", json.getValue("user").jsonObject.getValue("state").jsonPrimitive.content)
        assertEquals(projectSettings, json.getValue("project").jsonObject.getValue("path").jsonPrimitive.content)
    }

    @Test
    fun `hooks help describes the three subcommands`() {
        val exitCode = cli(repositoryExecutor()).run(listOf("hooks", "--help"))

        assertEquals(0, exitCode)
        assertTrue(out.contains("hooks install|uninstall"), out.toString())
        assertTrue(out.contains("hooks status"), out.toString())
    }
}
