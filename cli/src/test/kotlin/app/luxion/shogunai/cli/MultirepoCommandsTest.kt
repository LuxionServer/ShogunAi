package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.FakeAgentSessionStore
import app.luxion.shogunai.domain.FakeClaudeSessionRegistry
import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeGitRepositories
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakeHerdrClient
import app.luxion.shogunai.domain.FakeHerdrConfigSource
import app.luxion.shogunai.domain.FakeOrcaClient
import app.luxion.shogunai.domain.FakeKmpTestWiring
import app.luxion.shogunai.domain.FakeMachineCapacity
import app.luxion.shogunai.domain.FakeMultirepoSource
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeResourceLimitsRepository
import app.luxion.shogunai.domain.FakeRepositoryTrust
import app.luxion.shogunai.domain.io.RepositoryTrust
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.ClaudeSession
import app.luxion.shogunai.domain.model.EnvironmentDeclaration
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.SharedVariablesDeclaration
import app.luxion.shogunai.domain.usecase.ResolveMultirepoUseCase
import app.luxion.shogunai.domain.model.approvalSubject
import app.luxion.shogunai.domain.model.parseEnvironmentVariable
import app.luxion.shogunai.domain.model.HerdrWorkspace
import app.luxion.shogunai.domain.model.HerdrPane
import app.luxion.shogunai.domain.model.CapacityProfile
import app.luxion.shogunai.domain.model.MultirepoDeclaration
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.ResourceSettings
import app.luxion.shogunai.domain.model.WorkspaceSetting
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MultirepoCommandsTest {

    private val folder = "/home/dev/projects/product"
    private val worktreesRoot = "/home/dev/projects/product-worktrees"
    private val appBase = "$folder/product-app"
    private val apiBase = "$folder/product-api"
    private val docsBase = "$folder/product-docs"
    private val git = FakeGitRepositories()
    private val app = git.repository(appBase)
    private val api = git.repository(apiBase)
    private val docs = git.repository(docsBase, mainBranch = "main")
    private val declaration = MultirepoDeclaration(
        repos = linkedMapOf("app" to "product-app", "api" to "product-api", "docs" to "product-docs"),
        worktreesRoot = "../product-worktrees",
        defaultRepos = listOf("app", "api"),
    )
    private val source = FakeMultirepoSource(mapOf(folder to declaration))
    private val projects = FakeProjectRepository(
        listOf(
            Project("app", "product-app", ProjectConfig(appBase, "$worktreesRoot/app", emptyList())),
            Project("api", "product-api", ProjectConfig(apiBase, "$worktreesRoot/api", emptyList())),
            Project("docs", "product-docs", ProjectConfig(docsBase, "$worktreesRoot/docs", emptyList())),
        ),
    )
    private val fileManager = FakeFileManager(existing = listOf(folder, appBase, apiBase, docsBase, "/src/web"))
    private val herdr = FakeHerdrClient()
    private val out = StringBuilder()
    private val err = StringBuilder()
    private val envFile = Files.createTempFile("claude-env", ".sh")
    private val environmentStore = FakeEnvironmentStateStore()

    @AfterTest
    fun tearDown() {
        Files.deleteIfExists(envFile)
    }

    private fun cli(
        workingDirectory: String = folder,
        input: String = "",
        answer: String? = null,
        herdrClient: FakeHerdrClient = herdr,
        multirepos: FakeMultirepoSource = source,
        sessionStore: FakeAgentSessionStore = FakeAgentSessionStore(),
        claudeSessions: FakeClaudeSessionRegistry = FakeClaudeSessionRegistry(),
        provisioning: FakeRepositoryProvisioningSource = FakeRepositoryProvisioningSource(),
        trust: RepositoryTrust = RepositoryTrust.NONE,
        orca: FakeOrcaClient = FakeOrcaClient(installed = false),
    ) = ShogunCli(
        executor = git,
        fileManager = fileManager,
        projectRepository = projects,
        provisioningSource = provisioning,
        daemonSource = FakeGradleDaemonSource(),
        gradleUserHome = "/home/dev/.gradle",
        environmentStateStore = environmentStore,
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
        environmentVariable = { if (it == "CLAUDE_ENV_FILE") envFile.toString() else null },
        userHome = "/home/dev",
        herdr = herdrClient,
        herdrAgentStartTimeout = 100.milliseconds,
        readAnswer = { answer },
        sessionStore = sessionStore,
        multirepoSource = multirepos,
        claudeSessions = claudeSessions,
        isClaudeProcessAlive = { true },
        trust = trust,
    )

    private fun json(): JsonObject = Json.parseToJsonElement(out.toString().trim()).jsonObject

    private fun groupTask1(merged: Boolean = false) = with(git) {
        app.worktree("$worktreesRoot/app/TASK-1", "feature/TASK-1", merged)
        api.worktree("$worktreesRoot/api/TASK-1", "feature/TASK-1", merged)
    }

    @Test
    fun `new in the multirepo folder creates the default repositories' worktrees from their bases`() {
        val exitCode = cli().run(listOf("new", "TASK-12"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(listOf("$worktreesRoot/app/TASK-12", "$worktreesRoot/api/TASK-12"), out.lines().filter { it.isNotBlank() })
        assertTrue(git.ranIn(appBase, "git", "worktree", "add", "--no-track", "$worktreesRoot/app/TASK-12", "-b", "feature/TASK-12", "origin/develop"))
        assertTrue(git.ranIn(apiBase, "git", "worktree", "add", "--no-track", "$worktreesRoot/api/TASK-12", "-b", "feature/TASK-12", "origin/develop"))
        assertTrue(err.contains("app: created") && err.contains("api: created"))
        assertTrue(herdr.opened.isEmpty())
    }

    @Test
    fun `new with repos from inside a repository creates a group there too`() {
        val exitCode = cli(workingDirectory = appBase).run(listOf("new", "TASK-3", "--repos", "app,docs", "--fix"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(git.ranIn(docsBase, "git", "worktree", "add", "--no-track", "$worktreesRoot/docs/TASK-3", "-b", "fix/TASK-3", "origin/main"))
    }

    @Test
    fun `new inside a repository without repos creates a single worktree as before`() {
        val exitCode = cli(workingDirectory = appBase).run(listOf("new", "TASK-4"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("$worktreesRoot/app/TASK-4", out.toString().trim())
        assertTrue(git.calls.none { it.second == apiBase && it.first.take(3) == listOf("git", "worktree", "add") })
    }

    @Test
    fun `new is idempotent and reports the repositories already on the branch`() {
        with(git) { api.worktree("/home/dev/old/api-task-12", "feature/TASK-12") }

        val exitCode = cli().run(listOf("new", "TASK-12", "--json"))

        assertEquals(0, exitCode, err.toString())
        val repos = json()["repos"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("created", "existing"), repos.map { it["result"]!!.jsonPrimitive.content })
        assertEquals("/home/dev/old/api-task-12", repos[1]["path"]!!.jsonPrimitive.content)
        assertEquals("feature/TASK-12", json()["branch"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a failing repository is reported and the others are still created`() {
        api.failAdd = "fatal: boom"

        val exitCode = cli().run(listOf("new", "TASK-12"))

        assertEquals(1, exitCode)
        assertEquals("$worktreesRoot/app/TASK-12", out.toString().trim())
        assertTrue(err.contains("api: couldn't create its worktree"))
    }

    @Test
    fun `new rejects unknown aliases, options that don't apply and a multirepo without defaults`() {
        assertEquals(2, cli().run(listOf("new", "TASK-1", "--repos", "app,web")))
        assertTrue(err.contains("'web' isn't a repository of the multirepo; its repositories are app, api, docs"))
        assertEquals(2, cli().run(listOf("new", "--branch", "feature/x")))
        assertTrue(err.contains("--branch doesn't apply to a group"))
        source.declarations[folder] = declaration.copy(defaultRepos = emptyList())
        assertEquals(2, cli().run(listOf("new", "TASK-1")))
        assertTrue(err.contains("pass --repos"))
        assertTrue(git.calls.none { it.first.take(3) == listOf("git", "worktree", "add") })
    }

    @Test
    fun `new with repos outside a multirepo is a usage error`() {
        val exitCode = cli(workingDirectory = appBase, multirepos = FakeMultirepoSource()).run(listOf("new", "TASK-1", "--repos", "app"))

        assertEquals(2, exitCode)
        assertTrue(err.contains("--repos needs a multirepo"))
    }

    @Test
    fun `status in the multirepo folder lists the groups by branch and the ungrouped worktrees`() {
        groupTask1(merged = true)
        with(git) { docs.worktree("$worktreesRoot/docs/notes", "feature/NOTES") }

        val exitCode = cli().run(listOf("status"))

        assertEquals(0, exitCode, err.toString())
        val lines = out.lines()
        assertEquals("feature/TASK-1", lines[0])
        assertTrue(lines[1].startsWith("  app  $worktreesRoot/app/TASK-1") && lines[1].contains("merged") && lines[1].contains("of develop"))
        assertTrue(lines[2].startsWith("  api  $worktreesRoot/api/TASK-1"))
        assertEquals("ungrouped", lines[3])
        assertTrue(lines[4].contains("docs  $worktreesRoot/docs/notes  feature/NOTES"))
    }

    @Test
    fun `status json has the groups with each repository's state`() {
        groupTask1()

        cli().run(listOf("status", "--json"))

        val group = json()["groups"]!!.jsonArray.single().jsonObject
        assertEquals("feature/TASK-1", group["branch"]!!.jsonPrimitive.content)
        val repo = group["repos"]!!.jsonArray.first().jsonObject
        assertEquals(listOf("alias", "path", "branch", "base", "folderMissing", "uncommittedChanges", "ahead", "behind", "merged", "agents"), repo.keys.toList())
        assertEquals("develop", repo["base"]!!.jsonPrimitive.content)
    }

    @Test
    fun `status with no groups says so`() {
        cli().run(listOf("status"))

        assertEquals("No groups.", out.toString().trim())
    }

    @Test
    fun `open in orca in a multirepo folder is a usage error`() {
        groupTask1()
        val orca = FakeOrcaClient()

        val exitCode = cli(orca = orca).run(listOf("open", "TASK-1", "--orca"))

        assertEquals(2, exitCode)
        assertTrue(err.contains("can't be opened in Orca yet"), err.toString())
        assertTrue(orca.created.isEmpty())
        assertTrue(herdr.opened.isEmpty())
    }

    @Test
    fun `open a group opens each worktree under its repository`() {
        groupTask1()

        val exitCode = cli().run(listOf("open", "TASK-1", "--herdr"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(
            listOf(Triple(appBase, "$worktreesRoot/app/TASK-1", "TASK-1"), Triple(apiBase, "$worktreesRoot/api/TASK-1", "TASK-1")),
            herdr.opened,
        )
        assertTrue(out.contains("opened $worktreesRoot/app/TASK-1 in Herdr with"))
        assertTrue(out.contains("(pane w1:p1)"))
    }

    @Test
    fun `open a group reports focused and started-agent members in json`() {
        groupTask1()
        val app = "$worktreesRoot/app/TASK-1"
        val api = "$worktreesRoot/api/TASK-1"
        val herdr = FakeHerdrClient(
            alreadyOpen = setOf(app, api),
            panes = listOf(HerdrPane("w1:p1", "w1", app, agent = "claude", agentStatus = "idle"), HerdrPane("w2:p1", "w2", api)),
        )

        val exitCode = cli(herdrClient = herdr).run(listOf("open", "TASK-1", "--herdr", "--json"))

        assertEquals(0, exitCode, err.toString())
        val repos = json()["repos"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("focused", "started-agent"), repos.map { it["result"]!!.jsonPrimitive.content })
        assertEquals(listOf("w1:p1", "w2:p2"), repos.map { it["pane_id"]!!.jsonPrimitive.content })
        assertEquals("claude", repos[0]["agent"]!!.jsonPrimitive.content)
    }

    @Test
    fun `open a group names each member's session and reports it in json`() {
        groupTask1()
        val sessions = FakeClaudeSessionRegistry(started = herdr::startedClaudeSessions)

        val exitCode = cli(claudeSessions = sessions).run(listOf("open", "TASK-1", "--herdr", "--json"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(listOf("claude -n app-task-1", "claude -n api-task-1"), herdr.ran.map { it.second })
        val repos = json()["repos"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("app-task-1", "api-task-1"), repos.map { it["session_name"]!!.jsonPrimitive.content })
    }

    @Test
    fun `open a group prints each member's session name`() {
        groupTask1()
        val sessions = FakeClaudeSessionRegistry(started = herdr::startedClaudeSessions)

        cli(claudeSessions = sessions).run(listOf("open", "TASK-1", "--herdr"))

        assertTrue(out.contains("opened $worktreesRoot/app/TASK-1 in Herdr with Claude (pane w1:p1) as session app-task-1"), out.toString())
        assertTrue(out.contains("as session api-task-1"), out.toString())
    }

    @Test
    fun `open a group reports null for a session it can't confirm`() {
        groupTask1()

        cli().run(listOf("open", "TASK-1", "--herdr", "--json"))

        val repos = json()["repos"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf(JsonNull, JsonNull), repos.map { it["session_name"] })
    }

    @Test
    fun `open without a target reports the multirepo agent's session`() {
        source.agents[folder] = AgentLaunchConfig(agentCommand = "claude", useHeadroom = false)
        val sessions = FakeClaudeSessionRegistry(started = herdr::startedClaudeSessions)

        cli(claudeSessions = sessions).run(listOf("open", "--herdr", "--json"))

        assertEquals("product", json()["session_name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `status in the multirepo folder shows each repository's session names`() {
        groupTask1()
        val appTask = "$worktreesRoot/app/TASK-1"
        val apiTask = "$worktreesRoot/api/TASK-1"
        git.lsof = "p400\ncclaude\nfcwd\nn$appTask\np401\ncclaude\nfcwd\nn$apiTask\n"
        git.ps = "400 claude\n401 claude\n"
        val sessions = FakeClaudeSessionRegistry(listOf(ClaudeSession(400, appTask, "app-task-1"), ClaudeSession(401, apiTask, "api-task-1")))

        cli(claudeSessions = sessions).run(listOf("status"))

        val lines = out.lines()
        assertTrue(lines[1].startsWith("  app  $appTask") && lines[1].contains("agents=claude(app-task-1)"), out.toString())
        assertTrue(lines[2].startsWith("  api  $apiTask") && lines[2].contains("agents=claude(api-task-1)"), out.toString())

        out.setLength(0)
        cli(claudeSessions = sessions).run(listOf("status", "--json"))

        val repo = json()["groups"]!!.jsonArray.single().jsonObject["repos"]!!.jsonArray.first().jsonObject
        assertEquals(
            Json.parseToJsonElement("""[{"name": "claude", "state": null, "stateSource": null, "herdrState": null, "sessions": [{"name": "app-task-1", "pid": 400}]}]"""),
            repo["agents"],
        )
    }

    @Test
    fun `open a group fails for a member whose agent doesn't start`() {
        groupTask1()
        val app = "$worktreesRoot/app/TASK-1"
        val herdr = FakeHerdrClient(alreadyOpen = setOf(app), panes = listOf(HerdrPane("w1:p1", "w1", app, agent = "claude", agentStatus = "working")), startsAgents = false)

        val exitCode = cli(herdrClient = herdr).run(listOf("open", "TASK-1", "--herdr"))

        assertEquals(1, exitCode)
        assertTrue(out.contains("focused $app in Herdr (already open, pane w1:p1)"), out.toString())
        assertTrue(err.contains("couldn't open api ($worktreesRoot/api/TASK-1) in Herdr"), err.toString())
        assertTrue(err.contains("didn't start in Herdr pane w2:p1"), err.toString())
    }

    @Test
    fun `open in the multirepo folder rejects unknown and ambiguous groups and the agent option`() {
        groupTask1()
        with(git) {
            app.worktree("/w/app-fix", "fix/TASK-1")
            docs.worktree("/w/docs-fix", "fix/TASK-1")
        }

        assertEquals(1, cli().run(listOf("open", "TASK-9", "--herdr")))
        assertEquals(2, cli().run(listOf("open", "TASK-1", "--herdr")))
        assertTrue(err.contains("feature/TASK-1, fix/TASK-1"))
        assertEquals(0, cli().run(listOf("open", "fix/TASK-1", "--herdr")))
        assertEquals(2, cli().run(listOf("open", "feature/TASK-1", "--herdr", "--agent", "Codex")))
    }

    @Test
    fun `open without a target opens the multirepo agent in its own workspace`() {
        source.agents[folder] = AgentLaunchConfig(agentCommand = "claude", useHeadroom = false)

        val exitCode = cli().run(listOf("open", "--herdr"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(listOf(folder to "product"), herdr.createdWorkspaces)
        assertEquals(listOf("plain1:p1" to "claude -n product"), herdr.ran)
        assertTrue(out.contains("opened $folder in Herdr with"), out.toString())
    }

    @Test
    fun `open without a target starts the multirepo agent in its open workspace without one`() {
        source.agents[folder] = AgentLaunchConfig(agentCommand = "claude", useHeadroom = false)
        val herdr = FakeHerdrClient(workspaces = listOf(HerdrWorkspace("w5", "product", null)), panes = listOf(HerdrPane("w5:p1", "w5", folder)))

        val exitCode = cli(herdrClient = herdr).run(listOf("open", "--herdr", "--json"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("started-agent", json()["herdr"]!!.jsonPrimitive.content)
        assertEquals("w5:p2", json()["pane_id"]!!.jsonPrimitive.content)
        assertTrue(json()["opened"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(listOf("w5:p2" to "claude -n product"), herdr.ran)
    }

    @Test
    fun `open without a target inside a repository is a usage error`() {
        assertEquals(2, cli(workingDirectory = appBase).run(listOf("open", "--herdr")))
    }

    @Test
    fun `clean lists a merged group and keeps the others with their reason`() {
        groupTask1(merged = true)
        with(git) {
            app.worktree("$worktreesRoot/app/TASK-2", "feature/TASK-2", merged = true)
            api.worktree("$worktreesRoot/api/TASK-2", "feature/TASK-2")
        }

        val exitCode = cli().run(listOf("clean"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(out.contains("would remove the group feature/TASK-1"))
        assertTrue(out.contains("would remove app $worktreesRoot/app/TASK-1"))
        assertTrue(out.contains("kept feature/TASK-2 (api isn't merged)"))
        assertTrue(out.contains("Run 'shogun clean --yes' to remove the groups."))
        assertTrue(git.calls.none { it.first.take(3) == listOf("git", "worktree", "remove") })
    }

    @Test
    fun `clean yes removes the group's worktrees and deletes branches only when asked`() {
        groupTask1(merged = true)

        val exitCode = cli().run(listOf("clean", "--yes"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(git.ranIn(appBase, "git", "worktree", "remove", "$worktreesRoot/app/TASK-1"))
        assertTrue(git.ranIn(apiBase, "git", "worktree", "remove", "$worktreesRoot/api/TASK-1"))
        assertTrue(git.calls.none { it.first.take(2) == listOf("git", "branch") })

        groupTask1(merged = true)
        cli().run(listOf("clean", "--yes", "--delete-branches"))
        assertTrue(git.calls.any { it.first.take(2) == listOf("git", "branch") && it.second == apiBase })
    }

    @Test
    fun `clean json lists candidates and kept groups`() {
        groupTask1(merged = true)

        cli().run(listOf("clean", "--json"))

        assertEquals("feature/TASK-1", json()["candidates"]!!.jsonArray.single().jsonObject["branch"]!!.jsonPrimitive.content)
        assertTrue(json()["dryRun"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `session start in a group worktree adds the group's context`() {
        groupTask1()

        val exitCode = cli(input = """{"cwd": "$worktreesRoot/api/TASK-1"}""").run(listOf("hook", "session-start"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(out.contains("this worktree is api in the group feature/TASK-1"))
        assertTrue(out.contains("Other repositories of the group: app at $worktreesRoot/app/TASK-1."))
        assertTrue(out.contains("shogun new TASK-1 --repos docs"))
    }

    @Test
    fun `session start in the multirepo folder describes it and writes no environment`() {
        val exitCode = cli(input = """{"cwd": "$folder"}""").run(listOf("hook", "session-start"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(out.lines().first().startsWith("ShogunAi multirepo product at $folder"))
        assertTrue(out.contains("shogun status"))
        assertEquals("", envFile.readText())
    }

    @Test
    fun `in a workspace repository the multirepo takes precedence and the main checkout's rules follow`() {
        git.repository(folder)

        assertEquals(0, cli().run(listOf("new", "TASK-5")))
        assertTrue(git.calls.none { it.second == folder && it.first.take(3) == listOf("git", "worktree", "add") })

        out.clear()
        cli(input = """{"cwd": "$folder"}""").run(listOf("hook", "session-start"))
        val lines = out.lines().filter { it.isNotBlank() }
        assertTrue(lines.first().startsWith("ShogunAi multirepo product"))
        assertTrue(lines.size > 3)
    }

    @Test
    fun `multirepo init, add-repo, defaults, agent, show and delete write only the declaration`() {
        val fresh = FakeMultirepoSource()
        val home = "/home/dev/projects/platform"
        git.repository("/src/web")

        assertEquals(0, cli(workingDirectory = home, multirepos = fresh).run(listOf("multirepo", "init")))
        assertEquals("../platform-worktrees", fresh.declarations[home]?.worktreesRoot)
        assertEquals(0, cli(workingDirectory = home, multirepos = fresh, answer = "y").run(listOf("multirepo", "add-repo", "web", "/src/web")))
        assertEquals("/home/dev/projects/platform-worktrees/web", projects.list().first { it.config.baseRepositoryPath == "/src/web" }.config.worktreesRoot)
        assertEquals(0, cli(workingDirectory = home, multirepos = fresh).run(listOf("multirepo", "defaults", "web")))
        assertEquals(0, cli(workingDirectory = home, multirepos = fresh).run(listOf("multirepo", "agent", "codex", "--name", "Codex")))
        out.clear()
        assertEquals(0, cli(workingDirectory = home, multirepos = fresh).run(listOf("multirepo", "show", "--json")))
        assertEquals(listOf("web"), json()["defaultRepos"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("Codex", json()["agent"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals(null, fresh.declarations[home]?.agent)
        assertEquals(AgentLaunchConfig("Codex", "codex", useHeadroom = false), fresh.agents[home])
        assertEquals(0, cli(workingDirectory = home, multirepos = fresh).run(listOf("multirepo", "agent", "codex", "--name", "Codex", "--headroom")))
        assertEquals("headroom wrap codex", fresh.agents[home]?.resolvedCommand())
        assertEquals(2, cli(workingDirectory = home, multirepos = fresh).run(listOf("multirepo", "agent", "codex", "--headroom", "--no-headroom")))
        assertEquals(2, cli(workingDirectory = home, multirepos = fresh).run(listOf("multirepo", "defaults", "nope")))
        assertEquals(0, cli(workingDirectory = home, multirepos = fresh).run(listOf("multirepo", "delete")))
        assertTrue(fresh.declarations.isEmpty() && fresh.known.isEmpty())
        assertEquals(4, projects.list().size)
    }

    @Test
    fun `init with a name, name and name clear set the display name shown by show`() {
        val fresh = FakeMultirepoSource()
        val home = "/home/dev/projects/product-workspace"

        assertEquals(0, cli(workingDirectory = home, multirepos = fresh).run(listOf("multirepo", "init", "--name", "Product")))
        assertEquals(MultirepoDeclaration(worktreesRoot = "../product-workspace-worktrees", name = "Product"), fresh.declarations[home])
        out.clear()
        assertEquals(0, cli(workingDirectory = home, multirepos = fresh).run(listOf("multirepo", "show")))
        assertEquals("Product  $home", out.lines().first())
        out.clear()
        assertEquals(0, cli(workingDirectory = home, multirepos = fresh).run(listOf("multirepo", "name", "Product Suite", "--json")))
        assertEquals("Product Suite", json()["name"]!!.jsonPrimitive.content)
        assertEquals(0, cli(workingDirectory = home, multirepos = fresh).run(listOf("multirepo", "name", "--clear")))
        assertEquals(null, fresh.declarations[home]?.name)
        out.clear()
        assertEquals(0, cli(workingDirectory = home, multirepos = fresh).run(listOf("multirepo", "show", "--json")))
        assertEquals("product-workspace", json()["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a blank name is a usage error and changes nothing`() {
        assertEquals(2, cli().run(listOf("multirepo", "name", "  ")))
        assertTrue(err.contains("can't be blank"))
        assertEquals(declaration, source.declarations[folder])
        assertEquals(2, cli().run(listOf("multirepo", "name")))
    }

    @Test
    fun `init in a moved multirepo registers it, its missing projects and forgets the old folder`() {
        val moved = "/home/dev/work/product"
        val movedApp = "$moved/product-app"
        val movedApi = "$moved/product-api"
        git.repository(movedApp)
        git.repository(movedApi)
        fileManager.writeText(movedApp, "")
        fileManager.writeText(movedApi, "")
        val registry = FakeMultirepoSource(
            mapOf(moved to MultirepoDeclaration(linkedMapOf("app" to "product-app", "api" to "product-api"), "../product-worktrees")),
            known = listOf(folder),
            approveAll = false,
        )
        registry.missingFolders += folder

        assertEquals(1, cli(workingDirectory = moved, multirepos = registry).run(listOf("multirepo", "init", "--json")))
        assertTrue(err.contains("pass --yes"))
        assertTrue(registry.known.isEmpty())
        assertEquals(0, cli(workingDirectory = moved, multirepos = registry, answer = "n").run(listOf("multirepo", "init")))
        assertTrue(registry.known.isEmpty())
        assertTrue(projects.list().none { it.config.baseRepositoryPath == movedApp })
        out.clear()

        val exitCode = cli(workingDirectory = moved, multirepos = registry, answer = "y").run(listOf("multirepo", "init"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(out.contains("  app  $movedApp"))
        assertTrue(err.contains("Approve this multirepo?"))
        assertEquals(listOf(moved), registry.known)
        assertTrue(out.contains("approved and registered the existing multirepo product"))
        assertEquals("/home/dev/work/product-worktrees/app", projects.list().first { it.config.baseRepositoryPath == movedApp }.config.worktreesRoot)
        assertTrue(projects.list().any { it.config.baseRepositoryPath == appBase })
        out.clear()
        assertEquals(0, cli(workingDirectory = moved, multirepos = registry).run(listOf("multirepo", "init", "--json")))
        assertEquals(false, json()["registered"]!!.jsonPrimitive.content.toBoolean())
        assertTrue(json()["projects"]!!.jsonArray.isEmpty())
    }

    @Test
    fun `init over an existing declaration rejects the options of a new one and an invalid declaration`() {
        source.known.clear()

        assertEquals(2, cli().run(listOf("multirepo", "init", "--name", "Product")))
        assertTrue(err.contains("'shogun multirepo name'"))
        assertTrue(source.known.isEmpty())

        source.declarations[folder] = declaration.copy(defaultRepos = listOf("web"))
        assertEquals(1, cli().run(listOf("multirepo", "init")))
        assertTrue(source.known.isEmpty())
        assertEquals(declaration.copy(defaultRepos = listOf("web")), source.declarations[folder])
    }

    @Test
    fun `add-repo in json mode never asks and keeps the worktrees folder`() {
        val fresh = FakeMultirepoSource(mapOf(folder to MultirepoDeclaration(worktreesRoot = "../elsewhere")))
        val before = projects.list().first { it.id == "app" }

        cli(multirepos = fresh, answer = "y").run(listOf("multirepo", "add-repo", "app", appBase, "--json"))

        assertEquals(before, projects.list().first { it.id == "app" })
        assertEquals("declined", json()["worktreesFolderProposal"]!!.jsonPrimitive.content)
        assertFalse(err.contains("[y/N]"))
    }

    @Test
    fun `multirepo subcommands outside a multirepo fail with 1`() {
        assertEquals(1, cli(workingDirectory = appBase, multirepos = FakeMultirepoSource()).run(listOf("multirepo", "show")))
        assertTrue(err.contains("No multirepo found"))
    }

    @Test
    fun `without a multirepo the repository commands behave as before and nothing is written`() {
        val none = FakeMultirepoSource()
        val projectsBefore = projects.list()

        assertEquals(0, cli(workingDirectory = appBase, multirepos = none).run(listOf("status")))
        assertTrue(out.lines().first().startsWith(appBase))
        assertTrue(none.declarations.isEmpty() && none.known.isEmpty())
        assertEquals(projectsBefore, projects.list())
    }

    @Test
    fun `an invalid declaration is reported by the commands that read it`() {
        source.declarations[folder] = declaration.copy(defaultRepos = listOf("web"))

        assertEquals(1, cli().run(listOf("status")))
        assertTrue(err.contains("'web' isn't a declared repository"))
    }

    @Test
    fun `grouping existing worktrees reads only and never moves or writes anything`() {
        with(git) {
            app.worktree("/home/dev/projects/product-app-worktrees/TASK-7", "feature/TASK-7")
            api.worktree("/home/dev/.herdr/worktrees/product-api/task-7", "feature/TASK-7")
        }
        val projectsBefore = projects.list()

        listOf(listOf("status"), listOf("status", "--json"), listOf("clean")).forEach { cli().run(it) }
        cli(input = """{"cwd": "/home/dev/.herdr/worktrees/product-api/task-7"}""").run(listOf("hook", "session-start"))

        assertTrue(out.contains("feature/TASK-7"))
        assertTrue(git.calls.none { (command, _) -> command.take(2) == listOf("git", "worktree") && command.getOrNull(2) in setOf("add", "move", "remove") })
        assertTrue(fileManager.writtenTexts.isEmpty())
        assertEquals(projectsBefore, projects.list())
        assertEquals(mapOf(folder to declaration), source.declarations)
    }

    private fun folderIsARepository() = git.repository(folder).also { fileManager.writeText("$folder/.git", "") }

    @Test
    fun `show lists a detected workspace repository with the command to declare it`() {
        folderIsARepository()

        val exitCode = cli().run(listOf("multirepo", "show"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(out.lines().any { it.trim().startsWith("workspace  $folder") && it.contains("workspace") && it.contains("detected") })
        assertTrue(out.contains("shogun multirepo workspace set"))
    }

    @Test
    fun `show json marks the workspace repository as detected`() {
        folderIsARepository()

        cli().run(listOf("multirepo", "show", "--json"))

        val workspace = json()["repos"]!!.jsonArray.map { it.jsonObject }.first { it["alias"]!!.jsonPrimitive.content == "workspace" }
        assertEquals("true", workspace["workspace"]!!.jsonPrimitive.content)
        assertEquals("true", workspace["detected"]!!.jsonPrimitive.content)
    }

    @Test
    fun `workspace set declares it and nothing else changes`() {
        folderIsARepository()

        val exitCode = cli().run(listOf("multirepo", "workspace", "set", "--default"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(declaration.copy(workspace = WorkspaceSetting(default = true)), source.declarations[folder])
        assertFalse(out.contains("detected"))
    }

    @Test
    fun `workspace clear turns detection off`() {
        folderIsARepository()

        val exitCode = cli().run(listOf("multirepo", "workspace", "clear"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(declaration.copy(workspace = WorkspaceSetting(enabled = false)), source.declarations[folder])
        assertFalse(out.lines().any { it.trim().startsWith("workspace ") })
    }

    @Test
    fun `workspace set in a folder that is not a repository fails and changes nothing`() {
        val exitCode = cli().run(listOf("multirepo", "workspace", "set"))

        assertEquals(1, exitCode)
        assertTrue(err.contains("no workspace repository"))
        assertEquals(declaration, source.declarations[folder])
    }

    @Test
    fun `new with the workspace alias creates its worktree in the aliased folder`() {
        folderIsARepository()

        val exitCode = cli().run(listOf("new", "TASK-12", "--repos", "workspace,app"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(git.ranIn(folder, "git", "worktree", "add", "--no-track", "$worktreesRoot/workspace/TASK-12", "-b", "feature/TASK-12", "origin/develop"))
        assertTrue(git.ranIn(appBase, "git", "worktree", "add", "--no-track", "$worktreesRoot/app/TASK-12", "-b", "feature/TASK-12", "origin/develop"))
    }

    @Test
    fun `new with the workspace alias is rejected when detection is off`() {
        folderIsARepository()
        source.declarations[folder] = declaration.copy(workspace = WorkspaceSetting(enabled = false))

        val exitCode = cli().run(listOf("new", "TASK-12", "--repos", "workspace"))

        assertEquals(2, exitCode)
        assertTrue(err.contains("isn't a repository of the multirepo"))
    }

    @Test
    fun `new with an unknown alias lists the workspace among the repositories`() {
        folderIsARepository()

        cli().run(listOf("new", "TASK-12", "--repos", "web"))

        assertTrue(err.contains("workspace"))
    }

    @Test
    fun `clean lists the loose merged worktrees of members and of the workspace after the groups`() {
        val workspace = folderIsARepository()
        groupTask1(merged = true)
        with(git) {
            api.worktree("$worktreesRoot/api/TASK-3", "feature/TASK-3", merged = true)
            workspace.worktree("$worktreesRoot/workspace/TASK-4", "feature/TASK-4", merged = true)
            docs.worktree("$worktreesRoot/docs/TASK-5", "feature/TASK-5")
        }

        val exitCode = cli().run(listOf("clean"))

        assertEquals(0, exitCode, err.toString())
        val lines = out.lines()
        assertEquals(1, lines.count { it.contains("$worktreesRoot/api/TASK-1") })
        assertTrue(out.contains("would remove api $worktreesRoot/api/TASK-3"))
        assertTrue(out.contains("would remove workspace $worktreesRoot/workspace/TASK-4"))
        assertFalse(out.contains("TASK-5"))
        assertTrue(lines.indexOfFirst { it.contains("feature/TASK-1") } < lines.indexOfFirst { it.contains("TASK-3") })
        assertTrue(out.contains("Run 'shogun clean --yes' to remove them."))
    }

    @Test
    fun `clean yes removes the loose worktrees too`() {
        val workspace = folderIsARepository()
        with(git) { workspace.worktree("$worktreesRoot/workspace/TASK-4", "feature/TASK-4", merged = true) }

        val exitCode = cli().run(listOf("clean", "--yes"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(git.ranIn(folder, "git", "worktree", "remove", "$worktreesRoot/workspace/TASK-4"))
    }

    @Test
    fun `clean with nothing to clean says so`() {
        val exitCode = cli().run(listOf("clean"))

        assertEquals(0, exitCode, err.toString())
        assertTrue(out.contains("Nothing to clean up."))
    }

    @Test
    fun `project add in the multirepo folder registers the workspace project once under the multirepo's worktrees folder`() {
        folderIsARepository()

        assertEquals(0, cli().run(listOf("project", "add")), err.toString())
        assertEquals(0, cli().run(listOf("project", "add")), err.toString())

        val registered = projects.list().filter { it.config.baseRepositoryPath == folder }
        assertEquals(1, registered.size)
        assertEquals("$worktreesRoot/workspace", registered.single().config.worktreesRoot)
    }

    @Test
    fun `project add with detection off registers the folder as an ordinary project`() {
        folderIsARepository()
        source.declarations[folder] = declaration.copy(workspace = WorkspaceSetting(enabled = false))

        assertEquals(0, cli().run(listOf("project", "add")), err.toString())

        assertEquals("/home/dev/projects/product-worktrees", projects.list().single { it.config.baseRepositoryPath == folder }.config.worktreesRoot)
    }

    private fun stateInput(event: String, cwd: String) =
        """{"session_id":"s1","transcript_path":"/home/dev/.claude/projects/p/s1.jsonl","cwd":"$cwd","hook_event_name":"$event"}"""

    @Test
    fun `agent-state records the session of a multirepo folder that is not a repository`() {
        val store = FakeAgentSessionStore()

        val exitCode = cli(workingDirectory = "/", input = stateInput("UserPromptSubmit", folder), sessionStore = store).run(listOf("hook", "agent-state"))

        assertEquals(0, exitCode, err.toString())
        assertEquals("s1", store.records[folder]!!.single().sessionId)

        cli(workingDirectory = "/", input = stateInput("SessionEnd", folder), sessionStore = store).run(listOf("hook", "agent-state"))

        assertTrue(store.records[folder].orEmpty().isEmpty())
    }

    @Test
    fun `agent-state still writes nothing for an ordinary directory that is not a repository`() {
        val store = FakeAgentSessionStore()

        cli(workingDirectory = "/", input = stateInput("UserPromptSubmit", "/home/dev/notes"), sessionStore = store).run(listOf("hook", "agent-state"))
        cli(workingDirectory = "/", input = stateInput("UserPromptSubmit", "$folder/notes"), sessionStore = store).run(listOf("hook", "agent-state"))

        assertTrue(store.records.isEmpty())
    }

    private val cloned = "/home/dev/clones/evil"
    private val plantedAgent = AgentLaunchConfig(name = "Claude", agentCommand = "touch /tmp/marker; claude", useHeadroom = false)

    private fun clonedWithDeclaration(): FakeMultirepoSource {
        git.repository(cloned)
        fileManager.writeText("$cloned/.git", "")
        return FakeMultirepoSource(mapOf(cloned to MultirepoDeclaration(worktreesRoot = "../wt", agent = plantedAgent)), known = emptyList(), approveAll = false)
    }

    @Test
    fun `a declaration in a cloned repository never reaches Herdr`() {
        val multirepos = clonedWithDeclaration()

        val exitCode = cli(workingDirectory = cloned, multirepos = multirepos).run(listOf("open", "--herdr"))

        assertEquals(2, exitCode)
        assertTrue(herdr.ran.isEmpty() && herdr.createdWorkspaces.isEmpty())
        assertTrue(err.contains("ignoring $cloned/.shogunai-multirepo.json") && err.contains("shogun multirepo trust"))
        assertFalse(out.contains("Claude"))
    }

    @Test
    fun `an ignored declaration in a parent folder leaves the repository commands as they were`() {
        val multirepos = FakeMultirepoSource(mapOf(folder to declaration), known = emptyList(), approveAll = false)

        assertEquals(0, cli(workingDirectory = appBase, multirepos = multirepos).run(listOf("status")))

        assertTrue(out.lines().first().startsWith(appBase))
        assertEquals(1, err.lines().count { it.contains("shogun multirepo trust") })
        err.clear()
        assertEquals(2, cli(workingDirectory = appBase, multirepos = multirepos).run(listOf("new", "TASK-1", "--repos", "app")))
        assertTrue(err.contains("--repos needs a multirepo approved on this machine"))
    }

    @Test
    fun `hooks say nothing about an ignored declaration`() {
        val multirepos = clonedWithDeclaration()

        cli(input = """{"cwd": "$cloned"}""", multirepos = multirepos).run(listOf("hook", "session-start"))

        assertFalse(out.contains("multirepo") || err.contains("multirepo"))
    }

    @Test
    fun `a multirepo registered before approvals existed needs one review`() {
        val multirepos = FakeMultirepoSource(mapOf(folder to declaration), approveAll = false)

        assertEquals(0, cli(workingDirectory = appBase, multirepos = multirepos).run(listOf("status")))
        assertTrue(err.contains("shogun multirepo trust"))

        assertEquals(0, cli(multirepos = multirepos, answer = "y").run(listOf("multirepo", "trust")), err.toString())
        err.clear()
        out.clear()
        assertEquals(0, cli(multirepos = multirepos).run(listOf("status", "--json")))
        assertTrue(json().containsKey("groups"))
        assertFalse(err.contains("shogun multirepo trust"))
    }

    @Test
    fun `multirepo trust reviews the declaration and approves it on yes`() {
        git.repository("/srv/other/api")
        fileManager.writeText("/srv/other/api", "")
        val declared = declaration.copy(repos = declaration.repos + ("api" to "/srv/other/api"), agent = plantedAgent)
        val multirepos = FakeMultirepoSource(mapOf(folder to declared), known = emptyList(), approveAll = false)

        assertEquals(0, cli(multirepos = multirepos, answer = "y").run(listOf("multirepo", "trust")))

        assertTrue(out.contains("Multirepo declared by $folder/.shogunai-multirepo.json"))
        assertTrue(out.lines().any { it.contains("api  /srv/other/api  (outside /home/dev/projects)") })
        assertTrue(out.lines().any { it.contains("app  $appBase") && !it.contains("outside") })
        assertTrue(out.contains("agent is ignored"))
        assertFalse(out.contains("touch /tmp/marker"))
        assertTrue(out.contains("approved"))
        assertEquals(listOf(folder), multirepos.known)
        out.clear()
        assertEquals(0, cli(multirepos = multirepos).run(listOf("open", "--herdr")))
        assertEquals(listOf("plain1:p1" to "claude -n product"), herdr.ran)
    }

    @Test
    fun `multirepo trust approves nothing on no, without a terminal or for an invalid declaration`() {
        val multirepos = FakeMultirepoSource(mapOf(folder to declaration), known = emptyList(), approveAll = false)

        assertEquals(0, cli(multirepos = multirepos, answer = "").run(listOf("multirepo", "trust")))
        assertEquals(1, cli(multirepos = multirepos, answer = null).run(listOf("multirepo", "trust")))
        assertTrue(err.contains("pass --yes"))
        assertTrue(multirepos.known.isEmpty() && multirepos.approvals.isEmpty())

        multirepos.declarations[folder] = declaration.copy(repos = declaration.repos + ("api" to "product-api\u001b[2K\r"))
        err.clear()
        assertEquals(1, cli(multirepos = multirepos).run(listOf("multirepo", "trust", "--yes")))
        assertTrue(err.contains("control characters") && !err.contains("\u001b"))
        assertTrue(multirepos.known.isEmpty())

        err.clear()
        assertEquals(1, cli(workingDirectory = "/home/dev/elsewhere", multirepos = multirepos).run(listOf("multirepo", "trust")))
        assertTrue(err.contains("No multirepo found"))
    }

    @Test
    fun `multirepo trust takes a folder and --yes, and --revoke forgets it keeping the file`() {
        val multirepos = FakeMultirepoSource(mapOf(folder to declaration), known = emptyList(), approveAll = false)

        assertEquals(0, cli(workingDirectory = "/home/dev", multirepos = multirepos).run(listOf("multirepo", "trust", folder, "--yes")))
        assertEquals(listOf(folder), multirepos.known)
        out.clear()
        assertEquals(0, cli(multirepos = multirepos).run(listOf("multirepo", "trust", "--json")))
        assertEquals("true", json()["approved"]!!.jsonPrimitive.content)

        assertEquals(0, cli(multirepos = multirepos).run(listOf("multirepo", "trust", "--revoke")))

        assertTrue(multirepos.known.isEmpty())
        assertEquals(declaration, multirepos.declarations[folder])
        assertEquals(2, cli(multirepos = multirepos).run(listOf("multirepo", "trust", "--yes", "--revoke")))
    }

    @Test
    fun `show reports an unapproved multirepo and an ignored declared agent`() {
        val multirepos = FakeMultirepoSource(mapOf(folder to declaration.copy(agent = plantedAgent)), known = emptyList(), approveAll = false)

        assertEquals(0, cli(multirepos = multirepos).run(listOf("multirepo", "show")))
        assertTrue(out.contains("not approved on this machine; review it with 'shogun multirepo trust'"))
        assertTrue(out.contains("agent  Claude (claude)"))
        assertTrue(out.contains("agent is ignored"))

        out.clear()
        assertEquals(0, cli(multirepos = multirepos).run(listOf("multirepo", "show", "--json")))
        assertEquals("false", json()["approved"]!!.jsonPrimitive.content)
        assertEquals("true", json()["ignoredDeclaredAgent"]!!.jsonPrimitive.content)
    }

    @Test
    fun `writing subcommands refuse an unapproved multirepo and change nothing`() {
        val multirepos = FakeMultirepoSource(mapOf(folder to declaration), approveAll = false)

        listOf(
            listOf("multirepo", "defaults", "docs"),
            listOf("multirepo", "agent", "codex"),
            listOf("multirepo", "remove-repo", "docs"),
            listOf("multirepo", "worktrees-root", "/tmp/w"),
        ).forEach { arguments ->
            err.clear()
            assertEquals(1, cli(multirepos = multirepos).run(arguments))
            assertTrue(err.contains("shogun multirepo trust"), err.toString())
        }
        assertEquals(declaration, multirepos.declarations[folder])
        assertTrue(multirepos.agents.isEmpty())
    }

    private val apiProvisioning = RepositoryProvisioning(
        environment = EnvironmentDeclaration(listOf(parseEnvironmentVariable("SERVER_PORT", "\${port:http}"))),
    )
    private val appProvisioning = RepositoryProvisioning(
        environment = EnvironmentDeclaration(listOf(parseEnvironmentVariable("APP_NAME", "demo"))),
    )
    private val sharedProvisioning = FakeRepositoryProvisioningSource(mapOf(appBase to appProvisioning, apiBase to apiProvisioning))

    private fun sharing(apiUrl: String = "http://localhost:\${port:api.http}") {
        val env = SharedVariablesDeclaration(mapOf("app" to mapOf("API_URL" to apiUrl)))
        source.declarations[folder] = declaration.copy(env = env)
    }

    private fun env(directory: String, trust: RepositoryTrust = FakeRepositoryTrust(approvesEverything = true)): Map<String, String> {
        out.clear()
        assertEquals(0, cli(directory, provisioning = sharedProvisioning, trust = trust).run(listOf("env", "--format", "json")))
        return Json.parseToJsonElement(out.toString().trim()).jsonObject.mapValues { it.value.jsonPrimitive.content }
    }

    @Test
    fun `env gives a group worktree the port its partner reserves`() {
        sharing()
        groupTask1()

        val app = env("$worktreesRoot/app/TASK-1")
        val api = env("$worktreesRoot/api/TASK-1")

        assertEquals("http://localhost:${api.getValue("SERVER_PORT")}", app["API_URL"])
        assertEquals("demo", app["APP_NAME"])
        assertEquals("", err.toString())
    }

    @Test
    fun `env names a shared variable that can't be resolved and still succeeds`() {
        sharing("http://localhost:\${port:api.admin}")
        groupTask1()

        val app = env("$worktreesRoot/app/TASK-1")

        assertEquals(mapOf("APP_NAME" to "demo"), app)
        assertTrue(err.contains("API_URL") && err.contains("'api' doesn't declare the port 'admin'"), err.toString())
    }

    @Test
    fun `env points to shogun trust when the referenced repository isn't approved`() {
        sharing()
        groupTask1()

        env("$worktreesRoot/app/TASK-1", FakeRepositoryTrust(mapOf(appBase to appProvisioning.approvalSubject)))

        assertTrue(err.contains("API_URL") && err.contains("shogun trust") && err.contains(apiBase), err.toString())
    }

    @Test
    fun `env reports invalid shared variables without failing`() {
        sharing("\${port}")
        groupTask1()

        val app = env("$worktreesRoot/app/TASK-1")

        assertEquals(mapOf("APP_NAME" to "demo"), app)
        assertTrue(err.contains("API_URL") && err.contains(".shogunai-multirepo.json"), err.toString())
    }

    @Test
    fun `the session start hook delivers shared variables and names unresolved ones`() {
        sharing()
        groupTask1()

        val exitCode = cli(
            input = """{"cwd": "$worktreesRoot/app/TASK-1"}""",
            provisioning = sharedProvisioning,
            trust = FakeRepositoryTrust(approvesEverything = true),
        ).run(listOf("hook", "session-start"))

        assertEquals(0, exitCode)
        assertTrue(envFile.readText().contains("export API_URL='http://localhost:20000'"), envFile.readText())
        assertTrue(out.contains("API_URL"))

        sharing("http://localhost:\${port:api.admin}")
        out.clear()
        cli(
            input = """{"cwd": "$worktreesRoot/app/TASK-1"}""",
            provisioning = sharedProvisioning,
            trust = FakeRepositoryTrust(approvesEverything = true),
        ).run(listOf("hook", "session-start"))

        assertTrue(out.contains("Shared variable API_URL of the multirepo isn't available"), out.toString())
    }

    @Test
    fun `show and trust list the shared variables`() {
        sharing()

        cli().run(listOf("multirepo", "show"))
        assertTrue(out.contains("shared variables") && out.contains("API_URL=http://localhost:\${port:api.http}"), out.toString())

        out.clear()
        cli().run(listOf("multirepo", "show", "--json"))
        assertEquals("http://localhost:\${port:api.http}", json()["env"]!!.jsonObject["app"]!!.jsonObject["API_URL"]!!.jsonPrimitive.content)

        out.clear()
        cli(answer = "n").run(listOf("multirepo", "trust"))
        assertTrue(out.contains("API_URL=http://localhost:\${port:api.http}"), out.toString())
    }

    private val apiUrl = "http://localhost:${'$'}{port:api.http}"

    @Test
    fun `env set adds a shared variable, keeps the approval and env lists it`() {
        source.approveAll = false
        source.approve(folder, ResolveMultirepoUseCase(source, git, fileManager).build(folder, declaration).approvalSubject)

        val exitCode = cli().run(listOf("multirepo", "env", "set", "app", "API_URL", apiUrl))

        assertEquals(0, exitCode, err.toString())
        assertEquals(mapOf("app" to mapOf("API_URL" to apiUrl)), source.declarations.getValue(folder).env?.byAlias)
        assertEquals(declaration, source.declarations.getValue(folder).copy(env = null))
        assertTrue(ResolveMultirepoUseCase(source, git, fileManager).build(folder, source.declarations.getValue(folder)).approved)
        out.clear()
        assertEquals(0, cli().run(listOf("multirepo", "env")))
        assertTrue(out.lines().map { it.trim() }.containsAll(listOf("app", "API_URL=$apiUrl")), out.toString())
        out.clear()
        assertEquals(0, cli().run(listOf("multirepo", "env", "--json")))
        assertEquals(apiUrl, json()["env"]!!.jsonObject["app"]!!.jsonObject["API_URL"]!!.jsonPrimitive.content)
    }

    @Test
    fun `env with no variables says so`() {
        assertEquals(0, cli().run(listOf("multirepo", "env")))
        assertTrue(out.contains("shares no variables"), out.toString())
    }

    @Test
    fun `env set rejects an invalid template with 1 and an unknown repository with 2`() {
        assertEquals(1, cli().run(listOf("multirepo", "env", "set", "app", "DEBUG_PORT", "${'$'}{port}")))
        assertTrue(err.contains(".shogunai.json"), err.toString())
        assertEquals(2, cli().run(listOf("multirepo", "env", "set", "web", "API_URL", "x")))
        assertTrue(err.contains("'web' isn't a repository of the multirepo") && err.contains("app, api, docs"), err.toString())
        assertEquals(declaration, source.declarations.getValue(folder))
    }

    @Test
    fun `env unset removes the last variable and refuses an undeclared one`() {
        source.declarations[folder] = declaration.copy(env = SharedVariablesDeclaration(mapOf("app" to mapOf("API_URL" to apiUrl))))

        assertEquals(1, cli().run(listOf("multirepo", "env", "unset", "app", "MISSING")))
        assertTrue(err.contains("'app' has no shared variable 'MISSING'"), err.toString())
        assertEquals(0, cli().run(listOf("multirepo", "env", "unset", "app", "API_URL")), err.toString())
        assertNull(source.declarations.getValue(folder).env)
    }

    @Test
    fun `env set in an unapproved multirepo is refused while env lists it`() {
        source.approveAll = false
        source.remember(folder)
        val tampered = declaration.copy(env = SharedVariablesDeclaration(mapOf("app" to mapOf("API_URL" to apiUrl))))
        source.declarations[folder] = tampered

        assertEquals(1, cli().run(listOf("multirepo", "env", "set", "app", "API_URL", "x")))
        assertTrue(err.contains("shogun multirepo trust"), err.toString())
        assertEquals(tampered, source.declarations.getValue(folder))
        out.clear()
        assertEquals(0, cli().run(listOf("multirepo", "env")))
        assertTrue(out.contains("API_URL=$apiUrl"), out.toString())
    }

    @Test
    fun `remove-repo of a repository referenced by a shared variable fails and changes nothing`() {
        val shared = declaration.copy(env = SharedVariablesDeclaration(mapOf("app" to mapOf("API_URL" to apiUrl))))
        source.declarations[folder] = shared

        assertEquals(1, cli().run(listOf("multirepo", "remove-repo", "api")))
        assertTrue(err.contains("app.API_URL"), err.toString())
        assertEquals(shared, source.declarations.getValue(folder))
    }

    @Test
    fun `workspace detect returns a turned off workspace to detection`() {
        folderIsARepository()
        source.declarations[folder] = declaration.copy(workspace = WorkspaceSetting(alias = "docs-root", default = true))

        val exitCode = cli().run(listOf("multirepo", "workspace", "detect"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(declaration, source.declarations.getValue(folder))
        assertTrue(out.contains("detected again as 'workspace'; it isn't a default repository"), out.toString())
        assertTrue(out.lines().any { it.trim().startsWith("workspace  $folder") && it.contains("detected") })
        out.clear()
        assertEquals(0, cli().run(listOf("multirepo", "workspace", "detect")))
        assertTrue(out.contains("already detected"), out.toString())
    }

    @Test
    fun `workspace detect fails outside a repository and when the alias is taken`() {
        source.declarations[folder] = declaration.copy(workspace = WorkspaceSetting(enabled = false))
        assertEquals(1, cli().run(listOf("multirepo", "workspace", "detect")))
        assertTrue(err.contains("no workspace repository"), err.toString())

        folderIsARepository()
        git.repository("/src/web")
        val taken = declaration.copy(repos = declaration.repos + ("workspace" to "/src/web"), workspace = WorkspaceSetting(alias = "root"))
        source.declarations[folder] = taken
        assertEquals(1, cli().run(listOf("multirepo", "workspace", "detect")))
        assertTrue(err.contains("'workspace' is already taken"), err.toString())
        assertEquals(taken, source.declarations.getValue(folder))
    }
}
