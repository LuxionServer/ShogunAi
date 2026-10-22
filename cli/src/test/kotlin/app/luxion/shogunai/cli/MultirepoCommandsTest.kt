package app.luxion.shogunai.cli

import app.luxion.shogunai.domain.FakeAgentSessionStore
import app.luxion.shogunai.domain.FakeAndroidSdk
import app.luxion.shogunai.domain.FakeAppleSimulators
import app.luxion.shogunai.domain.FakeDerivedDataSource
import app.luxion.shogunai.domain.FakeEnvironmentStateStore
import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeGitRepositories
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakeHerdrClient
import app.luxion.shogunai.domain.FakeHerdrConfigSource
import app.luxion.shogunai.domain.FakeKmpTestWiring
import app.luxion.shogunai.domain.FakeMachineCapacity
import app.luxion.shogunai.domain.FakeMultirepoSource
import app.luxion.shogunai.domain.FakePortProbe
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeResourceLimitsRepository
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.CapacityProfile
import app.luxion.shogunai.domain.model.MultirepoDeclaration
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.ResourceSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    ) = ShogunCli(
        executor = git,
        fileManager = fileManager,
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
        workingDirectory = workingDirectory,
        out = out,
        err = err,
        readInput = { input },
        canonicalPath = { it.trimEnd('/') },
        environmentVariable = { if (it == "CLAUDE_ENV_FILE") envFile.toString() else null },
        userHome = "/home/dev",
        herdr = herdrClient,
        readAnswer = { answer },
        sessionStore = FakeAgentSessionStore(),
        multirepoSource = multirepos,
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
    fun `open a group opens each worktree under its repository`() {
        groupTask1()

        val exitCode = cli().run(listOf("open", "TASK-1", "--herdr"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(
            listOf(Triple(appBase, "$worktreesRoot/app/TASK-1", "TASK-1"), Triple(apiBase, "$worktreesRoot/api/TASK-1", "TASK-1")),
            herdr.opened,
        )
        assertTrue(out.contains("opened $worktreesRoot/app/TASK-1 in Herdr with"))
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
        source.declarations[folder] = declaration.copy(agent = AgentLaunchConfig(agentCommand = "claude", useHeadroom = false))

        val exitCode = cli().run(listOf("open", "--herdr"))

        assertEquals(0, exitCode, err.toString())
        assertEquals(listOf(folder to "product"), herdr.createdWorkspaces)
        assertEquals(listOf("plain1:p1" to "claude"), herdr.ran)
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
        assertEquals(2, cli(workingDirectory = home, multirepos = fresh).run(listOf("multirepo", "defaults", "nope")))
        assertEquals(0, cli(workingDirectory = home, multirepos = fresh).run(listOf("multirepo", "delete")))
        assertTrue(fresh.declarations.isEmpty() && fresh.known.isEmpty())
        assertEquals(4, projects.list().size)
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
}
