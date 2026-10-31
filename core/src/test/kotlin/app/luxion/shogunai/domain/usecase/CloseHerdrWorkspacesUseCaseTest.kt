package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeHerdrClient
import app.luxion.shogunai.domain.io.HerdrClient
import app.luxion.shogunai.domain.model.HerdrPane
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CloseHerdrWorkspacesUseCaseTest {

    private val config = ProjectConfig(
        baseRepositoryPath = "/home/dev/projects/main-repo",
        worktreesRoot = "/home/dev/projects",
        secretFiles = emptyList(),
    )
    private val first = "/home/dev/projects/TASK-1"
    private val second = "/home/dev/projects/TASK-2"

    @Test
    fun `a worktree open in Herdr is found by its path`() = runTest {
        val herdr = FakeHerdrClient(openWorkspaces = mapOf(first to "w2", "/home/dev/projects/other" to "w3"))

        val workspaces = CloseHerdrWorkspacesUseCase(config, herdr).openWorkspaces(listOf(first, second))

        assertEquals(mapOf(first to "w2"), workspaces)
        assertEquals(1, herdr.listings)
    }

    @Test
    fun `a worktree that is not open has no workspace`() = runTest {
        val herdr = FakeHerdrClient(openWorkspaces = mapOf(first to "w2"))

        assertTrue(CloseHerdrWorkspacesUseCase(config, herdr).openWorkspaces(listOf(second)).isEmpty())
    }

    @Test
    fun `paths differing in trailing slash or backslashes still match`() = runTest {
        val herdr = FakeHerdrClient(openWorkspaces = mapOf("$first/" to "w2", "C:\\projects\\TASK-2" to "w3"))
        val useCase = CloseHerdrWorkspacesUseCase(config, herdr)

        val workspaces = useCase.openWorkspaces(listOf(first, "C:/projects/TASK-2"))
        useCase.close(workspaces, listOf(first, "C:/projects/TASK-2"))

        assertEquals(listOf("w2", "w3"), herdr.closedWorkspaces)
    }

    @Test
    fun `Herdr not installed or not running means nothing to close`() = runTest {
        listOf<Throwable>(
            WorktreeError.HerdrNotInstalled,
            WorktreeError.HerdrCommandFailed(listOf("herdr"), "no herdr server is running"),
        ).forEach { failure ->
            val herdr = FakeHerdrClient(listFailure = failure)

            assertTrue(CloseHerdrWorkspacesUseCase(config, herdr).openWorkspaces(listOf(first)).isEmpty())
        }
    }

    @Test
    fun `without paths Herdr isn't asked`() = runTest {
        val herdr = FakeHerdrClient()

        CloseHerdrWorkspacesUseCase(config, herdr).openWorkspaces(emptyList())

        assertEquals(0, herdr.listings)
    }

    @Test
    fun `only the removed paths are closed`() = runTest {
        val herdr = FakeHerdrClient()

        val failures = CloseHerdrWorkspacesUseCase(config, herdr).close(mapOf(first to "w2", second to "w3"), listOf(second))

        assertTrue(failures.isEmpty())
        assertEquals(listOf("w3"), herdr.closedWorkspaces)
    }

    @Test
    fun `a failing close is collected and the others still close`() = runTest {
        val failure = WorktreeError.HerdrCommandFailed(listOf("herdr"), "workspace w2 not found")
        val herdr = FakeHerdrClient(closeFailures = mapOf("w2" to failure))

        val failures = CloseHerdrWorkspacesUseCase(config, herdr).close(mapOf(first to "w2", second to "w3"), listOf(first, second))

        assertEquals(listOf(first), failures.map { it.path })
        assertEquals("workspace w2 not found", assertIs<WorktreeError.HerdrCommandFailed>(failures.single().error).detail)
        assertEquals(listOf("w3"), herdr.closedWorkspaces)
    }

    @Test
    fun `panes that can't be listed leave the holders as they are`() = runTest {
        val closing = CloseHerdrWorkspacesUseCase(config, FakeHerdrClient(failure = IllegalStateException("herdr is down"))).closeHolders(first, null)

        assertEquals(HolderClosing(), closing)
    }

    @Test
    fun `a pane that can't be closed is reported as a Herdr failure`() = runTest {
        val herdr = object : HerdrClient by FakeHerdrClient(panes = listOf(HerdrPane("p1", "w9", "$first/app"))) {
            override suspend fun closePane(paneId: String) = throw IllegalStateException("pane is gone")
        }

        val failure = CloseHerdrWorkspacesUseCase(config, herdr).closeHolders(first, null).failure

        assertIs<WorktreeError.HerdrCommandFailed>(failure)
        assertEquals("pane is gone", failure.herdrDetail())
        assertEquals("No multirepo found at /x", WorktreeError.MultirepoNotFound("/x").herdrDetail())
    }
    @Test
    fun `the first failure closing holders is kept and generic errors become Herdr failures`() = runTest {
        val workspaceFailure = WorktreeError.HerdrCommandFailed(listOf("workspace"), "busy")
        val paneFailure = WorktreeError.HerdrCommandFailed(listOf("pane"), "gone")
        fun herdr(closePaneError: (String) -> Throwable, closeFailures: Map<String, Throwable> = emptyMap()) =
            object : HerdrClient by FakeHerdrClient(
                panes = listOf(HerdrPane("p1", "w9", "$first/app"), HerdrPane("p2", "w9", first), HerdrPane("p3", "w9", null)),
                closeFailures = closeFailures,
            ) {
                override suspend fun closePane(paneId: String) = throw closePaneError(paneId)
            }

        val keptWorkspaceFailure = CloseHerdrWorkspacesUseCase(config, herdr({ paneFailure }, mapOf("w2" to workspaceFailure))).closeHolders(first, "w2")
        val firstPaneFailure = CloseHerdrWorkspacesUseCase(config, herdr({ if (it == "p1") paneFailure else IllegalStateException() })).closeHolders(first, null)
        val genericFailure = CloseHerdrWorkspacesUseCase(config, herdr({ IllegalStateException() })).closeHolders(first, null)

        assertEquals(HolderClosing(closedWorkspace = false, failure = workspaceFailure), keptWorkspaceFailure)
        assertEquals(paneFailure, firstPaneFailure.failure)
        assertEquals("", genericFailure.failure?.herdrDetail())
    }

    @Test
    fun `a workspace that fails to close with a generic error is reported without detail`() = runTest {
        val herdr = FakeHerdrClient(closeFailures = mapOf("w2" to IllegalStateException()))

        val failures = CloseHerdrWorkspacesUseCase(config, herdr).close(mapOf(first to "w2"), listOf(first, second))

        assertEquals(listOf(first), failures.map { it.path })
        assertEquals("", failures.single().error.herdrDetail())
        val withMessage = FakeHerdrClient(closeFailures = mapOf("w2" to IllegalStateException("socket closed")))
        assertEquals("socket closed", CloseHerdrWorkspacesUseCase(config, withMessage).close(mapOf(first to "w2"), listOf(first)).single().error.herdrDetail())
    }
}
