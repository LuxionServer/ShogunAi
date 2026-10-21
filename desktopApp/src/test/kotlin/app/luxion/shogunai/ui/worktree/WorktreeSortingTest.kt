package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeSort
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class WorktreeSortingTest {

    private val now = Instant.parse("2026-10-16T12:00:00Z")

    private fun worktree(branch: String, isMain: Boolean = false) =
        Worktree(path = "/home/dev/projects/${branch.substringAfterLast('/')}", branch = branch, isMain = isMain)

    private fun sort(sort: WorktreeSort, dates: Map<String, Instant?>, vararg worktrees: Worktree) =
        sortWorktreeItems(worktrees.toList(), sort, worktreeOf = { it }, activityDateOf = { dates[it.branch] }).map { it.branch }

    @Test
    fun `names are ordered naturally ignoring case`() {
        val ordered = sort(
            WorktreeSort.NAME,
            emptyMap(),
            worktree("feature/TASK-10"),
            worktree("feature/TASK-9"),
            worktree("feature/task-11"),
        )

        assertEquals(listOf("feature/TASK-9", "feature/TASK-10", "feature/task-11"), ordered)
    }

    @Test
    fun `the main worktree comes first in both orders`() {
        val main = worktree("main", isMain = true)
        val other = worktree("alpha")
        val dates = mapOf<String, Instant?>("main" to now.minusSeconds(3600), "alpha" to now)

        assertEquals(listOf("main", "alpha"), sort(WorktreeSort.NAME, dates, other, main))
        assertEquals(listOf("main", "alpha"), sort(WorktreeSort.ACTIVITY, dates, other, main))
    }

    @Test
    fun `last activity puts the most recent first and the undated last`() {
        val dates = mapOf<String, Instant?>(
            "TASK-1" to now.minusSeconds(3600),
            "TASK-2" to now.minusSeconds(600),
            "TASK-3" to null,
        )

        val ordered = sort(WorktreeSort.ACTIVITY, dates, worktree("TASK-3"), worktree("TASK-1"), worktree("TASK-2"))

        assertEquals(listOf("TASK-2", "TASK-1", "TASK-3"), ordered)
    }

    @Test
    fun `equal names keep a total order`() {
        val ordered = sort(WorktreeSort.NAME, emptyMap(), worktree("Task-1"), worktree("task-1"))

        assertEquals(listOf("Task-1", "task-1"), ordered)
    }
}
