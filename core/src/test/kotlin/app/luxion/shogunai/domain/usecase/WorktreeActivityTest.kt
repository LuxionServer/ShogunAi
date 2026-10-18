package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.model.Divergence
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeStatus
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorktreeActivityTest {

    private val now = Instant.parse("2026-10-16T12:00:00Z")
    private val worktree = Worktree(path = "/home/dev/projects/TASK-1", branch = "feature/TASK-1")
    private val main = Worktree(path = "/home/dev/projects/main-repo", branch = "main", isMain = true)
    private val agent = listOf(RunningAgent("codex"))

    private fun status(
        changes: Int = 0,
        upstream: Divergence? = Divergence(0, 0),
        mainBranch: Divergence? = Divergence(2, 0),
        lastCommitAt: Instant? = now.minusSeconds(60),
    ) = WorktreeStatus(changes, upstream, mainBranch, lastCommitAt)

    private fun activity(worktree: Worktree, status: WorktreeStatus?, agents: List<RunningAgent>? = null) =
        worktreeActivity(worktree, status, agents, now)

    @Test
    fun `an agent running makes the worktree agent working even before its status is read`() {
        assertEquals(Activity.AgentWorking, activity(worktree, null, agent))
    }

    @Test
    fun `an agent on a merged worktree keeps it agent working`() {
        assertEquals(Activity.AgentWorking, activity(worktree, status(mainBranch = Divergence(0, 3)), agent))
    }

    @Test
    fun `uncommitted changes need the user`() {
        assertEquals(Activity.NeedsYou, activity(worktree, status(changes = 2)))
    }

    @Test
    fun `the main worktree with changes needs the user`() {
        assertEquals(Activity.NeedsYou, activity(main, status(changes = 1, mainBranch = null)))
    }

    @Test
    fun `a clean main worktree is idle`() {
        assertEquals(Activity.Idle, activity(main, status(mainBranch = null)))
    }

    @Test
    fun `commits ahead of the upstream need the user`() {
        assertEquals(Activity.NeedsYou, activity(worktree, status(upstream = Divergence(1, 0))))
    }

    @Test
    fun `commits ahead of main with no upstream need the user`() {
        assertEquals(Activity.NeedsYou, activity(worktree, status(upstream = null, mainBranch = Divergence(1, 0))))
    }

    @Test
    fun `a freshly created worktree is in progress`() {
        assertEquals(Activity.InProgress, activity(worktree, status(upstream = null, mainBranch = Divergence(0, 0))))
    }

    @Test
    fun `a pushed worktree with nothing pending is in progress`() {
        assertEquals(Activity.InProgress, activity(worktree, status()))
    }

    @Test
    fun `a merged worktree is merged`() {
        assertEquals(Activity.Merged, activity(worktree, status(mainBranch = Divergence(0, 3))))
    }

    @Test
    fun `an old clean worktree is inactive`() {
        val old = now.minus(Duration.ofDays(20))

        assertEquals(Activity.Inactive, activity(worktree, status(lastCommitAt = old)))
    }

    @Test
    fun `a worktree whose folder is missing is missing`() {
        assertEquals(Activity.Missing, activity(worktree.copy(isPrunable = true), status()))
    }

    @Test
    fun `a worktree whose status isn't read yet is unknown`() {
        assertEquals(Activity.Unknown, activity(worktree, null))
    }

    @Test
    fun `only the first three activities are active`() {
        val active = Activity.entries.filter { it.isActive }

        assertEquals(listOf(Activity.AgentWorking, Activity.NeedsYou, Activity.InProgress), active)
        assertTrue(Activity.AgentWorking.isActive)
        assertFalse(Activity.Idle.isActive)
    }
}
