package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.model.Divergence
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeStatus
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
    fun `a missing folder wins over an agent`() {
        assertEquals(Activity.Missing, activity(worktree.copy(isPrunable = true), status(), agent))
    }

    @Test
    fun `a blocked or done agent needs the user`() {
        listOf(AgentState.BLOCKED, AgentState.DONE).forEach { state ->
            assertEquals(Activity.NeedsYou, activity(worktree, null, listOf(RunningAgent("claude", state))))
        }
    }

    @Test
    fun `a working agent or one without a state keeps the worktree agent working`() {
        listOf(AgentState.WORKING, null).forEach { state ->
            assertEquals(Activity.AgentWorking, activity(worktree, null, listOf(RunningAgent("claude", state))))
        }
    }

    @Test
    fun `an idle agent with pending changes needs the user`() {
        val idle = listOf(RunningAgent("claude", AgentState.IDLE))

        assertEquals(Activity.NeedsYou, activity(worktree, status(changes = 2), idle))
    }

    @Test
    fun `an agent working over uncommitted changes stays agent working`() {
        val working = listOf(RunningAgent("claude", AgentState.WORKING))

        assertEquals(Activity.AgentWorking, activity(worktree, status(changes = 2), working))
    }

    @Test
    fun `an idle agent without pending work leaves the worktree stopped`() {
        val idle = listOf(RunningAgent("claude", AgentState.IDLE))

        assertEquals(Activity.Stopped, activity(worktree, status(), idle))
    }

    @Test
    fun `a blocked agent needs the user whatever the git status`() {
        val blocked = listOf(RunningAgent("claude", AgentState.BLOCKED))

        assertEquals(Activity.NeedsYou, activity(worktree, status(), blocked))
    }

    @Test
    fun `one blocked agent among working ones needs the user`() {
        val agents = listOf(RunningAgent("claude", AgentState.WORKING), RunningAgent("codex", AgentState.BLOCKED))

        assertEquals(Activity.NeedsYou, activity(worktree, null, agents))
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
    fun `a freshly created worktree is new`() {
        assertEquals(Activity.New, activity(worktree, status(upstream = null, mainBranch = Divergence(0, 0))))
    }

    @Test
    fun `a pushed worktree with nothing pending is stopped`() {
        assertEquals(Activity.Stopped, activity(worktree, status()))
    }

    @Test
    fun `a merged worktree is merged`() {
        assertEquals(Activity.Merged, activity(worktree, status(mainBranch = Divergence(0, 3))))
    }

    @Test
    fun `an old clean worktree is inactive`() {
        val old = now.minus(Duration.ofDays(5))

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
    fun `only the first four activities are active`() {
        val active = Activity.entries.filter { it.isActive }

        assertEquals(listOf(Activity.NeedsYou, Activity.AgentWorking, Activity.New, Activity.Stopped), active)
        assertTrue(Activity.AgentWorking.isActive)
        assertFalse(Activity.Idle.isActive)
    }

    private fun statusWithDates(
        mainBranch: Divergence? = Divergence(2, 0),
        lastCommitAt: Instant? = now.minus(Duration.ofHours(3)),
        createdAt: Instant? = now.minus(Duration.ofDays(2)),
    ) = WorktreeStatus(0, null, mainBranch, lastCommitAt, createdAt)

    @Test
    fun `a branch with commits of its own is dated by its last commit`() {
        val date = activityDate(worktree, statusWithDates())

        assertEquals(ActivityDate(now.minus(Duration.ofHours(3)), ActivityKind.COMMIT), date)
    }

    @Test
    fun `a branch without commits of its own is dated by its creation`() {
        val date = activityDate(
            worktree,
            statusWithDates(mainBranch = Divergence(0, 0), lastCommitAt = now.minus(Duration.ofDays(10))),
        )

        assertEquals(ActivityDate(now.minus(Duration.ofDays(2)), ActivityKind.CREATED), date)
    }

    @Test
    fun `the main worktree is dated by its last commit`() {
        val date = activityDate(main, statusWithDates(mainBranch = null))

        assertEquals(ActivityKind.COMMIT, date?.kind)
    }

    @Test
    fun `without a main branch the last commit is used`() {
        assertEquals(ActivityKind.COMMIT, activityDate(worktree, statusWithDates(mainBranch = null))?.kind)
    }

    @Test
    fun `an unreadable creation date falls back to the last commit`() {
        val date = activityDate(worktree, statusWithDates(mainBranch = Divergence(0, 0), createdAt = null))

        assertEquals(ActivityKind.COMMIT, date?.kind)
    }

    @Test
    fun `a worktree without status has no activity date`() {
        assertNull(activityDate(worktree, null))
    }

    @Test
    fun `a merged worktree isn't new`() {
        assertEquals(Activity.Merged, activity(worktree, status(upstream = null, mainBranch = Divergence(0, 3))))
    }

    @Test
    fun `a never committed branch behind main is new`() {
        val neverCommitted = WorktreeStatus(0, null, Divergence(0, 3), now, neverCommitted = true)

        assertEquals(Activity.New, activity(worktree, neverCommitted))
    }

    @Test
    fun `a longer threshold keeps the worktree stopped`() {
        val old = status(lastCommitAt = now.minus(Duration.ofDays(5)))

        assertEquals(Activity.Stopped, worktreeActivity(worktree, old, null, now, Duration.ofDays(7)))
    }
}
