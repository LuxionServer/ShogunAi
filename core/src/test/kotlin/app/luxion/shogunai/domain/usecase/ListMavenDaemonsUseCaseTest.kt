package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeMavenDaemonSource
import app.luxion.shogunai.domain.model.DaemonOwner
import app.luxion.shogunai.domain.model.MavenDaemonProcess
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListMavenDaemonsUseCaseTest {

    private val worktrees = listOf(
        Worktree("/home/dev/projects/main-repo", "develop", isMain = true),
        Worktree("/home/dev/projects/worktrees/task-123", "feature/task-123"),
    )
    private val worktreesRoot = "/home/dev/projects/worktrees"

    private suspend fun ownerOf(directory: String?, existing: Boolean, root: String? = worktreesRoot): DaemonOwner? {
        val source = FakeMavenDaemonSource(listOf(MavenDaemonProcess(1, 100, directory)))
        val fileManager = FakeFileManager(existing = if (existing && directory != null) listOf(directory) else emptyList())
        return ListMavenDaemonsUseCase(source, fileManager)(worktrees, root).getOrThrow().single().owner
    }

    @Test
    fun `a daemon in a worktree belongs to it`() = runTest {
        assertEquals(
            DaemonOwner.Worktree("/home/dev/projects/worktrees/task-123"),
            ownerOf("/home/dev/projects/worktrees/task-123/service", existing = true),
        )
    }

    @Test
    fun `a daemon in a removed worktree folder belongs to a removed worktree`() = runTest {
        assertEquals(DaemonOwner.RemovedWorktree, ownerOf("/home/dev/projects/worktrees/task-9/service", existing = false))
    }

    @Test
    fun `a daemon elsewhere belongs to another project`() = runTest {
        assertEquals(DaemonOwner.OtherProject, ownerOf("/home/dev/other", existing = true))
    }

    @Test
    fun `a daemon whose directory is unreadable is unknown`() = runTest {
        assertNull(ownerOf(null, existing = false))
    }

    @Test
    fun `daemons without worktrees are listed without owner`() = runTest {
        val source = FakeMavenDaemonSource(listOf(MavenDaemonProcess(1, 100, "/x"), MavenDaemonProcess(2, 300, "/y")))

        val daemons = ListMavenDaemonsUseCase(source, FakeFileManager())(null).getOrThrow()

        assertTrue(daemons.all { it.owner == null })
        assertEquals(400L, totalMavenResidentMemory(daemons))
    }

    @Test
    fun `an unavailable process listing is a failure`() = runTest {
        val source = FakeMavenDaemonSource(failure = WorktreeError.ProcessListingFailed(listOf("ps"), 1))

        assertTrue(ListMavenDaemonsUseCase(source, FakeFileManager())(worktrees).isFailure)
    }
}
