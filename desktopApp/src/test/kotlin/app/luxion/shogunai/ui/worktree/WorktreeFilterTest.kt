package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.model.Worktree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorktreeFilterTest {

    private val worktrees = listOf(
        Worktree(path = "/home/dev/projects/TASK-12", branch = "feature/login"),
        Worktree(path = "/home/dev/projects/other", branch = "bugfix/task-120"),
        Worktree(path = "/home/dev/projects/main-repo", branch = null),
    )

    @Test
    fun `matches the branch or the folder name ignoring case`() {
        assertEquals(worktrees.take(2), filterWorktrees(worktrees, "Task-12"))
        assertEquals(listOf(worktrees[2]), filterWorktrees(worktrees, "MAIN"))
    }

    @Test
    fun `a blank filter keeps every worktree`() {
        assertEquals(worktrees, filterWorktrees(worktrees, "  "))
    }

    @Test
    fun `the parent folders don't match`() {
        assertTrue(filterWorktrees(worktrees, "projects").isEmpty())
    }
}
