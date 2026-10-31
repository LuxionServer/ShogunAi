package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.ui.worktree.inspector.displayPath
import kotlin.test.Test
import kotlin.test.assertEquals

class InspectorPathTest {

    private val home = "/home/dev"

    @Test
    fun `a path inside the worktrees folder is shown relative to it`() {
        assertEquals("TASK-123", displayPath("/home/dev/projects/main-repo-worktrees/TASK-123", "/home/dev/projects/main-repo-worktrees", home))
    }

    @Test
    fun `a worktrees folder written from the home folder is expanded`() {
        assertEquals("TASK-123", displayPath("/home/dev/projects/main-repo-worktrees/TASK-123", "~/projects/main-repo-worktrees/", home))
        assertEquals("projects/TASK-123", displayPath("/home/dev/projects/TASK-123", "~", home))
    }

    @Test
    fun `a path outside the worktrees folder is shown from the home folder`() {
        assertEquals("~/projects/main-repo", displayPath("/home/dev/projects/main-repo", "/home/dev/projects/main-repo-worktrees", home))
    }

    @Test
    fun `a path outside the home folder is shown whole`() {
        assertEquals("/srv/repos/main-repo", displayPath("/srv/repos/main-repo", "/srv/worktrees", home))
    }

    @Test
    fun `windows separators are understood`() {
        assertEquals("TASK-123", displayPath("C:\\Users\\dev\\worktrees\\TASK-123", "~\\worktrees", "C:\\Users\\dev"))
    }

    @Test
    fun `the worktrees folder itself is not shown as an empty path`() {
        assertEquals("~/worktrees", displayPath("/home/dev/worktrees", "/home/dev/worktrees", home))
    }
}
