package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeGradleDaemonSource
import app.luxion.shogunai.domain.FakeGradleDaemonSource.Companion.gradleDaemon
import app.luxion.shogunai.domain.FakeGradleDaemonSource.Companion.kotlinDaemon
import app.luxion.shogunai.domain.model.DaemonOwner
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.GradleDaemon
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ListGradleDaemonsUseCaseTest {

    private val idle = DaemonState.Idle(Instant.parse("2026-10-16T10:57:00Z"))
    private val main = Worktree(path = "/home/dev/projects/main-repo", branch = "develop", isMain = true)
    private val nested = Worktree(path = "/home/dev/projects/main-repo/.claude/worktrees/task", branch = "worktree-task")
    private val sibling = Worktree(path = "/home/dev/projects/TASK-123", branch = "feature/TASK-123")
    private val worktrees = listOf(main, nested, sibling)

    private val existing = listOf(main.path, nested.path, sibling.path, "/home/dev/other-project")

    private fun useCase(source: FakeGradleDaemonSource) = ListGradleDaemonsUseCase(source, FakeFileManager(existing))

    @Test
    fun `attributes each gradle daemon to the worktree of its last build`() = runTest {
        val source = FakeGradleDaemonSource(
            listOf(
                gradleDaemon(1, idle, lastBuildDirectory = sibling.path),
                gradleDaemon(2, DaemonState.Busy, lastBuildDirectory = nested.path),
                gradleDaemon(3, idle, lastBuildDirectory = "/home/dev/projects/TASK-9"),
                gradleDaemon(4, idle, lastBuildDirectory = "/home/dev/other-project"),
                gradleDaemon(5, DaemonState.Unknown),
                kotlinDaemon(6),
            ),
        )

        val owners = useCase(source)(worktrees).getOrThrow().map { it.owner }

        assertEquals(
            listOf(
                DaemonOwner.Worktree(sibling.path),
                DaemonOwner.Worktree(nested.path),
                DaemonOwner.RemovedWorktree,
                DaemonOwner.OtherProject,
                null,
                null,
            ),
            owners,
        )
    }

    @Test
    fun `leaves daemons unattributed outside a repository`() = runTest {
        val source = FakeGradleDaemonSource(listOf(gradleDaemon(1, idle, lastBuildDirectory = sibling.path)))

        assertNull(useCase(source)(null).getOrThrow().single().owner)
    }

    @Test
    fun `fails when processes can't be listed`() = runTest {
        val source = FakeGradleDaemonSource(failure = WorktreeError.ProcessListingFailed(listOf("ps"), 1))

        assertIs<WorktreeError.ProcessListingFailed>(useCase(source)(worktrees).exceptionOrNull())
    }

    @Test
    fun `selects the idle gradle daemons of removed worktrees`() {
        val daemons = listOf(
            GradleDaemon(gradleDaemon(1, idle, lastBuildDirectory = sibling.path), null),
            GradleDaemon(gradleDaemon(2, DaemonState.Busy, lastBuildDirectory = sibling.path), null),
            GradleDaemon(gradleDaemon(3, idle, lastBuildDirectory = nested.path), null),
            GradleDaemon(gradleDaemon(4, idle, lastBuildDirectory = main.path), null),
            GradleDaemon(kotlinDaemon(5), null),
        )

        val pids = idleDaemonsOfRemovedWorktrees(daemons, worktrees, listOf(sibling.path, nested.path))

        assertEquals(listOf(1L, 3L), pids)
    }
}
