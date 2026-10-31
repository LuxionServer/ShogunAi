package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakeHerdrClient
import app.luxion.shogunai.domain.FakeHerdrConfigSource
import app.luxion.shogunai.domain.FakeKmpTestWiring
import app.luxion.shogunai.domain.FakeMachineCapacity
import app.luxion.shogunai.domain.FakeMultirepoSource
import app.luxion.shogunai.domain.FakeOrcaClient
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeResourceLimitsRepository
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.io.LauncherFiles
import app.luxion.shogunai.domain.io.UserPathEntries
import app.luxion.shogunai.domain.model.CapacityProfile
import app.luxion.shogunai.domain.model.DockerComposeProvider
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.model.MultirepoDeclaration
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.domain.usecase.CommandLineTool
import app.luxion.shogunai.infrastructure.ExecutableLocator
import app.luxion.shogunai.infrastructure.SystemHostEnvironment
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DoctorCommandTest {

    private val home = "/home/dev"
    private val settings = "$home/.claude/settings.json"
    private val out = StringBuilder()
    private val err = StringBuilder()

    private object NoLaunchers : LauncherFiles {
        override fun exists(path: String) = false
        override fun isWritableFolder(folder: String) = false
        override fun writeText(path: String, text: String) = error("the doctor writes nothing")
        override fun readText(path: String): String? = null
        override fun linkTarget(link: String): String? = null
        override fun createLink(target: String, link: String) = error("the doctor writes nothing")
        override fun delete(path: String) = error("the doctor writes nothing")
    }

    private object NoUserPath : UserPathEntries {
        override suspend fun contains(folder: String) = false
        override suspend fun add(folder: String) = error("the doctor writes nothing")
        override suspend fun remove(folder: String) = error("the doctor writes nothing")
    }

    private fun cli(
        executables: Set<String> = setOf("/usr/bin/git", "/usr/bin/shogun"),
        gitVersion: String = "git version 2.45.1",
        hooks: String? = null,
        projects: List<Project> = emptyList(),
        fileManager: FakeFileManager = FakeFileManager(),
        provisioning: FakeRepositoryProvisioningSource = FakeRepositoryProvisioningSource(),
        multirepos: FakeMultirepoSource = FakeMultirepoSource(),
        executor: FakeShellCommandExecutor = FakeShellCommandExecutor { command -> FakeShellCommandExecutor.success(command, "$gitVersion\n") },
    ): ShogunCli {
        hooks?.let { fileManager.writtenTexts[settings] = it }
        val environment = SystemHostEnvironment(HostOs.LINUX, mapOf("PATH" to "/usr/bin", "SHELL" to "/bin/bash"), home, loginShell = null)
        val locator = ExecutableLocator(HostOs.LINUX, home, { listOf("/usr/bin") }, isFile = { it in executables })
        return ShogunCli(
            executor = executor,
            fileManager = fileManager,
            projectRepository = FakeProjectRepository(projects),
            provisioningSource = provisioning,
            multirepoSource = multirepos,
            daemonSource = FakeGradleDaemonSource(),
            gradleUserHome = "$home/.gradle",
            environmentStateStore = FakeEnvironmentStateStore(),
            portProbe = FakePortProbe(),
            androidSdk = FakeAndroidSdk(),
            appleSimulators = FakeAppleSimulators(),
            kmpTestWiring = FakeKmpTestWiring(),
            resourceLimits = FakeResourceLimitsRepository(ResourceSettings(CapacityProfile.PER_WORKTREE)),
            machineCapacity = FakeMachineCapacity(),
            derivedDataSource = FakeDerivedDataSource(),
            herdrConfig = FakeHerdrConfigSource(),
            workingDirectory = "/",
            out = out,
            err = err,
            environment = environment,
            findExecutable = locator::find,
            herdr = FakeHerdrClient(),
            orca = FakeOrcaClient(installed = false),
            commandLineTool = CommandLineTool(
                os = HostOs.LINUX,
                appLauncher = null,
                home = home,
                pathFolders = { environment.path.folders },
                userBinFolders = locator.userBinFolders(),
                findShogun = { locator.find("shogun") },
                files = NoLaunchers,
                userPath = NoUserPath,
            ),
        )
    }

    @Test
    fun `warnings only exit with 0 and print their fix`() {
        val exit = cli().run(listOf("doctor"))

        assertEquals(0, exit)
        val lines = out.lines()
        assertEquals("ok    Git 2.45.1 (/usr/bin/git)", lines[0])
        assertEquals("ok    PATH of the process that started ShogunAi", lines[1])
        assertEquals("ok    shogun command at /usr/bin/shogun", lines[2])
        assertEquals("warn  Claude Code hooks aren't installed in $settings", lines[3])
        assertTrue(lines[4].startsWith("      fix: Run 'shogun hooks install'"))
        assertTrue("--    Claude Code not found" in lines)
        assertTrue("--    Herdr not found" in lines)
        assertTrue("--    Orca not found" in lines)
    }

    @Test
    fun `a failed check exits with 1`() {
        val exit = cli(executables = setOf("/usr/bin/shogun")).run(listOf("doctor"))

        assertEquals(1, exit)
        assertTrue(out.lines().first().startsWith("fail  Git not found on the PATH"))
    }

    @Test
    fun `an old git fails`() {
        assertEquals(1, cli(gitVersion = "git version 2.25.1").run(listOf("doctor")))
    }

    @Test
    fun `a project's missing local file fails, naming it`() {
        val base = "$home/projects/main-repo"
        val project = Project("1", "main-repo", ProjectConfig(base, "$home/worktrees", listOf("local.properties")))

        val exit = cli(projects = listOf(project), fileManager = FakeFileManager(directories = setOf(base))).run(listOf("doctor"))

        assertEquals(1, exit)
        val lines = out.lines()
        val index = lines.indexOf("fail  main-repo: 1 local file unavailable in $base")
        assertTrue(index >= 0, out.toString())
        assertEquals(
            "      fix: local.properties: not found at $base/local.properties. Create it there, or remove it from the project's local files",
            lines[index + 1],
        )
    }

    @Test
    fun `json lists every check with its id, state, summary and fix`() {
        val exit = cli(executables = setOf("/usr/bin/git")).run(listOf("doctor", "--json"))

        assertEquals(1, exit)
        val json = Json.parseToJsonElement(out.toString()).jsonObject
        assertTrue(json.getValue("failed").jsonPrimitive.boolean)
        val checks = json.getValue("checks").jsonArray.map { it.jsonObject }
        assertEquals(listOf("git", "path", "shogun", "claude-hooks", "claude-code", "herdr", "orca"), checks.map { it.getValue("id").jsonPrimitive.content })
        val git = checks.first()
        assertEquals("passed", git.getValue("state").jsonPrimitive.content)
        assertEquals("Git 2.45.1 (/usr/bin/git)", git.getValue("summary").jsonPrimitive.content)
        assertEquals(JsonNull, git.getValue("fix"))
        val shogun = checks[2]
        assertEquals("failed", shogun.getValue("state").jsonPrimitive.content)
        assertEquals("shogun command not found", shogun.getValue("summary").jsonPrimitive.content)
        assertEquals("not_found", checks[6].getValue("state").jsonPrimitive.content)
    }

    @Test
    fun `doctor takes no arguments`() {
        assertEquals(ShogunCli.EXIT_USAGE, cli().run(listOf("doctor", "extra")))
    }

    @Test
    fun `doctor has its help`() {
        assertTrue(CliHelp.forCommand("doctor").orEmpty().contains("doctor [--json]"))
        assertTrue(CliHelp.USAGE.contains("doctor [--json]"))
    }

    @Test
    fun `a missing provider tool warns with its fix and doesn't fail`() {
        val base = "$home/projects/main-repo"
        val project = Project("1", "main-repo", ProjectConfig(base, "$home/worktrees", emptyList()))
        val provisioning = FakeRepositoryProvisioningSource(
            mapOf(base to RepositoryProvisioning(providers = RepositoryProviders(dockerCompose = DockerComposeProvider()))),
        )

        val exit = cli(projects = listOf(project), fileManager = FakeFileManager(directories = setOf(base)), provisioning = provisioning).run(listOf("doctor"))

        assertEquals(0, exit)
        val lines = out.lines()
        val index = lines.indexOf("warn  main-repo: Docker Compose not found, needed by the docker-compose provider (looked in docker on the PATH)")
        assertTrue(index >= 0, out.toString())
        assertTrue(lines[index + 1].startsWith("      fix: Install Docker with its Compose plugin"))
    }

    @Test
    fun `a multirepo whose repository is gone fails, in text and json`() {
        val folder = "$home/projects/product"
        val multirepos = FakeMultirepoSource(mapOf(folder to MultirepoDeclaration(mapOf("api" to "product-api"), "../product-worktrees")))
        val executor = FakeShellCommandExecutor { command ->
            if (command.last() == "--version") FakeShellCommandExecutor.success(command, "git version 2.45.1\n") else FakeShellCommandExecutor.failure(command)
        }

        assertEquals(1, cli(multirepos = multirepos, executor = executor).run(listOf("doctor")))
        assertTrue("fail  Multirepo product ($folder): 1 problem" in out.lines(), out.toString())

        out.clear()
        assertEquals(1, cli(multirepos = multirepos, executor = executor).run(listOf("doctor", "--json")))
        val checks = Json.parseToJsonElement(out.toString()).jsonObject.getValue("checks").jsonArray.map { it.jsonObject }
        val multirepo = checks.last()
        assertEquals("multirepo:product", multirepo.getValue("id").jsonPrimitive.content)
        assertEquals("failed", multirepo.getValue("state").jsonPrimitive.content)
        assertTrue(multirepo.getValue("fix").jsonPrimitive.content.startsWith("'api': $folder/product-api isn't a Git repository"))
    }
}
