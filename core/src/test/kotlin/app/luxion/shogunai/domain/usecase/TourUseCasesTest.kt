package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.executor.CommandExecutionException
import app.luxion.shogunai.domain.model.AppLanguage
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.newProject
import app.luxion.shogunai.infrastructure.NioFileManager
import app.luxion.shogunai.infrastructure.ProcessBuilderShellCommandExecutor
import app.luxion.shogunai.infrastructure.RepositoryProvisioningFiles
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.io.path.exists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TourUseCasesTest {

    private val home: Path = Files.createTempDirectory("tour-home")
    private val location = TourLocation(home.toString())
    private val fileManager = NioFileManager()
    private val executor = ProcessBuilderShellCommandExecutor(path = null)

    @AfterTest
    fun cleanUp() {
        home.toFile().deleteRecursively()
    }

    @Test
    fun `crea el repositorio de ejemplo con un worktree en cada estado`() = runTest {
        val projects = FakeProjectRepository()

        val project = CreateTourUseCase(executor, fileManager, projects, location)(AppLanguage.ENGLISH).getOrThrow()

        assertEquals(listOf(project), projects.list())
        assertEquals(TOUR_PROJECT_NAME, project.name)
        assertEquals("main", project.config.mainBranch)
        assertTrue(location.isTour(project))
        val activities = activitiesByName(project)
        assertEquals(Activity.Idle, activities["sample-repo"])
        assertEquals(Activity.New, activities["new-idea"])
        assertEquals(Activity.Unfinished, activities["unfinished"])
        assertEquals(Activity.Stopped, activities["pushed-work"])
        assertEquals(Activity.Merged, activities["merged-fix"])
        assertEquals(Activity.Inactive, activities["old-experiment"])
        assertEquals(Activity.New, activities["locked-task"])
        assertEquals(Activity.Missing, activities["folder-gone"])
    }

    @Test
    fun `el trabajo empujado queda por detrás de main y el worktree bloqueado lo indica`() = runTest {
        val project = CreateTourUseCase(executor, fileManager, FakeProjectRepository(), location)(AppLanguage.ENGLISH).getOrThrow()

        val worktrees = ListWorktreesUseCase(project.config, executor, fileManager)().getOrThrow()
        val mainBranch = ResolveMainBranchUseCase(project.config, executor)().getOrThrow()
        val pushed = worktrees.single { it.path.endsWith("pushed-work") }
        val status = ReadWorktreeStatusUseCase(executor, fileManager)(pushed, mainBranch).getOrThrow()

        assertTrue((status.mainBranch?.behind ?: 0) > 0)
        assertTrue(worktrees.single { it.path.endsWith("locked-task") }.isLocked)
    }

    @Test
    fun `declara la configuración del repositorio y el guion para los agentes`() = runTest {
        CreateTourUseCase(executor, fileManager, FakeProjectRepository(), location)(AppLanguage.SPANISH).getOrThrow()

        val provisioning = RepositoryProvisioningFiles().read(location.repository)
        assertEquals(listOf(".env.local"), provisioning.copyPatterns)
        assertEquals(listOf(listOf("git", "status", "--short")), provisioning.postCreateCommands)
        assertTrue(fileManager.readText("${location.repository}/AGENTS.md").orEmpty().contains("Spanish"))
        assertEquals("@AGENTS.md\n", fileManager.readText("${location.repository}/CLAUDE.md"))
        assertTrue(fileManager.exists("${location.repository}/.env.local"))
    }

    @Test
    fun `los agentes del recorrido arrancan con el prompt y sin Headroom`() = runTest {
        val project = CreateTourUseCase(executor, fileManager, FakeProjectRepository(), location)(AppLanguage.ENGLISH).getOrThrow()

        assertEquals("claude \"Start the ShogunAi tour\"", project.config.agentLaunchConfig.resolvedCommand())
        assertEquals(listOf("Codex"), project.config.additionalAgents.map { it.displayName })
        assertEquals("claude", project.config.agentLaunchConfig.executable())
    }

    @Test
    fun `un fallo de Git no deja carpeta ni proyecto`() = runTest {
        val projects = FakeProjectRepository()
        val failing = FakeShellCommandExecutor { command ->
            if ("worktree" in command) FakeShellCommandExecutor.failure(command, stderr = "fatal: broken") else FakeShellCommandExecutor.success(command)
        }

        val error = CreateTourUseCase(failing, fileManager, projects, location)(AppLanguage.ENGLISH).exceptionOrNull()

        assertIs<WorktreeError.TourCreationFailed>(error)
        assertEquals("fatal: broken", error.detail)
        assertFalse(Path.of(location.root).exists())
        assertTrue(projects.list().isEmpty())
    }

    @Test
    fun `un comando de Git que no arranca no deja carpeta ni proyecto`() = runTest {
        val projects = FakeProjectRepository()
        val failing = FakeShellCommandExecutor { command ->
            if ("worktree" in command) throw CommandExecutionException(command, java.io.IOException("git not found")) else FakeShellCommandExecutor.success(command)
        }

        val error = CreateTourUseCase(failing, fileManager, projects, location)(AppLanguage.ENGLISH).exceptionOrNull()

        assertIs<WorktreeError.TourCreationFailed>(error)
        assertEquals("git not found", error.detail)
        assertFalse(Path.of(location.root).exists())
    }

    @Test
    fun `no sobrescribe una carpeta del recorrido que ya existe`() = runTest {
        Files.createDirectories(Path.of(location.root))

        val error = CreateTourUseCase(executor, fileManager, FakeProjectRepository(), location)(AppLanguage.ENGLISH).exceptionOrNull()

        assertIs<WorktreeError.TourAlreadyExists>(error)
    }

    @Test
    fun `terminar el recorrido borra su proyecto y su carpeta sin tocar otros proyectos`() = runTest {
        val other = newProject("other", "/projects/main-repo")
        val projects = FakeProjectRepository(listOf(other))
        CreateTourUseCase(executor, fileManager, projects, location)(AppLanguage.ENGLISH).getOrThrow()

        EndTourUseCase(fileManager, projects, location)().getOrThrow()

        assertEquals(listOf(other), projects.list())
        assertFalse(Path.of(location.root).exists())
    }

    @Test
    fun `terminar el recorrido con la carpeta ya borrada no falla`() = runTest {
        val projects = FakeProjectRepository()
        CreateTourUseCase(executor, fileManager, projects, location)(AppLanguage.ENGLISH).getOrThrow()
        home.resolve(".shogunai").toFile().deleteRecursively()

        EndTourUseCase(fileManager, projects, location)().getOrThrow()

        assertTrue(projects.list().isEmpty())
    }

    private suspend fun activitiesByName(project: Project): Map<String, Activity> {
        val worktrees = ListWorktreesUseCase(project.config, executor, fileManager)().getOrThrow()
        val mainBranch = ResolveMainBranchUseCase(project.config, executor)().getOrThrow()
        val readStatus = ReadWorktreeStatusUseCase(executor, fileManager)
        return worktrees.associate { worktree ->
            val status = if (worktree.isPrunable) null else readStatus(worktree, mainBranch).getOrThrow()
            worktree.path.substringAfterLast('/') to worktreeActivity(worktree, status, emptyList(), Instant.now(), Duration.ofDays(90))
        }
    }

    @Test
    fun `a git failure without an error message reports its output or the command`() = runTest {
        val quiet = FakeShellCommandExecutor { command ->
            if ("worktree" in command) FakeShellCommandExecutor.failure(command, stderr = " ").copy(stdout = "fatal on stdout") else FakeShellCommandExecutor.success(command)
        }
        val silent = FakeShellCommandExecutor { command ->
            if ("worktree" in command) throw CommandExecutionException(command, java.io.IOException()) else FakeShellCommandExecutor.success(command)
        }

        val quietError = assertIs<WorktreeError.TourCreationFailed>(CreateTourUseCase(quiet, fileManager, FakeProjectRepository(), location)(AppLanguage.ENGLISH).exceptionOrNull())
        val silentError = assertIs<WorktreeError.TourCreationFailed>(CreateTourUseCase(silent, fileManager, FakeProjectRepository(), location)(AppLanguage.ENGLISH).exceptionOrNull())

        assertEquals("fatal on stdout", quietError.detail)
        assertTrue(silentError.detail.startsWith("Could not start the command: git"))
    }
}

class TourScriptTest {

    private val location = TourLocation("/home/user")

    @Test
    fun `el guion recorre cada paso en orden`() {
        val script = TourScript.agentInstructions(AppLanguage.ENGLISH, location)

        val positions = TourScript.steps.map { script.indexOf(it) }
        assertTrue(positions.all { it >= 0 })
        assertEquals(positions.sorted(), positions)
        assertTrue(script.contains("Speak to the user in English."))
    }

    @Test
    fun `en español pide hablar en español`() {
        assertTrue(TourScript.agentInstructions(AppLanguage.SPANISH, location).contains("Speak to the user in Spanish"))
    }

    @Test
    fun `limita al agente a la carpeta del recorrido y explica qué hacer sin la CLI`() {
        val script = TourScript.agentInstructions(AppLanguage.ENGLISH, location)

        assertTrue(script.contains("Change nothing outside `/home/user/.shogunai/tour`"))
        assertTrue(script.contains("shogun --version"))
    }
}

class TourLocationTest {

    @Test
    fun `reconoce el proyecto del recorrido por su carpeta`() {
        val location = TourLocation("/home/user")

        assertTrue(location.isTour(newProject("tour", "/home/user/.shogunai/tour/sample-repo")))
        assertFalse(location.isTour(newProject("other", "/projects/main-repo")))
        assertEquals("/home/user/.shogunai/tour/worktrees/new-idea", location.worktree("new-idea"))
    }
}
