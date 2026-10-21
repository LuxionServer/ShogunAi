package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeComposeProjectSource
import app.luxion.shogunai.domain.FakeShellCommandExecutor
import app.luxion.shogunai.domain.model.ComposeOwner
import app.luxion.shogunai.domain.model.ComposeProject
import app.luxion.shogunai.domain.model.MainBranch
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComposeProjectUseCasesTest {

    private val repository = "/home/dev/projects/main-repo"
    private val worktreesRoot = "/home/dev/projects/worktrees"
    private val worktrees = listOf(
        Worktree(repository, "develop", isMain = true),
        Worktree("$worktreesRoot/TASK-123", "feature/TASK-123"),
    )

    private fun project(name: String, file: String, status: String = "running(2)") = ComposeProject(name, status, listOf(file))

    private suspend fun list(source: FakeComposeProjectSource) = ListComposeProjectsUseCase(source)(repository, worktrees, worktreesRoot)

    @Test
    fun `a project named like a worktree belongs to it`() = runTest {
        val source = FakeComposeProjectSource(listOf(project("main-repo-task-123", "$worktreesRoot/TASK-123/compose.yaml")))

        val listed = list(source).getOrThrow().single()

        assertEquals(ComposeOwner.Worktree("$worktreesRoot/TASK-123"), listed.owner)
        assertEquals("running(2)", listed.project.status)
    }

    @Test
    fun `a project of a removed worktree is recognised by its compose files`() = runTest {
        val source = FakeComposeProjectSource(listOf(project("main-repo-task-9", "$worktreesRoot/TASK-9/compose.yaml", "exited(1)")))

        assertEquals(ComposeOwner.RemovedWorktree, list(source).getOrThrow().single().owner)
    }

    @Test
    fun `an unrelated project is not listed`() = runTest {
        val source = FakeComposeProjectSource(
            listOf(
                project("other-app", "/home/dev/other/compose.yaml"),
                project("main-repo-task-9", "/home/dev/elsewhere/compose.yaml"),
                project("main-repo-extra-task-1", "/home/dev/projects/main-repo-extra/worktrees/TASK-1/compose.yaml"),
            ),
        )

        assertTrue(list(source).getOrThrow().isEmpty())
    }

    @Test
    fun `a sibling repository sharing the prefix is never an orphan`() = runTest {
        val source = FakeComposeProjectSource(listOf(project("main-repo2-task-1", "$worktreesRoot/TASK-1/compose.yaml")))

        assertTrue(list(source).getOrThrow().isEmpty())
    }

    @Test
    fun `an unavailable docker is a failure with the reason`() = runTest {
        val result = list(FakeComposeProjectSource(unavailable = "daemon not reachable"))

        assertEquals("Docker isn't available: daemon not reachable", result.exceptionOrNull()?.message)
    }

    @Test
    fun `takes projects down keeping volumes unless asked`() = runTest {
        val source = FakeComposeProjectSource()

        val kept = TakeDownComposeProjectsUseCase(source)(listOf("main-repo-task-9"))
        val removed = TakeDownComposeProjectsUseCase(source)(listOf("main-repo-task-9"), removeVolumes = true)

        assertEquals(listOf("main-repo-task-9"), kept.takenDown)
        assertEquals(listOf("main-repo-task-9" to false, "main-repo-task-9" to true), source.downs)
        assertTrue(removed.failures.isEmpty())
    }

    @Test
    fun `a failing project is reported and the rest are still processed`() = runTest {
        val source = FakeComposeProjectSource(failingDowns = mapOf("a" to "network in use"))

        val report = TakeDownComposeProjectsUseCase(source)(listOf("a", "b"))

        assertEquals(listOf("b"), report.takenDown)
        assertEquals("network in use", report.failures.single().detail)
    }

    private val config = ProjectConfig(baseRepositoryPath = repository, worktreesRoot = worktreesRoot, secretFiles = emptyList())
    private val mainBranch = MainBranch("develop", "origin/develop")
    private val removed = Worktree("$worktreesRoot/TASK-9", "feature/TASK-9")

    @Test
    fun `cleanup takes down the projects of the removed worktrees`() = runTest {
        val source = FakeComposeProjectSource(
            listOf(project("main-repo-task-9", "$worktreesRoot/TASK-9/compose.yaml"), project("main-repo-task-123", "x")),
        )

        val report = CleanUpWorktreesUseCase(config, FakeShellCommandExecutor(), composeProjects = source)(
            listOf(removed),
            mainBranch,
            deleteBranches = false,
            downComposeProjects = true,
        ).getOrThrow()

        assertEquals(listOf("main-repo-task-9"), report.composeDown?.takenDown)
        assertEquals(listOf("main-repo-task-9" to false), source.downs)
    }

    @Test
    fun `cleanup without the option never touches docker`() = runTest {
        val source = FakeComposeProjectSource(listOf(project("main-repo-task-9", "x")))

        val report = CleanUpWorktreesUseCase(config, FakeShellCommandExecutor(), composeProjects = source)(
            listOf(removed),
            mainBranch,
            deleteBranches = false,
        ).getOrThrow()

        assertNull(report.composeDown)
        assertTrue(source.listings.isEmpty() && source.downs.isEmpty())
    }

    @Test
    fun `cleanup still succeeds when docker is unavailable and says the projects were kept`() = runTest {
        val source = FakeComposeProjectSource(unavailable = "daemon not reachable")

        val report = CleanUpWorktreesUseCase(config, FakeShellCommandExecutor(), composeProjects = source)(
            listOf(removed),
            mainBranch,
            deleteBranches = false,
            downComposeProjects = true,
        ).getOrThrow()

        assertEquals(listOf(removed.path), report.removed)
        assertEquals("daemon not reachable", report.composeUnavailable)
        assertNull(report.composeDown)
    }
}
