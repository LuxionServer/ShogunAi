package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.model.FlutterProvider
import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakeHerdrClient
import app.luxion.shogunai.domain.io.HerdrClient
import app.luxion.shogunai.domain.FakeHerdrConfigSource
import app.luxion.shogunai.domain.FakeOrcaClient
import app.luxion.shogunai.domain.FakeToolPreferenceRepository
import app.luxion.shogunai.domain.FakeKmpTestWiring
import app.luxion.shogunai.domain.FakeMachineCapacity
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeResourceLimitsRepository
import app.luxion.shogunai.domain.executor.CommandResult
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.CapacityProfile
import app.luxion.shogunai.domain.model.DockerComposeProvider
import app.luxion.shogunai.domain.model.EmulatorProvider
import app.luxion.shogunai.domain.model.IosSimulatorProvider
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.domain.model.RunnerId
import app.luxion.shogunai.domain.model.WorkflowRules
import app.luxion.shogunai.domain.usecase.AgentInstructionsBlock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import app.luxion.shogunai.domain.model.HerdrAgent
import kotlinx.coroutines.awaitCancellation
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
        herdr: HerdrClient = FakeHerdrClient(agentsFailure = IllegalStateException("herdr isn't running")),
        environment: Map<String, String> = emptyMap(),
        registered: Boolean = true,
        runner: FakeToolPreferenceRepository = FakeToolPreferenceRepository(),
        orca: FakeOrcaClient = FakeOrcaClient(installed = false),
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
        orca = orca,
        workingDirectory = workingDirectory,
        out = out,
        err = err,
        readInput = { input },
        canonicalPath = { it.trimEnd('/') },
        environment = hostEnvironment(home, environment),
        herdr = herdr,
        toolPreferences = runner,
        readAnswer = { answer },
    )

    private fun everything() = declaring(
        RepositoryProviders(emulator = EmulatorProvider(), iosSimulator = IosSimulatorProvider(), dockerCompose = DockerComposeProvider()),
    )

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
    fun `the worktree base rule stays out of the session start`() {
        listOf(linked, base).forEach { cwd ->
            out.setLength(0)
            assertEquals(0, cli(everything(), input = """{"cwd": "$cwd"}""", herdr = fakeHerdr()).run(listOf("hook", "session-start")), err.toString())

            assertFalse(out.contains("--from"), out.toString())
            assertFalse(out.contains("git log -1"), out.toString())
        }
    }

    @Test
    fun `a backend session start has no desktop, emulator or simulator rules`() {
        val exitCode = cli(declaring(RepositoryProviders(dockerCompose = DockerComposeProvider())), input = """{"cwd": "$linked"}""", herdr = fakeHerdr())
            .run(listOf("hook", "session-start"))

        assertEquals(0, exitCode, err.toString())
        val context = out.toString().lowercase()
        listOf("desktop", "computer use", "emulator", "simulator", "headless").forEach { assertFalse(it in context, "$it in $context") }
    }

    @Test
    fun `guide resources keeps the desktop turn rule`() {
        assertEquals(0, cli().run(listOf("guide", "resources")), err.toString())

        assertTrue(out.contains("shogun desktop acquire --wait"), out.toString())
        assertTrue(out.contains("shogun desktop release"), out.toString())
        assertTrue(out.contains("`orca computer`"), out.toString())
    }

    @Test
    fun `guide tells agents to take a pull request's base from shogun base`() {
        assertEquals(0, cli().run(listOf("guide", "worktrees")))

        assertTrue(out.contains("pull request against the branch `shogun base` prints"), out.toString())
        assertTrue(out.contains("--base \"\$(shogun base)\""), out.toString())
        assertTrue(out.contains("shogun base --set <branch>"), out.toString())
    }

    @Test
    fun `the pull request base rule stays out of the session start`() {
        assertEquals(0, cli(everything(), input = """{"cwd": "$linked"}""", herdr = fakeHerdr()).run(listOf("hook", "session-start")), err.toString())

        assertTrue(out.contains("shogun guide"), out.toString())
        assertFalse(out.contains("shogun base"), out.toString())
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
    fun `guide parallel treats a Herdr that doesn't answer as unavailable`() {
        val silent = object : HerdrClient by FakeHerdrClient() {
            override suspend fun listAgents(): List<HerdrAgent> = awaitCancellation()
        }

        assertEquals(0, cli(herdr = silent).run(listOf("guide", "parallel")))

        assertFalse(out.lines().takeWhile { !it.startsWith("Not applicable") }.any { it.contains("shogun open <id> --herdr") }, out.toString())
    }

    @Test
    fun `guide parallel with Orca in use names --orca while Orca is installed`() {
        assertEquals(0, cli(orca = FakeOrcaClient(running = false), runner = FakeToolPreferenceRepository(runner = RunnerId.ORCA)).run(listOf("guide", "parallel")))

        val applicable = out.lines().takeWhile { !it.startsWith("Not applicable") }
        assertTrue(applicable.any { it.contains("shogun open <id> --orca") }, out.toString())
        assertFalse(out.contains("--herdr"), out.toString())
    }

    @Test
    fun `guide parallel with Orca chosen but not installed falls back to Herdr`() {
        assertEquals(0, cli(herdr = fakeHerdr(), runner = FakeToolPreferenceRepository(runner = RunnerId.ORCA)).run(listOf("guide", "parallel")))

        assertTrue(out.contains("shogun open <id> --herdr"), out.toString())
        assertFalse(out.contains("--orca"), out.toString())
    }

    @Test
    fun `the plain guide names the runner in use`() {
        assertEquals(0, cli(orca = FakeOrcaClient(), runner = FakeToolPreferenceRepository(runner = RunnerId.ORCA)).run(listOf("guide", "--plain")))

        assertTrue(out.contains("shogun open <id> --orca"), out.toString())
        assertFalse(out.contains("--herdr"), out.toString())
    }

    @Test
    fun `session start with Orca in use names --orca`() {
        val exitCode = cli(everything(), input = """{"cwd": "$linked"}""", orca = FakeOrcaClient(), runner = FakeToolPreferenceRepository(runner = RunnerId.ORCA))
            .run(listOf("hook", "session-start"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(out.contains("Open another session with `shogun open <id> --orca`."), out.toString())
        assertFalse(out.contains("--herdr"), out.toString())
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
        val rules = lines.filterNot { it.startsWith("ShogunAi environment of worktree") }
        assertTrue(rules.size <= 10, out.toString())
        assertTrue(lines.any { it.contains("shogun open <id> --herdr") }, out.toString())
        assertTrue(lines.any { it.contains("shogun emulator start") }, out.toString())
        assertTrue(lines.any { it.contains("shogun simulator start") }, out.toString())
        assertTrue(lines.any { it.contains("shogun compose up") }, out.toString())
        assertTrue(lines.last().contains("shogun guide"), out.toString())
    }

    @Test
    fun `guide resources tells agents to run compose through shogun when the provider is declared`() {
        assertEquals(0, cli(declaring(RepositoryProviders(dockerCompose = DockerComposeProvider()))).run(listOf("guide", "resources")), err.toString())

        val applicable = out.lines().takeWhile { !it.startsWith("Not applicable") }.joinToString("\n")
        assertTrue(applicable.contains("`shogun compose up`"), out.toString())
        assertTrue(applicable.contains("`shogun compose down <worktree>`"), out.toString())
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
    fun `install --json reports the block without asking, applies it with --yes and then reports it up to date`() {
        val path = "$home/.gemini/GEMINI.md"

        assertEquals(0, cli().run(listOf("guide", "install", "--agent", "gemini", "--json")))
        val preview = out.toString()
        out.clear()
        assertEquals(0, cli().run(listOf("guide", "install", "--agent", "gemini", "--json", "--yes")))
        val applied = out.toString()
        out.clear()
        assertEquals(0, cli().run(listOf("guide", "install", "--agent", "gemini", "--json")))

        assertEquals("{\"path\":\"$path\",\"applied\":false,\"state\":\"missing\",\"warnings\":[]}\n", preview)
        assertEquals("{\"path\":\"$path\",\"applied\":true,\"state\":\"installed\",\"warnings\":[]}\n", applied)
        assertEquals("{\"path\":\"$path\",\"applied\":false,\"state\":\"installed\",\"warnings\":[]}\n", out.toString())
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

    @Test
    fun `session-start of a flutter repository tells to pass each declared device with -d`() {
        val providers = RepositoryProviders(flutter = FlutterProvider, emulator = EmulatorProvider(), iosSimulator = IosSimulatorProvider())

        val exitCode = cli(declaring(providers), input = """{"cwd": "$linked"}""").run(listOf("hook", "session-start"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(out.contains("`shogun emulator start` and then `flutter run -d \"\$ANDROID_SERIAL\"`"), out.toString())
        assertTrue(out.contains("`shogun simulator start` and then `flutter run -d \"\$IOS_SIMULATOR_UDID\"`"), out.toString())
        assertTrue(out.contains("`flutter emulators --launch`"), out.toString())
    }

    @Test
    fun `guide resources gives the flutter rule with fvm and only the declared devices`() {
        files.writeText("$linked/.fvmrc", "{\"flutter\": \"3.27.0\"}")

        assertEquals(0, cli(declaring(RepositoryProviders(flutter = FlutterProvider, emulator = EmulatorProvider()))).run(listOf("guide", "resources")), err.toString())

        val flutterLine = out.lines().takeWhile { !it.startsWith("Not applicable") }.single { it.contains("pub get") }
        assertTrue(flutterLine.contains("`fvm flutter run -d \"\$ANDROID_SERIAL\"`"), flutterLine)
        assertFalse(flutterLine.contains("IOS_SIMULATOR_UDID"), flutterLine)
    }

    @Test
    fun `session-start without the flutter provider says nothing about flutter`() {
        assertEquals(0, cli(everything(), input = """{"cwd": "$linked"}""").run(listOf("hook", "session-start")), err.toString())

        assertFalse(out.contains("flutter"), out.toString())
    }

    @Test
    fun `guide migrate compares the file it is given`() {
        assertEquals(0, cli().run(listOf("guide", "--migrate", "--file", "AGENTS.md")), err.toString())

        assertTrue(out.contains("Read $linked/AGENTS.md and compare it"), out.toString())
    }

    @Test
    fun `guide plain prints only the rules of the topic it is given`() {
        assertEquals(0, cli().run(listOf("guide", "--plain", "worktrees")), err.toString())

        assertTrue(out.lines().none { it.startsWith("## ") && it != "## worktrees" }, out.toString())
    }

    @Test
    fun `uninstall where there is no block says so`() {
        assertEquals(0, cli().run(listOf("guide", "uninstall", "--project", "--yes")), err.toString())

        assertTrue(out.contains("There is no ShogunAi guide block in $base/AGENTS.md"), out.toString())
    }

    @Test
    fun `install asks before writing and writes on yes`() {
        assertEquals(0, cli(answer = "y").run(listOf("guide", "install", "--project")), err.toString())

        assertTrue(err.contains("Write it? [y/N] "), err.toString())
        assertTrue(out.contains("Installed in $base/AGENTS.md"), out.toString())
        out.clear()
        assertEquals(0, cli(answer = "y").run(listOf("guide", "uninstall", "--project")), err.toString())
        assertTrue(err.contains("Remove it? [y/N] "), err.toString())
    }

    @Test
    fun `install for Codex with a blank CODEX_HOME uses the home folder`() {
        assertEquals(0, cli(environment = mapOf("CODEX_HOME" to " ")).run(listOf("guide", "install", "--agent", "codex", "--yes")))

        assertEquals("$block\n", files.writtenTexts.getValue("$home/.codex/AGENTS.md"))
    }

    @Test
    fun `status outside a repository lists the agents and the file it is given`() {
        assertEquals(0, cli(workingDirectory = "/tmp").run(listOf("guide", "status", "--file", "/tmp/NOTES.md")), err.toString())

        assertTrue(out.lines().none { it.startsWith("project:") }, out.toString())
        assertTrue(out.lines().any { it.startsWith("file: ") && it.endsWith("/tmp/NOTES.md") }, out.toString())
    }

    @Test
    fun `status doesn't warn about the hook when it isn't installed or its settings can't be read`() {
        cli().run(listOf("guide", "install", "--project", "--yes"))
        err.clear()

        assertEquals(0, cli().run(listOf("guide", "status")), err.toString())
        files.writeText("$home/.claude/settings.json", "{ not json")
        assertEquals(0, cli().run(listOf("guide", "status")), err.toString())

        assertFalse(err.contains("Claude Code gets the rules twice"), err.toString())
    }

    @Test
    fun `status outside a repository warns about a file with the block when the user's hook is installed`() {
        cli(workingDirectory = "/tmp").run(listOf("hooks", "install", "--yes"))
        cli(workingDirectory = "/tmp").run(listOf("guide", "install", "--file", "/tmp/NOTES.md", "--yes"))
        err.clear()

        assertEquals(0, cli(workingDirectory = "/tmp").run(listOf("guide", "status", "--file", "/tmp/NOTES.md")), err.toString())

        assertTrue(err.contains("Claude Code gets the rules twice"), err.toString())
    }

    @Test
    fun `guide migrate without a file asks for the one the person points to`() {
        assertEquals(0, cli().run(listOf("guide", "--migrate")), err.toString())

        assertTrue(out.contains("Read the AGENTS.md the person points you to"), out.toString())
    }
}
