package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeMultirepoSource
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.model.CheckState
import app.luxion.shogunai.domain.model.InstallationCheck
import app.luxion.shogunai.domain.model.InstallationFinding
import app.luxion.shogunai.domain.model.MultirepoProblem
import app.luxion.shogunai.domain.model.Project
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CheckMultireposUseCaseTest {

    private val world = MultirepoTestWorld()

    private suspend fun check(
        source: FakeMultirepoSource = world.source,
        fileManager: FakeFileManager = world.fileManager,
        projects: ProjectRepository = world.projects,
    ): List<InstallationCheck> =
        CheckMultireposUseCase(source, ResolveMultirepoUseCase(source, world.git, fileManager), DetectGroupsUseCase(world.git), projects)()

    private fun List<InstallationCheck>.finding(): InstallationFinding.MultirepoCheck = single().finding as InstallationFinding.MultirepoCheck

    @Test
    fun `no known multirepo means no checks`() = runTest {
        assertTrue(check(source = FakeMultirepoSource()).isEmpty())
    }

    @Test
    fun `a healthy multirepo passes, naming its repositories and groups`() = runTest {
        with(world.git) {
            world.app.worktree("${world.worktreesRoot}/app/TASK-123", "feature/TASK-123")
            world.api.worktree("${world.worktreesRoot}/api/TASK-123", "feature/TASK-123")
        }

        val checks = check()

        assertEquals(CheckState.PASSED, checks.single().state)
        assertEquals(
            InstallationFinding.MultirepoCheck(world.folder, "product", listOf("app", "api", "docs"), 1, emptyList()),
            checks.finding(),
        )
        assertEquals("multirepo:product", checks.single().finding.id)
    }

    @Test
    fun `every repository that isn't a Git repository fails the multirepo`() = runTest {
        val fileManager = FakeFileManager(existing = listOf(world.folder, world.apiBase))

        val checks = check(fileManager = fileManager)

        assertEquals(CheckState.FAILED, checks.single().state)
        assertEquals(
            listOf(MultirepoProblem.RepositoryMissing("app", world.appBase), MultirepoProblem.RepositoryMissing("docs", world.docsBase)),
            checks.finding().problems,
        )
    }

    @Test
    fun `an invalid declaration fails, naming the file and the problem`() = runTest {
        world.source.declarations[world.folder] = world.declaration.copy(defaultRepos = listOf("web"))

        val checks = check()

        assertEquals(CheckState.FAILED, checks.single().state)
        assertEquals(
            MultirepoProblem.InvalidDeclaration("${world.folder}/.shogunai-multirepo.json", "'web' isn't a declared repository"),
            checks.finding().problems.single(),
        )
    }

    @Test
    fun `a declaration that can't be read fails with the reason`() = runTest {
        world.source.unreadable[world.folder] = IOException("Unexpected JSON token")

        val checks = check()

        assertEquals(CheckState.FAILED, checks.single().state)
        assertEquals(
            InstallationFinding.MultirepoCheck(
                world.folder,
                "product",
                emptyList(),
                0,
                listOf(MultirepoProblem.InvalidDeclaration("${world.folder}/.shogunai-multirepo.json", "Unexpected JSON token")),
            ),
            checks.finding(),
        )
    }

    @Test
    fun `a gone declaration warns and the multirepo isn't forgotten`() = runTest {
        val source = FakeMultirepoSource(known = listOf(world.folder))
        source.missingFolders += world.folder

        val checks = check(source = source)

        assertEquals(CheckState.WARNING, checks.single().state)
        assertEquals(listOf(MultirepoProblem.DeclarationMissing), checks.finding().problems)
        assertEquals(listOf(world.folder), source.knownFolders())
    }

    @Test
    fun `an untrusted multirepo warns`() = runTest {
        world.source.approveAll = false

        val checks = check()

        assertEquals(CheckState.WARNING, checks.single().state)
        assertEquals(listOf(MultirepoProblem.NotTrusted), checks.finding().problems)
    }

    @Test
    fun `a group worktree whose folder is gone warns, naming the group, repository and folder`() = runTest {
        val missing = "${world.worktreesRoot}/app/TASK-123"
        with(world.git) {
            world.app.worktree(missing, "feature/TASK-123")
            world.api.worktree("${world.worktreesRoot}/api/TASK-123", "feature/TASK-123")
        }
        world.app.prunable += missing

        val checks = check()

        assertEquals(CheckState.WARNING, checks.single().state)
        assertEquals(listOf(MultirepoProblem.GroupWorktreeMissing("feature/TASK-123", "app", missing)), checks.finding().problems)
    }

    @Test
    fun `a repository that can't be listed warns and the others' groups are still checked`() = runTest {
        with(world.git) {
            world.app.worktree("${world.worktreesRoot}/app/TASK-123", "feature/TASK-123")
            world.api.worktree("${world.worktreesRoot}/api/TASK-123", "feature/TASK-123")
        }
        world.docs.failList = true

        val checks = check()

        assertEquals(CheckState.WARNING, checks.single().state)
        val problem = checks.finding().problems.single() as MultirepoProblem.ListingFailed
        assertEquals("docs", problem.alias)
        assertEquals(1, checks.finding().groups)
    }

    @Test
    fun `projects that can't be listed don't stop the check`() = runTest {
        val broken = object : ProjectRepository by world.projects {
            override fun list(): List<Project> = throw IOException("unreadable")
        }

        val checks = check(projects = broken)

        assertEquals(CheckState.PASSED, checks.single().state)
    }
}
