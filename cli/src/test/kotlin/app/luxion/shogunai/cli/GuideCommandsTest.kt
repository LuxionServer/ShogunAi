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
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeResourceLimitsRepository
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.CapacityProfile
import app.luxion.shogunai.domain.model.EmulatorProvider
import app.luxion.shogunai.domain.model.IosSimulatorProvider
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.domain.model.WorkflowRules
import app.luxion.shogunai.domain.usecase.AgentInstructionsBlock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuideCommandsTest {

    private val base = "/home/dev/projects/main-repo"
    private val linked = "/home/dev/projects/worktrees/TASK-123"
    private val common = "$base/.git"
    private val home = "/home/dev"
    private val out = StringBuilder()
    private val err = StringBuilder()
    private val files = FakeFileManager(existing = listOf(base, linked))
    private val block = AgentInstructionsBlock.current()

    private val porcelain = """
        worktree $base
        HEAD 1111111111111111111111111111111111111111
        branch refs/heads/main

        worktree $linked
        HEAD 2222222222222222222222222222222222222222
        branch refs/heads/feature/TASK-123
    """.trimIndent()

    private val executor = object : ShellCommandExecutor {
        override suspend fun execute(command: List<String>, workingDirectory: String?): CommandResult {
            val inLinked = workingDirectory?.startsWith(linked) == true
            val stdout = when {
                "--git-dir" in command -> if (inLinked) "$common/worktrees/TASK-123\n$common\n" else "$common\n$common\n"
                "--git-common-dir" in command -> "$common\n"
                command.take(3) == listOf("git", "worktree", "list") -> porcelain
                else -> ""
            }
            val outside = workingDirectory?.startsWith("/home/dev/projects") != true
            return CommandResult(command, if (outside) 128 else 0, stdout, if (outside) "not a git repository" else "")
        }
    }

    private fun declaring(providers: RepositoryProviders = RepositoryProviders()) = FakeRepositoryProvisioningSource(
        byRepository = mapOf(base to RepositoryProvisioning(providers = providers)),
    )

    private val project = Project("main-repo", "main-repo", ProjectConfig(base, "/home/dev/projects/worktrees", emptyList()))

    private fun cli(
        provisioning: FakeRepositoryProvisioningSource = declaring(),
        workingDirectory: String = linked,
        input: String = "",
        answer: String? = null,
        herdr: FakeHerdrClient = FakeHerdrClient(agentsFailure = IllegalStateException("herdr isn't running")),
        environment: Map<String, String> = emptyMap(),
        registered: Boolean = true,
    ) = ShogunCli(
        executor = executor,
        fileManager = files,
        projectRepository = FakeProjectRepository(if (registered) listOf(project) else emptyList()),
        provisioningSource = provisioning,
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
        workingDirectory = workingDirectory,
        out = out,
        err = err,
        readInput = { input },
        canonicalPath = { it.trimEnd('/') },
        environmentVariable = { environment[it] },
        userHome = home,
        herdr = herdr,
        readAnswer = { answer },
    )

    private fun everything() = declaring(RepositoryProviders(emulator = EmulatorProvider(), iosSimulator = IosSimulatorProvider()))

    private fun fakeHerdr() = FakeHerdrClient()

    @Test
    fun `guide in a worktree prints its rules and not the main checkout one`() {
        assertEquals(0, cli().run(listOf("guide", "worktrees")), err.toString())

        assertTrue(out.lines().first().contains("take precedence"), out.toString())
        assertTrue(out.contains("no `git switch`, no `git checkout -b`"), out.toString())
        assertFalse(out.lines().takeWhile { !it.startsWith("Not applicable") }.any { it.contains("This is the main checkout") }, out.toString())
    }

    @Test
    fun `guide in the main checkout says to create a worktree`() {
        assertEquals(0, cli(workingDirectory = base).run(listOf("guide", "worktrees")), err.toString())

        val applicable = out.lines().takeWhile { !it.startsWith("Not applicable") }
        assertTrue(applicable.any { it.contains("create a worktree with `shogun new <id>`") }, out.toString())
        assertFalse(applicable.any { it.contains("no `git switch`") }, out.toString())
    }

    @Test
    fun `guide explains how to branch from an epic`() {
        assertEquals(0, cli().run(listOf("guide", "worktrees")))

        assertTrue(out.contains("shogun new <id> --from <ref>"), out.toString())
        assertTrue(out.contains("epic's branch"), out.toString())
    }

    @Test
    fun `guide resources tells agents to wait for a full pool in the background`() {
        assertEquals(0, cli(everything()).run(listOf("guide", "resources")))

        assertTrue(out.contains("`shogun emulator start --wait` run in the background"), out.toString())
        assertTrue(out.contains("`shogun simulator start --wait` run in the background"), out.toString())
    }

    @Test
    fun `guide marks the rules of an undeclared provider after the others`() {
        assertEquals(0, cli().run(listOf("guide", "resources")))

        val inapplicable = out.lines().dropWhile { !it.startsWith("Not applicable") }
        assertTrue(inapplicable.any { it.contains("shogun emulator start") && it.contains("applies when the repository declares the emulator provider") }, out.toString())
        assertFalse(out.lines().takeWhile { !it.startsWith("Not applicable") }.any { it.contains("shogun emulator start") }, out.toString())
    }

    @Test
    fun `guide parallel with Herdr tells to open sessions and use messages`() {
        assertEquals(0, cli(herdr = fakeHerdr()).run(listOf("guide", "parallel")))

        val applicable = out.lines().takeWhile { !it.startsWith("Not applicable") }
        assertTrue(applicable.any { it.contains("shogun open <id> --herdr") }, out.toString())
        assertTrue(applicable.any { it.contains("never by typing into its terminal") }, out.toString())
    }

    @Test
    fun `guide cleanup says to ask and names the extra options`() {
        assertEquals(0, cli().run(listOf("guide", "cleanup")))

        assertTrue(out.contains("Ask before running `shogun clean --yes`"), out.toString())
        listOf("--stop-emulators", "--stop-gradle-daemons", "--delete-simulators", "--delete-derived-data", "--delete-branches")
            .forEach { assertTrue(out.contains(it), it) }
    }

    @Test
    fun `guide with an unknown topic is a usage error listing the topics`() {
        assertEquals(2, cli().run(listOf("guide", "parties")))

        assertTrue(err.contains("worktrees, parallel, resources, cleanup"), err.toString())
        assertEquals("", out.toString())
    }

    @Test
    fun `guide plain prints every rule outside a repository`() {
        assertEquals(0, cli(workingDirectory = "/tmp/elsewhere").run(listOf("guide", "--plain")), err.toString())

        WorkflowRules.all.forEach { assertTrue(out.contains(it.text), it.id) }
        listOf("worktrees", "parallel", "resources", "cleanup").forEach { assertTrue(out.contains("## $it"), it) }
    }

    @Test
    fun `guide outside a repository fails and points to plain`() {
        assertEquals(1, cli(workingDirectory = "/tmp/elsewhere").run(listOf("guide")))

        assertTrue(err.contains("shogun guide --plain"), err.toString())
    }

    @Test
    fun `guide migrate lists the ids and tells the agent to apply nothing`() {
        files.writeText("$home/AGENTS.md", "own\n")

        assertEquals(0, cli().run(listOf("guide", "--migrate", "--file", "$home/AGENTS.md")))

        WorkflowRules.all.forEach { assertTrue(out.contains("- ${it.id}:"), it.id) }
        assertTrue(out.contains("Read $home/AGENTS.md"), out.toString())
        assertTrue(out.contains("apply nothing until the person confirms"), out.toString())
        assertEquals("own\n", files.writtenTexts.getValue("$home/AGENTS.md"))
    }

    @Test
    fun `session-start in the richest context stays within ten lines`() {
        val exitCode = cli(everything(), input = """{"cwd": "$linked"}""", herdr = fakeHerdr()).run(listOf("hook", "session-start"))

        assertEquals(0, exitCode, err.toString())
        val lines = out.lines().filter { it.isNotBlank() }
        assertTrue(lines.size <= 10, out.toString())
        assertTrue(lines.any { it.contains("shogun open <id> --herdr") }, out.toString())
        assertTrue(lines.any { it.contains("shogun emulator start") }, out.toString())
        assertTrue(lines.any { it.contains("shogun simulator start") }, out.toString())
        assertTrue(lines.last().contains("shogun guide"), out.toString())
    }

    @Test
    fun `install writes the block into the Gemini file and keeps the rest`() {
        files.writeText("$home/.gemini/GEMINI.md", "# mine\n")

        assertEquals(0, cli().run(listOf("guide", "install", "--agent", "gemini", "--yes")), err.toString())

        assertEquals("# mine\n\n$block\n", files.writtenTexts.getValue("$home/.gemini/GEMINI.md"))
    }

    @Test
    fun `install for Codex honours CODEX_HOME`() {
        assertEquals(0, cli(environment = mapOf("CODEX_HOME" to "/data/codex")).run(listOf("guide", "install", "--agent", "codex", "--yes")))

        assertEquals("$block\n", files.writtenTexts.getValue("/data/codex/AGENTS.md"))
    }

    @Test
    fun `install in the project writes the repository AGENTS md only`() {
        assertEquals(0, cli().run(listOf("guide", "install", "--project", "--yes")), err.toString())

        assertEquals(listOf("$base/AGENTS.md"), files.writtenTexts.keys.toList())
    }

    @Test
    fun `install twice leaves one block`() {
        cli().run(listOf("guide", "install", "--agent", "gemini", "--yes"))
        out.clear()

        assertEquals(0, cli().run(listOf("guide", "install", "--agent", "gemini", "--yes")))

        assertTrue(out.contains("already installed"), out.toString())
        assertEquals(1, Regex("shogun:guide v").findAll(files.writtenTexts.getValue("$home/.gemini/GEMINI.md")).count())
    }

    @Test
    fun `declining the question writes nothing`() {
        assertEquals(0, cli(answer = "n").run(listOf("guide", "install", "--agent", "gemini")))

        assertTrue(files.writtenTexts.isEmpty())
        assertTrue(out.contains("Nothing written."), out.toString())
    }

    @Test
    fun `install without a target is a usage error and writes nothing`() {
        assertEquals(2, cli().run(listOf("guide", "install")))

        assertTrue(err.contains("--agent"), err.toString())
        assertTrue(files.writtenTexts.isEmpty())
    }

    @Test
    fun `uninstall leaves only the user's text`() {
        files.writeText("/home/dev/mine.md", "own text\n")
        cli().run(listOf("guide", "install", "--file", "/home/dev/mine.md", "--yes"))

        assertEquals(0, cli().run(listOf("guide", "uninstall", "--file", "/home/dev/mine.md", "--yes")), err.toString())

        assertEquals("own text\n", files.writtenTexts.getValue("/home/dev/mine.md"))
    }

    @Test
    fun `install warns about duplicated workflow text and leaves it alone`() {
        files.writeText("/home/dev/mine.md", "Use shogun new for every change.\n")

        assertEquals(0, cli().run(listOf("guide", "install", "--file", "/home/dev/mine.md", "--yes")))

        assertTrue(err.contains("shogun guide --migrate --file /home/dev/mine.md"), err.toString())
        assertTrue(files.writtenTexts.getValue("/home/dev/mine.md").startsWith("Use shogun new for every change.\n"))
    }

    @Test
    fun `status reports installed, outdated and missing targets`() {
        files.writeText("$home/.gemini/GEMINI.md", "<!-- shogun:guide v0 -->\nold\n<!-- /shogun:guide -->\n")
        cli().run(listOf("guide", "install", "--agent", "codex", "--yes"))
        out.clear()

        assertEquals(0, cli().run(listOf("guide", "status")), err.toString())

        assertTrue(out.lines().contains("codex: installed  $home/.codex/AGENTS.md"), out.toString())
        assertTrue(out.lines().contains("gemini: outdated  $home/.gemini/GEMINI.md"), out.toString())
        assertTrue(out.lines().contains("project: missing  $base/AGENTS.md"), out.toString())
    }

    @Test
    fun `status json lists the targets`() {
        cli().run(listOf("guide", "install", "--agent", "codex", "--yes"))
        out.clear()

        assertEquals(0, cli().run(listOf("guide", "status", "--json")), err.toString())

        val targets = Json.parseToJsonElement(out.toString()).jsonObject.getValue("targets").jsonArray
        val codex = targets.first { it.jsonObject.getValue("target").jsonPrimitive.content == "codex" }.jsonObject
        assertEquals("installed", codex.getValue("state").jsonPrimitive.content)
    }

    @Test
    fun `status warns when the hook and the block both reach Claude Code`() {
        cli().run(listOf("hooks", "install", "--yes"))
        cli().run(listOf("guide", "install", "--project", "--yes"))
        err.clear()

        assertEquals(0, cli().run(listOf("guide", "status")), err.toString())

        assertTrue(err.contains("Claude Code gets the rules twice"), err.toString())
    }

    @Test
    fun `status doesn't warn about the hook for user-level agent files`() {
        cli().run(listOf("hooks", "install", "--yes"))
        cli().run(listOf("guide", "install", "--agent", "gemini", "--yes"))

        assertEquals(0, cli().run(listOf("guide", "status")), err.toString())

        assertFalse(err.contains("Claude Code gets the rules twice"), err.toString())
    }
}
