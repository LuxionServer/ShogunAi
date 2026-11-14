package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakeHerdrConfigSource
import app.luxion.shogunai.domain.FakeKmpTestWiring
import app.luxion.shogunai.domain.FakeMachineCapacity
import app.luxion.shogunai.domain.FakeOrcaClient
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeResourceLimitsRepository
import app.luxion.shogunai.domain.FakeSwiftBuildSource
import app.luxion.shogunai.domain.FakeSwiftBuildSource.Companion.swiftBuild
import app.luxion.shogunai.domain.ScriptedShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.domain.model.XcodeProvider
import app.luxion.shogunai.engine.EngineOptions
import app.luxion.shogunai.engine.ShogunEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XcodeSwiftBuildCommandsTest {

    private val gigabyte = 1_073_741_824L
    private val base = "/home/dev/projects/main-repo"
    private val common = "$base/.git"
    private val task1 = "/home/dev/projects/worktrees/TASK-1"
    private val task2 = "/home/dev/projects/worktrees/TASK-2"
    private val other = "/home/dev/projects/other-repo"
    private val now = Instant.parse("2026-11-13T12:00:00Z")
    private val yesterday = now.minus(Duration.ofDays(1))

    private val porcelain = """
        worktree $base
        HEAD 1111111111111111111111111111111111111111
        branch refs/heads/main

        worktree $task1
        HEAD 2222222222222222222222222222222222222222
        branch refs/heads/feature/TASK-1

        worktree $task2
        HEAD 3333333333333333333333333333333333333333
        branch refs/heads/feature/TASK-2
    """.trimIndent()

    private val otherPorcelain = """
        worktree $other
        HEAD 4444444444444444444444444444444444444444
        branch refs/heads/main
    """.trimIndent()

    private val out = StringBuilder()
    private val err = StringBuilder()

    private val executor = object : ScriptedShellCommandExecutor() {
        override suspend fun respond(command: List<String>, workingDirectory: String?): CommandResult {
            if (workingDirectory?.startsWith("/home/dev/projects") != true) return CommandResult(command, 128, "", "not a git repository")
            val inOther = workingDirectory.startsWith(other)
            val stdout = when {
                command.take(3) == listOf("git", "worktree", "list") -> if (inOther) otherPorcelain else porcelain
                "--show-toplevel" in command -> if (inOther) "$other\n" else "$base\n"
                "--git-dir" in command || "--git-common-dir" in command -> if (inOther) "$other/.git\n" else "$common\n"
                else -> ""
            }
            return CommandResult(command, 0, stdout, "")
        }
    }

    private val declaring = FakeRepositoryProvisioningSource(
        byRepository = mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(xcode = XcodeProvider()))),
    )

    private fun cli(
        builds: FakeSwiftBuildSource,
        provisioning: FakeRepositoryProvisioningSource = declaring,
        derivedData: FakeDerivedDataSource = FakeDerivedDataSource(),
        settings: ResourceSettings = ResourceSettings(),
        projects: List<Project> = emptyList(),
    ) = ShogunCli(
        engine = ShogunEngine(
            cliEnginePorts(
                executor = executor,
                fileManager = FakeFileManager(existing = listOf(base, task1, task2, other)),
                projectRepository = FakeProjectRepository(projects),
                provisioningSource = provisioning,
                gradleDaemons = FakeGradleDaemonSource(),
                gradleUserHome = "/home/dev/.gradle",
                environmentStateStore = FakeEnvironmentStateStore(),
                portProbe = FakePortProbe(),
                androidSdk = FakeAndroidSdk(),
                appleSimulators = FakeAppleSimulators(),
                kmpTestWiring = FakeKmpTestWiring(),
                resourceLimits = FakeResourceLimitsRepository(settings),
                machineCapacity = FakeMachineCapacity(),
                derivedData = derivedData,
                herdrConfig = FakeHerdrConfigSource(),
                orca = FakeOrcaClient(installed = false),
                workingDirectory = base,
                environment = hostEnvironment(),
                swiftBuilds = builds,
            ),
            EngineOptions(clock = { now }),
        ),
        workingDirectory = base,
        out = out,
        err = err,
        canonicalPath = { it.trimEnd('/') },
        environment = hostEnvironment(),
        now = { now },
    )

    private fun json(): JsonObject = Json.parseToJsonElement(out.toString()).jsonObject

    private fun builds(vararg sizes: Pair<String, Long>, accessed: Instant = yesterday) =
        FakeSwiftBuildSource(*sizes.map { (worktree, _) -> swiftBuild(worktree, accessed) }.toTypedArray()).apply {
            sizes.forEach { (worktree, size) -> this.sizes["$worktree/.build"] = size }
        }

    @Test
    fun `xcode lists the build folders of the worktrees by path with their packages`() {
        val builds = builds(task1 to 4 * gigabyte).apply { packageSizes["$task1/.build"] = gigabyte / 2 }

        val exitCode = cli(builds).run(listOf("xcode"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("$task1/.build  4.0 GB  (packages 512.0 MB)\norphans: 0 (0 B)\n", out.toString())
    }

    @Test
    fun `xcode json gives every folder its kind`() {
        val project = "$task2/App.xcodeproj"
        val derivedData = FakeDerivedDataSource(existing = listOf(FakeDerivedDataSource.derivedData("App-hash", project)), existingProjects = setOf(project))

        cli(builds(task1 to gigabyte), derivedData = derivedData).run(listOf("xcode", "--json"))

        val folders = json().getValue("folders").jsonArray.map { it.jsonObject }
        assertEquals(listOf("derived-data", "swift-build"), folders.map { it.getValue("kind").jsonPrimitive.content })
        assertEquals(task1, folders.last().getValue("worktree").jsonPrimitive.content)
    }

    @Test
    fun `clean with swift build deletes the unused build folders and keeps the ones in use`() {
        val builds = builds(task1 to 3 * gigabyte)
        builds.folders[task2] = swiftBuild(task2, now.minus(Duration.ofMinutes(5)))

        val exitCode = cli(builds).run(listOf("xcode", "clean", "--swift-build"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(listOf("$task1/.build"), builds.deleted)
        assertEquals("deleted $task1/.build  3.0 GB\nfreed 3.0 GB\nkept $task2/.build  (in use)\n", out.toString())
    }

    @Test
    fun `clean with swift build says when there is nothing to delete`() {
        cli(FakeSwiftBuildSource()).run(listOf("xcode", "clean", "--swift-build"))

        assertEquals("Nothing to delete.\n", out.toString())
    }

    @Test
    fun `clean with swift build lists only what it kept or couldn't delete`() {
        val inUse = builds(task1 to gigabyte, accessed = now)
        val failing = builds(task1 to gigabyte).apply { failDeletionFor = setOf("$task1/.build") }

        cli(inUse).run(listOf("xcode", "clean", "--swift-build"))
        val kept = out.toString()
        out.clear()
        val exitCode = cli(failing).run(listOf("xcode", "clean", "--swift-build"))

        assertEquals("kept $task1/.build  (in use)\n", kept)
        assertEquals(1, exitCode)
        assertEquals("", out.toString())
        assertEquals("shogun: couldn't delete $task1/.build: can't delete $task1/.build\n", err.toString())
    }

    @Test
    fun `plain clean keeps the build folders`() {
        val builds = builds(task1 to gigabyte)

        cli(builds).run(listOf("xcode", "clean"))

        assertTrue(builds.deleted.isEmpty())
    }

    @Test
    fun `clean with swift build json reports deleted, in use and failed folders and exits with 1 on a failure`() {
        val builds = builds(task1 to gigabyte, task2 to gigabyte).apply { failDeletionFor = setOf("$task2/.build") }

        val exitCode = cli(builds).run(listOf("xcode", "clean", "--swift-build", "--json"))

        assertEquals(1, exitCode)
        val json = json()
        assertEquals(setOf("active", "deleted", "freedBytes", "inUse", "failures"), json.keys)
        assertEquals("$task1/.build", json.getValue("deleted").jsonArray.single().jsonPrimitive.content)
        assertEquals(JsonPrimitive(gigabyte), json.getValue("freedBytes"))
        assertEquals("$task2/.build", json.getValue("failures").jsonArray.single().jsonObject.getValue("path").jsonPrimitive.content)
        assertEquals("shogun: couldn't delete $task2/.build: can't delete $task2/.build\n", err.toString())
    }

    @Test
    fun `clean with swift build reports an inactive provider as text or json`() {
        cli(builds(task1 to gigabyte), provisioning = FakeRepositoryProvisioningSource()).run(listOf("xcode", "clean", "--swift-build"))
        val text = out.toString()
        out.clear()
        cli(builds(task1 to gigabyte), provisioning = FakeRepositoryProvisioningSource()).run(listOf("xcode", "clean", "--swift-build", "--json"))

        assertEquals("The Xcode provider isn't active.\n", text)
        assertEquals(JsonPrimitive(false), json().getValue("active"))
        assertEquals(setOf("active", "deleted", "freedBytes", "inUse", "failures"), json().keys)
    }

    @Test
    fun `trim releases build folders of the declaring repositories only`() {
        val otherProject = Project("other", "Other", ProjectConfig(baseRepositoryPath = other, worktreesRoot = "/home/dev/projects/other-worktrees", secretFiles = emptyList()))
        val builds = builds(task1 to 8 * gigabyte, other to 30 * gigabyte)

        val exitCode = cli(builds, settings = ResourceSettings(derivedDataQuotaGigabytes = 5), projects = listOf(otherProject)).run(listOf("xcode", "trim"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(listOf("$task1/.build"), builds.deleted)
        assertEquals("released $task1/.build  8.0 GB\nfreed 8.0 GB\nusage 0 B of 5.0 GB\n", out.toString())
    }
}
