package app.luxion.shogunai.domain.usecase

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DetectGroupsUseCaseTest {

    private val world = MultirepoTestWorld()
    private val detect = DetectGroupsUseCase(world.git)

    @Test
    fun `worktrees created anywhere with the same branch form a group in place`() = runTest {
        with(world.git) {
            world.app.worktree("/home/dev/projects/product-app-worktrees/TASK-7", "feature/TASK-7")
            world.api.worktree("/home/dev/.herdr/worktrees/product-api/api-task-7", "feature/TASK-7")
        }

        val result = detect(world.members())

        val group = result.groups.single()
        assertEquals("feature/TASK-7", group.branch)
        assertEquals("TASK-7", group.title)
        assertEquals(listOf("/home/dev/projects/product-app-worktrees/TASK-7", "/home/dev/.herdr/worktrees/product-api/api-task-7"), group.worktrees.map { it.worktree.path })
        assertTrue(result.ungrouped.isEmpty())
    }

    @Test
    fun `worktrees made with today's shogun new in the proposed folders are adopted as they are`() = runTest {
        with(world.git) {
            world.app.worktree("${world.worktreesRoot}/app/TASK-1", "feature/TASK-1")
            world.api.worktree("${world.worktreesRoot}/api/TASK-1", "feature/TASK-1")
        }

        val group = detect(world.members()).groups.single()

        assertEquals(listOf("${world.worktreesRoot}/app/TASK-1", "${world.worktreesRoot}/api/TASK-1"), group.worktrees.map { it.worktree.path })
    }

    @Test
    fun `a branch in a single repository, different prefixes and main checkouts aren't groups`() = runTest {
        with(world.git) {
            world.app.worktree("/w/app-8", "feature/TASK-8")
            world.app.worktree("/w/app-9", "feature/TASK-9")
            world.api.worktree("/w/api-9", "fix/TASK-9")
        }

        val result = detect(world.members())

        assertTrue(result.groups.isEmpty())
        assertEquals(listOf("/w/app-8", "/w/app-9", "/w/api-9"), result.ungrouped.map { it.worktree.path })
    }

    @Test
    fun `a repository that can't be listed is reported and the others still group`() = runTest {
        with(world.git) {
            world.app.worktree("/w/app-1", "feature/TASK-1")
            world.api.worktree("/w/api-1", "feature/TASK-1")
        }
        world.git.repositories.remove(world.docs)

        val result = detect(world.members())

        assertEquals(setOf("docs"), result.failures.keys)
        assertEquals(1, result.groups.size)
    }
    @Test
    fun `a detached worktree is never grouped`() = runTest {
        with(world.git) {
            world.app.worktree("${world.worktreesRoot}/app/detached", null)
            world.api.worktree("${world.worktreesRoot}/api/detached", null)
        }

        assertTrue(detect(world.members()).groups.isEmpty())
    }

    @Test
    fun `a repository without a project gets the proposed folder only when it is the workspace`() {
        world.makeFolderARepository()
        val multirepo = world.resolved()

        val members = multirepoMembers(multirepo, emptyList())

        assertEquals("", members.first { it.alias == "app" }.config.worktreesRoot)
        assertEquals("${world.worktreesRoot}/workspace", members.first { it.alias == "workspace" }.config.worktreesRoot)
    }
}
