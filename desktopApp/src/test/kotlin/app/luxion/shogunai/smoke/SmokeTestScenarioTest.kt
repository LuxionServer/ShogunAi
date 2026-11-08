package app.luxion.shogunai.smoke

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.io.UserPathEntries
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.usecase.ClaudeCodeHooks
import app.luxion.shogunai.domain.usecase.CommandLineTool
import app.luxion.shogunai.domain.usecase.CommandLineToolState
import app.luxion.shogunai.domain.usecase.HooksInstallState
import app.luxion.shogunai.ui.projectconfig.ProjectConfigViewModel
import app.luxion.shogunai.ui.projectconfig.RepositoryInspection
import app.luxion.shogunai.ui.shell.ClaudeHooksState
import app.luxion.shogunai.ui.shell.FakeLauncherFiles
import app.luxion.shogunai.ui.shell.TourActions
import app.luxion.shogunai.ui.shell.TourState
import app.luxion.shogunai.ui.shell.TourStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmokeTestScenarioTest {

    private val launcherFiles = FakeLauncherFiles()
    private val settingsFiles = FakeFileManager()
    private val userPath = mutableListOf<String>()
    private val projects = FakeProjectRepository()
    private val root = Files.createTempDirectory("smoke-test").toFile()
    private val repository = File(root, "main-repo").apply { mkdirs() }
    private var tourCreated = 0
    private var tourEnded = 0
    private var tourProject: Project? = null

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        File(repository, ".env.local").writeText("GREETING=hello\n")
        File(repository, "config").mkdirs()
        File(repository, "config/local.json").writeText("{\"port\": 8080}\n")
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        root.deleteRecursively()
    }

    private fun macTool() = CommandLineTool(
        os = HostOs.MACOS,
        appLauncher = "/Applications/ShogunAi.app/Contents/MacOS/ShogunAi",
        home = "/Users/dev",
        pathFolders = { listOf("/usr/bin", "/bin", "/opt/homebrew/bin") },
        userBinFolders = listOf("/opt/homebrew/bin", "/usr/local/bin", "/Users/dev/.local/bin"),
        findShogun = { launcherFiles.links.keys.firstOrNull() },
        files = launcherFiles,
        userPath = recordingUserPath(),
    ).also {
        launcherFiles.writable += "/opt/homebrew/bin"
        launcherFiles.others += "/Applications/ShogunAi.app/Contents/MacOS/ShogunAi"
    }

    private fun linuxToolWithoutLocalBinOnPath() = CommandLineTool(
        os = HostOs.LINUX,
        appLauncher = "/opt/shogunai/bin/ShogunAi",
        home = "/home/dev",
        pathFolders = { listOf("/usr/bin", "/bin") },
        userBinFolders = listOf("/home/dev/.local/bin"),
        findShogun = { null },
        files = launcherFiles,
        userPath = recordingUserPath(),
    ).also { launcherFiles.others += "/opt/shogunai/bin/ShogunAi" }

    private fun windowsTool() = CommandLineTool(
        os = HostOs.WINDOWS,
        appLauncher = "C:\\Program Files\\ShogunAi\\ShogunAi.exe",
        home = "C:\\Users\\dev",
        pathFolders = { listOf("C:\\Windows\\system32") },
        userBinFolders = emptyList(),
        findShogun = { null },
        files = launcherFiles,
        userPath = recordingUserPath(),
    ).also { launcherFiles.others += "C:\\Program Files\\ShogunAi\\runtime\\bin\\java.exe" }

    private fun recordingUserPath() = object : UserPathEntries {
        override suspend fun contains(folder: String) = folder in userPath
        override suspend fun add(folder: String) {
            userPath += folder
        }
        override suspend fun remove(folder: String) {
            userPath -= folder
        }
    }

    private fun scenario(
        tool: CommandLineTool = macTool(),
        declared: List<String> = listOf("config/local.json"),
        createWorktree: suspend (ProjectConfig, String) -> Result<Worktree> = ::copyingWorktree,
        inspect: suspend (String) -> Result<RepositoryInspection> = { Result.success(RepositoryInspection(it, "main")) },
    ): SmokeTestScenario {
        val hooks = ClaudeHooksState(
            hooks = ClaudeCodeHooks(settingsFiles),
            settingsPath = { "/home/dev/.claude/settings.json" },
            tool = tool,
            noticeDismissedStore = { false },
            rememberNoticeDismissed = {},
        )
        val tour = TourState(
            TourActions(
                create = {
                    tourCreated++
                    Result.success(sampleProject("ShogunAi tour", "/home/dev/.shogunai/tour/repository")).onSuccess { tourProject = it }
                },
                end = {
                    tourEnded++
                    tourProject = null
                    Result.success(Unit)
                },
                existingProject = { tourProject },
                leftoverFolder = { false },
                toolAvailable = {
                    hooks.refresh()
                    hooks.toolStatus?.state == CommandLineToolState.AVAILABLE
                },
                offered = { false },
                rememberOffered = {},
            ),
        )
        val provisioning = FakeRepositoryProvisioningSource(mapOf(repository.path to RepositoryProvisioning(copyPatterns = declared)))
        return SmokeTestScenario(
            hooks = hooks,
            tour = tour,
            projectForm = { ProjectConfigViewModel(projects, provisioning, null, inspectRepository = inspect) },
            declaredCopies = { provisioning.read(it).copyPatterns },
            createWorktree = createWorktree,
        )
    }

    private fun copyingWorktree(config: ProjectConfig, taskId: String): Result<Worktree> {
        val worktree = File(root, "main-repo-worktrees/$taskId").apply { mkdirs() }
        (config.secretFiles + "config/local.json").forEach { entry ->
            File(repository, entry).copyTo(File(worktree, entry), overwrite = true)
        }
        return Result.success(Worktree(worktree.path, "feature/$taskId"))
    }

    private fun sampleProject(name: String, path: String) =
        Project("tour-id", name, ProjectConfig(baseRepositoryPath = path, worktreesRoot = "$path-worktrees", secretFiles = emptyList()))

    private fun run(scenario: SmokeTestScenario, repositoryPath: String? = repository.path, localFiles: List<String> = listOf(".env.local")) =
        runBlocking { scenario.run(repositoryPath, localFiles) }

    private fun SmokeTestReport.status(name: SmokeStepName) = steps.single { it.name == name }.status

    private fun SmokeTestReport.detail(name: SmokeStepName) = steps.single { it.name == name }.detail

    @Test
    fun `on a Mac opened from the Dock it installs the command into Homebrew's folder, the hooks, and copies the local files`() {
        val report = run(scenario())

        assertTrue(report.passed)
        assertEquals(0, report.exitStatus)
        assertEquals(CommandLineToolState.AVAILABLE, report.commandLineTool?.state)
        assertEquals("/opt/homebrew/bin", report.commandLineTool?.folder)
        assertEquals("/Applications/ShogunAi.app/Contents/MacOS/ShogunAi", launcherFiles.links["/opt/homebrew/bin/shogun"])
        assertEquals(SmokeStepStatus.PASSED, report.status(SmokeStepName.INSTALL_COMMAND))
        assertEquals(HooksInstallState.INSTALLED, report.hooksState)
        assertEquals(TourStep.OPEN_AGENT, report.tourReached)
        assertFalse(report.tourToolStepShown)
        assertEquals(listOf(".env.local", "config/local.json"), report.copiedFiles)
    }

    @Test
    fun `a command already available is left as it is`() {
        launcherFiles.links["/opt/homebrew/bin/shogun"] = "/Applications/ShogunAi.app/Contents/MacOS/ShogunAi"

        val report = run(scenario())

        assertEquals(SmokeStepStatus.SKIPPED, report.status(SmokeStepName.INSTALL_COMMAND))
        assertEquals(SmokeStepStatus.PASSED, report.status(SmokeStepName.INSTALL_HOOKS))
        assertTrue(report.passed)
    }

    @Test
    fun `without a writable folder on the PATH the command lands in local bin and the hooks aren't offered`() {
        val report = run(scenario(tool = linuxToolWithoutLocalBinOnPath()))

        assertEquals(CommandLineToolState.INSTALLED_NOT_ON_PATH, report.commandLineTool?.state)
        assertEquals("/home/dev/.local/bin", report.commandLineTool?.folder)
        assertEquals(SmokeStepStatus.SKIPPED, report.status(SmokeStepName.INSTALL_HOOKS))
        assertEquals(HooksInstallState.MISSING, report.hooksState)
        assertTrue(settingsFiles.writtenTexts.isEmpty())
        assertTrue(report.tourToolStepShown)
        assertEquals(TourStep.OPEN_AGENT, report.tourReached)
        assertTrue(report.passed)
    }

    @Test
    fun `on Windows it installs the command script, asks for a restart and installs the hooks`() {
        val report = run(scenario(tool = windowsTool()))

        assertEquals(CommandLineToolState.INSTALLED_NOT_ON_PATH, report.commandLineTool?.state)
        assertEquals(true, report.commandLineTool?.restartNeeded)
        assertEquals(listOf("C:\\Users\\dev\\.shogunai\\bin"), userPath)
        assertEquals(HooksInstallState.INSTALLED, report.hooksState)
        assertTrue(report.passed)
    }

    @Test
    fun `the tour is ended after it reaches its agent step`() {
        run(scenario())

        assertEquals(1, tourCreated)
        assertEquals(1, tourEnded)
        assertNull(tourProject)
    }

    @Test
    fun `the project keeps the local files it was given`() {
        run(scenario())

        assertEquals(listOf(".env.local"), projects.list().single().config.secretFiles)
        assertEquals(repository.path, projects.list().single().config.baseRepositoryPath)
    }

    @Test
    fun `a local file missing from the worktree fails the check naming it`() {
        val report = run(scenario(createWorktree = { _, taskId ->
            val worktree = File(root, "main-repo-worktrees/$taskId").apply { mkdirs() }
            Result.success(Worktree(worktree.path, "feature/$taskId"))
        }))

        assertEquals(SmokeStepStatus.FAILED, report.status(SmokeStepName.CHECK_FILES))
        assertTrue(".env.local wasn't copied" in report.detail(SmokeStepName.CHECK_FILES))
        assertFalse(report.passed)
        assertEquals(1, report.exitStatus)
    }

    @Test
    fun `a copied file that differs from the base fails the check`() {
        val report = run(scenario(createWorktree = { config, taskId ->
            copyingWorktree(config, taskId).onSuccess { File(it.path, "config/local.json").writeText("{}") }
        }))

        assertTrue("config/local.json differs" in report.detail(SmokeStepName.CHECK_FILES))
        assertFalse(report.passed)
    }

    @Test
    fun `a worktree that can't be created fails with its error and skips the check`() {
        val report = run(scenario(createWorktree = { _, _ -> Result.failure(WorktreeError.LocalFileUnreadable("${repository.path}/.env.local", "permission denied")) }))

        assertEquals(SmokeStepStatus.FAILED, report.status(SmokeStepName.CREATE_WORKTREE))
        assertEquals(SmokeStepStatus.SKIPPED, report.status(SmokeStepName.CHECK_FILES))
        assertFalse(report.passed)
    }

    @Test
    fun `without a repository the project step fails and the later steps are skipped`() {
        val report = run(scenario(), repositoryPath = null)

        assertTrue(SmokeTestScenario.REPOSITORY_VARIABLE in report.detail(SmokeStepName.ADD_PROJECT))
        assertEquals(SmokeStepStatus.SKIPPED, report.status(SmokeStepName.CREATE_WORKTREE))
        assertEquals(SmokeStepStatus.SKIPPED, report.status(SmokeStepName.CHECK_FILES))
        assertEquals(1, report.exitStatus)
    }

    @Test
    fun `a folder that isn't a repository fails the project step naming it`() {
        val report = run(scenario(inspect = { Result.failure(WorktreeError.RepositoryNotFound(it)) }), repositoryPath = "/tmp/not-a-repo")

        assertEquals(SmokeStepStatus.FAILED, report.status(SmokeStepName.ADD_PROJECT))
        assertTrue("/tmp/not-a-repo" in report.detail(SmokeStepName.ADD_PROJECT))
        assertTrue(projects.list().isEmpty())
    }
}
