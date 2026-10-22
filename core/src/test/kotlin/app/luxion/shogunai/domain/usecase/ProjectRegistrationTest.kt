package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.failure
import app.luxion.shogunai.domain.FakeShellCommandExecutor.Companion.success
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProjectRegistrationTest {

    private val customised = Project(
        id = "existing",
        name = "Custom name",
        config = ProjectConfig(
            baseRepositoryPath = "/home/dev/projects/main-repo",
            worktreesRoot = "/home/dev/custom-worktrees",
            secretFiles = listOf(".env"),
            mainBranch = "develop",
        ),
    )

    private fun resolver(commonDir: String) =
        ResolveRepositoryUseCase(FakeShellCommandExecutor { success(it, stdout = commonDir) })

    private class RecordingRepository(initial: List<Project>) : ProjectRepository {
        private val delegate = FakeProjectRepository(initial)
        var saves = 0
        override fun list() = delegate.list()
        override fun save(project: Project) {
            saves++
            delegate.save(project)
        }
        override fun delete(id: String) = delegate.delete(id)
    }

    @Test
    fun `registers a repository with the app's defaults`() = runTest {
        val projects = RecordingRepository(emptyList())

        val result = RegisterProjectUseCase(projects, resolver("/home/dev/projects/main-repo/.git"), { "new-id" })("/home/dev/projects/main-repo").getOrThrow()

        val added = assertIs<ProjectRegistration.Added>(result)
        assertEquals("main-repo", added.project.name)
        assertEquals("/home/dev/projects/main-repo-worktrees", added.project.config.worktreesRoot)
        assertEquals(listOf(added.project), projects.list())
    }

    @Test
    fun `registers the main repository when run from a linked worktree`() = runTest {
        val projects = RecordingRepository(emptyList())

        val result = RegisterProjectUseCase(projects, resolver("/home/dev/projects/main-repo/.git"))("/home/dev/projects/main-repo-worktrees/TASK-123").getOrThrow()

        assertEquals("/home/dev/projects/main-repo", result.project.config.baseRepositoryPath)
    }

    @Test
    fun `an already registered repository keeps its settings and is not saved again`() = runTest {
        val projects = RecordingRepository(listOf(customised))

        val result = RegisterProjectUseCase(projects, resolver("/home/dev/projects/main-repo/.git"))("/home/dev/projects/main-repo").getOrThrow()

        assertEquals(ProjectRegistration.AlreadyRegistered(customised), result)
        assertEquals(0, projects.saves)
        assertEquals(listOf(customised), projects.list())
    }

    @Test
    fun `the same repository through a symbolic link is not registered twice`() = runTest {
        val real = Files.createTempDirectory("registration-real").toRealPath()
        val link = Files.createTempDirectory("registration-links").resolve("link")
        Files.createSymbolicLink(link, real)
        val registered = customised.copy(config = customised.config.copy(baseRepositoryPath = real.toString()))
        val projects = RecordingRepository(listOf(registered))

        val result = RegisterProjectUseCase(projects, resolver("$link/.git"))(link.toString()).getOrThrow()

        assertIs<ProjectRegistration.AlreadyRegistered>(result)
        assertEquals(1, projects.list().size)
    }

    @Test
    fun `fails outside a repository without registering anything`() = runTest {
        val projects = RecordingRepository(emptyList())
        val notARepository = ResolveRepositoryUseCase(FakeShellCommandExecutor { failure(it, exitCode = 128) })

        val error = RegisterProjectUseCase(projects, notARepository)("/home/dev").exceptionOrNull()

        assertIs<WorktreeError.RepositoryNotFound>(error)
        assertTrue(projects.list().isEmpty())
    }

    @Test
    fun `unregisters only the project of the repository`() = runTest {
        val other = customised.copy(id = "other", config = customised.config.copy(baseRepositoryPath = "/home/dev/projects/other-repo"))
        val projects = RecordingRepository(listOf(customised, other))

        val removed = UnregisterProjectUseCase(projects, resolver("/home/dev/projects/main-repo/.git"))("/home/dev/projects/main-repo").getOrThrow()

        assertEquals(customised, removed)
        assertEquals(listOf(other), projects.list())
    }

    @Test
    fun `unregisters a project whose folder is gone by its stored path`() = runTest {
        val projects = RecordingRepository(listOf(customised))
        val notARepository = ResolveRepositoryUseCase(FakeShellCommandExecutor { failure(it, exitCode = 128) })

        val removed = UnregisterProjectUseCase(projects, notARepository)("/home/dev/projects/main-repo").getOrThrow()

        assertEquals(customised, removed)
        assertTrue(projects.list().isEmpty())
    }

    @Test
    fun `fails when the repository has no project`() = runTest {
        val projects = RecordingRepository(listOf(customised))

        val error = UnregisterProjectUseCase(projects, resolver("/home/dev/projects/other-repo/.git"))("/home/dev/projects/other-repo").exceptionOrNull()

        assertIs<WorktreeError.ProjectNotRegistered>(error)
        assertEquals(listOf(customised), projects.list())
    }
}
