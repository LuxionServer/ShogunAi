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
import app.luxion.shogunai.domain.model.WorkspaceSetting
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
        sessionStore: FakeAgentSessionStore = FakeAgentSessionStore(),
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
        sessionStore = sessionStore,
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
        source.agents[folder] = AgentLaunchConfig(agentCommand = "claude", useHeadroom = false)

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
        assertEquals(listOf("plain1:p1" to "claude"), herdr.ran)
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
}
