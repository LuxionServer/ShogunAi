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
    fun `a blocked or done agent waits for the user`() {
        listOf(AgentState.BLOCKED, AgentState.DONE).forEach { state ->
            assertEquals(Activity.WaitingForYou, activity(worktree, null, listOf(RunningAgent("claude", state))))
        }
    }

    @Test
    fun `a working agent or one without a state keeps the worktree agent working`() {
        listOf(AgentState.WORKING, null).forEach { state ->
            assertEquals(Activity.AgentWorking, activity(worktree, null, listOf(RunningAgent("claude", state))))
        }
    }

    @Test
    fun `an idle agent with pending changes leaves the worktree unfinished`() {
        val idle = listOf(RunningAgent("claude", AgentState.IDLE))

        assertEquals(Activity.Unfinished, activity(worktree, status(changes = 2), idle))
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
    fun `a blocked agent waits for the user whatever the git status`() {
        val blocked = listOf(RunningAgent("claude", AgentState.BLOCKED))

        assertEquals(Activity.WaitingForYou, activity(worktree, status(), blocked))
        assertEquals(Activity.WaitingForYou, activity(worktree, status(changes = 3, lastCommitAt = now.minus(Duration.ofDays(20))), blocked))
    }

    @Test
    fun `an agent waiting in the main worktree waits for the user`() {
        val done = listOf(RunningAgent("claude", AgentState.DONE))

        assertEquals(Activity.WaitingForYou, activity(main, status(changes = 1, mainBranch = null), done))
    }

    @Test
    fun `one blocked agent among working ones waits for the user`() {
        val agents = listOf(RunningAgent("claude", AgentState.WORKING), RunningAgent("codex", AgentState.BLOCKED))

        assertEquals(Activity.WaitingForYou, activity(worktree, null, agents))
    }

    @Test
    fun `uncommitted changes leave the worktree unfinished`() {
        assertEquals(Activity.Unfinished, activity(worktree, status(changes = 2)))
    }

    @Test
    fun `the main worktree with changes or unpushed commits stays idle`() {
        assertEquals(Activity.Idle, activity(main, status(changes = 1, mainBranch = null)))
        assertEquals(Activity.Idle, activity(main, status(upstream = Divergence(2, 0), mainBranch = null)))
    }

    @Test
    fun `a clean main worktree is idle`() {
        assertEquals(Activity.Idle, activity(main, status(mainBranch = null)))
    }

    @Test
    fun `commits ahead of the upstream leave the worktree unfinished`() {
        assertEquals(Activity.Unfinished, activity(worktree, status(upstream = Divergence(1, 0))))
    }

    @Test
    fun `commits of its own with no upstream leave the worktree unfinished`() {
        assertEquals(Activity.Unfinished, activity(worktree, status(upstream = null, mainBranch = Divergence(1, 0))))
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
    fun `an old worktree with pending work is inactive`() {
        val old = now.minus(Duration.ofDays(5))

        assertEquals(Activity.Inactive, activity(worktree, status(changes = 3, lastCommitAt = old)))
        assertEquals(Activity.Inactive, activity(worktree, status(upstream = Divergence(2, 0), lastCommitAt = old)))
        assertEquals(Activity.Inactive, activity(worktree, status(upstream = null, lastCommitAt = old)))
    }

    @Test
    fun `an old merged worktree stays merged`() {
        val old = now.minus(Duration.ofDays(5))
        val mergedIntoMain = WorktreeStatus(0, Divergence(0, 0), Divergence(0, 3), old, base = Divergence(2, 0))

        assertEquals(Activity.Merged, activity(worktree, mergedIntoMain))
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
    fun `only the first five activities are active`() {
        val active = Activity.entries.filter { it.isActive }

        assertEquals(listOf(Activity.WaitingForYou, Activity.AgentWorking, Activity.Unfinished, Activity.New, Activity.Stopped), active)
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
    fun `a branch of an epic without commits of its own is new and dated by its creation`() {
        val status = WorktreeStatus(
            uncommittedChanges = 0,
            upstream = null,
            mainBranch = Divergence(5, 0),
            lastCommitAt = now.minus(Duration.ofDays(10)),
            createdAt = now.minus(Duration.ofDays(2)),
            base = Divergence(0, 0),
            baseName = "epic/redesign",
        )

        assertEquals(ActivityDate(now.minus(Duration.ofDays(2)), ActivityKind.CREATED), activityDate(worktree, status))
        assertEquals(Activity.New, worktreeActivity(worktree, status, emptyList(), now))
    }

    @Test
    fun `a branch merged into its base reads as merged`() {
        val status = WorktreeStatus(0, null, Divergence(4, 2), now, base = Divergence(0, 1), baseName = "epic/redesign")

        assertEquals(Activity.Merged, worktreeActivity(worktree, status, emptyList(), now))
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

    @Test
    fun `only a started branch with its own unmerged commits and no upstream is unpushed`() {
        assertTrue(WorktreeStatus(0, null, Divergence(2, 0), null).isUnpushedBranch)
        assertFalse(WorktreeStatus(0, Divergence(0, 0), Divergence(2, 0), null).isUnpushedBranch)
        assertFalse(WorktreeStatus(0, null, Divergence(0, 0), null).isUnpushedBranch)
        assertFalse(WorktreeStatus(0, null, null, null, neverCommitted = true).isUnpushedBranch)
        assertFalse(WorktreeStatus(0, null, null, null, neverCommitted = true, base = Divergence(1, 0)).isUnpushedBranch)
        assertFalse(WorktreeStatus(0, null, Divergence(0, 3), null, base = Divergence(2, 0)).isUnpushedBranch)
    }
}
