package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.model.Worktree
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeasureWorktreeSizeUseCaseTest {

    private val main = Worktree(path = "/home/dev/projects/main-repo", branch = "develop", isMain = true)
    private val nested = Worktree(path = "/home/dev/projects/main-repo/.claude/worktrees/task", branch = "worktree-task")
    private val sibling = Worktree(path = "/home/dev/projects/main-repo-2", branch = "feature/TASK-2")
    private val all = listOf(main, nested, sibling)

    @Test
    fun `nested worktrees are excluded from the size`() = runTest {
        val fileManager = FakeFileManager(existing = all.map { it.path }, sizes = mapOf(main.path to 1234L))

        val size = MeasureWorktreeSizeUseCase(fileManager, StandardTestDispatcher(testScheduler))(main, all).getOrThrow()

        assertEquals(1234L, size)
        assertEquals(main.path to setOf(nested.path), fileManager.sizeRequests.single())
    }

    @Test
    fun `a worktree without nested worktrees excludes nothing`() = runTest {
        val fileManager = FakeFileManager(existing = all.map { it.path }, sizes = mapOf(sibling.path to 10L))

        MeasureWorktreeSizeUseCase(fileManager, StandardTestDispatcher(testScheduler))(sibling, all).getOrThrow()

        assertTrue(fileManager.sizeRequests.single().second.isEmpty())
    }

    @Test
    fun `a missing folder has no size`() = runTest {
        val fileManager = FakeFileManager(existing = emptyList())

        assertNull(MeasureWorktreeSizeUseCase(fileManager, StandardTestDispatcher(testScheduler))(sibling, all).getOrThrow())
        assertTrue(fileManager.sizeRequests.isEmpty())
    }
    @Test
    fun `a prunable worktree has no size`() = runTest {
        val fileManager = FakeFileManager(existing = all.map { it.path })

        assertEquals(null, MeasureWorktreeSizeUseCase(fileManager, StandardTestDispatcher(testScheduler))(sibling.copy(isPrunable = true), all).getOrThrow())
    }
}
