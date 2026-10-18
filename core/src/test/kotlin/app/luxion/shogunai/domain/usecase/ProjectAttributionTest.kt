package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.model.Worktree
import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectAttributionTest {

    private val main = Worktree(path = "/home/dev/projects/main-repo", branch = "develop", isMain = true)
    private val nested = Worktree(path = "/home/dev/projects/main-repo/.claude/worktrees/task", branch = "worktree-task")
    private val apiTask = Worktree(path = "/home/dev/projects/api-TASK-9", branch = "feature/TASK-9")
    private val api = Worktree(path = "/home/dev/projects/api", branch = "develop", isMain = true)

    private val projects = listOf(
        ProjectWorktreeSet("main-repo", listOf(main, nested)),
        ProjectWorktreeSet("api", listOf(api, apiTask)),
    )

    private fun attribute(path: String, sets: List<ProjectWorktreeSet> = projects, exists: Boolean = true) =
        attributeToProjects(path, sets) { exists }

    @Test
    fun `a folder inside a worktree belongs to it`() {
        assertEquals(
            ProjectOwner.Owned("api", apiTask.path),
            attribute("${apiTask.path}/app/build"),
        )
    }

    @Test
    fun `the deepest worktree wins over the one that contains it`() {
        assertEquals(
            ProjectOwner.Owned("main-repo", nested.path),
            attribute("${nested.path}/src"),
        )
        assertEquals(
            ProjectOwner.Owned("main-repo", main.path),
            attribute("${main.path}/src"),
        )
    }

    @Test
    fun `a sibling folder with a common prefix isn't inside the worktree`() {
        assertEquals(ProjectOwner.Outside, attribute("/home/dev/projects/api-TASK-90"))
    }

    @Test
    fun `a folder that no longer exists belongs to a removed worktree`() {
        assertEquals(
            ProjectOwner.RemovedWorktree,
            attribute("/home/dev/projects/api-TASK-1", exists = false),
        )
    }

    @Test
    fun `an existing folder outside every project is outside`() {
        assertEquals(ProjectOwner.Outside, attribute("/home/dev/other/thing"))
    }

    @Test
    fun `an unowned folder is loading while a project's worktrees are unknown`() {
        val sets = listOf(ProjectWorktreeSet("main-repo", listOf(main)), ProjectWorktreeSet("api", null))
        assertEquals(ProjectOwner.Loading, attribute("/home/dev/other/thing", sets))
    }

    @Test
    fun `a folder owned by a loaded project doesn't wait for the others`() {
        val sets = listOf(ProjectWorktreeSet("main-repo", listOf(main)), ProjectWorktreeSet("api", null))
        assertEquals(ProjectOwner.Owned("main-repo", main.path), attribute("${main.path}/src", sets))
    }
}
