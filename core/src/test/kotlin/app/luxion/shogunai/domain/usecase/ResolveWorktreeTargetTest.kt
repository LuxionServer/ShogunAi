package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ResolveWorktreeTargetTest {

    private val repository = "/home/dev/projects/main-repo"
    private val root = "/home/dev/projects/worktrees"
    private val config = ProjectConfig(baseRepositoryPath = repository, worktreesRoot = root, secretFiles = emptyList())
    private val main = Worktree(repository, "develop", isMain = true)
    private val task = Worktree("$root/TASK-123", "feature/TASK-123")
    private val other = Worktree("$root/TASK-456", "fix/TASK-456")
    private val worktrees = listOf(main, task, other)

    private fun resolve(target: String, listed: List<Worktree> = worktrees) =
        resolveWorktreeTarget(target, listed, config) { it.trimEnd('/') }

    @Test
    fun `resolves a path, a branch and a task id to the same worktree`() {
        assertEquals(task, resolve("$root/TASK-123"))
        assertEquals(task, resolve("$root/TASK-123/"))
        assertEquals(task, resolve("feature/TASK-123"))
        assertEquals(task, resolve("TASK-123"))
        assertEquals(other, resolve("TASK-456"))
    }

    @Test
    fun `refuses the main worktree by path or by branch`() {
        assertIs<WorktreeError.CannotRemoveMainWorktree>(assertFailsWith<WorktreeError> { resolve(repository) })
        assertIs<WorktreeError.CannotRemoveMainWorktree>(assertFailsWith<WorktreeError> { resolve("develop") })
    }

    @Test
    fun `fails for an unknown target without guessing`() {
        assertIs<WorktreeError.WorktreeTargetNotFound>(assertFailsWith<WorktreeError> { resolve("TASK-12") })
    }

    @Test
    fun `lists the candidates when a branch matches more than one worktree`() {
        val twin = Worktree("$root/TASK-124", "feature/TASK-123")

        val error = assertFailsWith<WorktreeError> { resolve("feature/TASK-123", worktrees + twin) }

        assertIs<WorktreeError.AmbiguousWorktreeTarget>(error)
        assertEquals(listOf(task.path, twin.path), error.candidates)
    }

    @Test
    fun `a blank target or a project without a worktrees folder isn't a task id`() {
        assertFailsWith<WorktreeError.WorktreeTargetNotFound> { resolveWorktreeTarget(" ", worktrees, config) { it } }
        assertFailsWith<WorktreeError.WorktreeTargetNotFound> { resolveWorktreeTarget("TASK-123", worktrees, config.copy(worktreesRoot = "")) { it } }
    }
}
