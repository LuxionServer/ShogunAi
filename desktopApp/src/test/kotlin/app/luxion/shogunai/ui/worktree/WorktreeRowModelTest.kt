package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.model.Divergence
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.HeadlessAppProcess
import app.luxion.shogunai.domain.model.HeadlessAppState
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeEmulator
import app.luxion.shogunai.domain.model.WorktreeOrigin
import app.luxion.shogunai.domain.model.WorktreeSimulator
import app.luxion.shogunai.domain.model.WorktreeStatus
import app.luxion.shogunai.domain.model.toLocalClockTime
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.theme.Status
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class WorktreeRowModelTest {

    private val now = Instant.parse("2026-10-16T12:00:00Z")

    private fun worktree(
        path: String = "/home/dev/projects/main-repo-worktrees/TASK-123",
        branch: String? = "feature/TASK-123",
        isMain: Boolean = false,
        isPrunable: Boolean = false,
        isLocked: Boolean = false,
    ) = Worktree(path = path, branch = branch, isMain = isMain, isLocked = isLocked, isPrunable = isPrunable)

    private fun status(
        changes: Int = 0,
        upstream: Divergence? = Divergence(0, 0),
        mainBranch: Divergence? = Divergence(2, 0),
        lastCommitAt: Instant? = now.minus(Duration.ofHours(3)),
        createdAt: Instant? = null,
    ) = WorktreeStatus(
        uncommittedChanges = changes,
        upstream = upstream,
        mainBranch = mainBranch,
        lastCommitAt = lastCommitAt,
        createdAt = createdAt,
    )

    private fun model(
        worktree: Worktree = worktree(),
        status: WorktreeStatusState? = WorktreeStatusState.Loaded(status()),
        mainBranchName: String? = "main",
        agents: List<RunningAgent> = emptyList(),
        emulator: WorktreeEmulator? = null,
        simulator: WorktreeSimulator? = null,
        origin: WorktreeOrigin? = WorktreeOrigin.SHOGUNAI,
        projectName: String? = null,
        inactiveAfter: Duration = Duration.ofDays(3),
        desktopTurn: WorktreeDesktopTurn? = null,
        headlessApp: HeadlessAppProcess? = null,
    ) = worktreeRowModel(
        worktree, status, mainBranchName, agents, emulator, simulator, origin, projectName, inactiveAfter, now, EnglishStrings,
        desktopTurn = desktopTurn,
        headlessApp = headlessApp,
    )

    @Test
    fun `a missing folder badge beats a running agent`() {
        val model = model(worktree(isPrunable = true), agents = listOf(RunningAgent("claude")))

        assertEquals(StateBadgeKind.FOLDER_MISSING, model.badge.kind)
        assertEquals("Folder missing", model.badge.label)
        assertTrue(model.isFolderMissing)
    }

    @Test
    fun `the badge follows the worktree's state`() {
        fun kind(model: WorktreeRowModel) = model.badge.kind

        assertEquals(StateBadgeKind.NEEDS_YOU, kind(model(status = WorktreeStatusState.Loaded(status(changes = 3)))))
        assertEquals(
            StateBadgeKind.AGENT_WORKING,
            kind(model(status = WorktreeStatusState.Loaded(status(changes = 3)), agents = listOf(RunningAgent("claude", AgentState.WORKING)))),
        )
        assertEquals(StateBadgeKind.NEW, kind(model(status = WorktreeStatusState.Loaded(status(mainBranch = Divergence(0, 0), upstream = null)))))
        assertEquals(StateBadgeKind.STOPPED, kind(model()))
        assertEquals(StateBadgeKind.MERGED, kind(model(status = WorktreeStatusState.Loaded(status(mainBranch = Divergence(0, 5))))))
        assertEquals(
            StateBadgeKind.INACTIVE,
            kind(model(status = WorktreeStatusState.Loaded(status(lastCommitAt = now.minus(Duration.ofDays(5)))))),
        )
        assertEquals(StateBadgeKind.OTHER, kind(model(status = WorktreeStatusState.Loading)))
    }

    @Test
    fun `the execution cell shows the most urgent agent with the count of the others`() {
        val agents = listOf(
            RunningAgent("codex", AgentState.WORKING),
            RunningAgent("claude", AgentState.BLOCKED),
        )

        val agent = model(agents = agents).execution.agent!!

        assertEquals("Claude · blocked", agent.label)
        assertEquals(Status.WARNING, agent.status)
        assertEquals(1, agent.moreCount)
        assertEquals("Claude: blocked\nCodex: working", agent.tooltip)
    }

    @Test
    fun `the agent's status follows the state Herdr reports`() {
        val expected = mapOf(
            AgentState.WORKING to ("Claude · working" to Status.INFO),
            AgentState.BLOCKED to ("Claude · blocked" to Status.WARNING),
            AgentState.DONE to ("Claude · done" to Status.SUCCESS),
            AgentState.IDLE to ("Claude · idle" to Status.NEUTRAL),
        )

        expected.forEach { (state, label) ->
            val agent = model(agents = listOf(RunningAgent("claude", state))).execution.agent!!

            assertEquals(label, agent.label to agent.status)
        }
    }

    @Test
    fun `an agent without a state shows only its name`() {
        val agent = model(agents = listOf(RunningAgent("claude"))).execution.agent!!

        assertEquals("Claude", agent.label)
        assertEquals(Status.INFO, agent.status)
        assertEquals("Claude: state unknown", agent.tooltip)
    }

    @Test
    fun `without agents or devices the execution cell is empty`() {
        val execution = model().execution

        assertNull(execution.agent)
        assertNull(execution.device)
    }

    @Test
    fun `the device line appears only while the emulator or the simulator is active`() {
        val booting = model(emulator = WorktreeEmulator("emulator-5556", EmulatorState.BOOTING))
        val running = model(emulator = WorktreeEmulator("emulator-5556", EmulatorState.RUNNING))
        val stopped = model(emulator = WorktreeEmulator("emulator-5556", EmulatorState.STOPPED))
        val bootedSimulator = model(simulator = WorktreeSimulator("ABCD-EFGH", "iPhone 16", SimulatorState.BOOTED))
        val shutdownSimulator = model(simulator = WorktreeSimulator("ABCD-EFGH", "iPhone 16", SimulatorState.SHUTDOWN))

        assertEquals(Status.INFO, booting.execution.device?.status)
        assertEquals(Status.SUCCESS, running.execution.device?.status)
        assertNull(stopped.execution.device)
        assertTrue(bootedSimulator.execution.device!!.isSimulator)
        assertNull(shutdownSimulator.execution.device)
    }

    @Test
    fun `a worktree holding the desktop shows it on the second line with its expiry in the tooltip`() {
        val expiresAt = now.plusSeconds(600)

        val line = model(desktopTurn = WorktreeDesktopTurn.Holding(expiresAt.toEpochMilli())).execution.device!!

        assertEquals(SecondLineKind.DESKTOP_HOLDER, line.kind)
        assertEquals("Has the desktop", line.label)
        assertEquals("Has the desktop until ${expiresAt.toLocalClockTime()}", line.tooltip)
        assertEquals(0, line.moreCount)
    }

    @Test
    fun `a worktree waiting for the desktop with a running app shows the wait first and counts the app`() {
        val line = model(
            desktopTurn = WorktreeDesktopTurn.Waiting(2),
            headlessApp = HeadlessAppProcess(HeadlessAppState.RUNNING, 4242, "/home/dev/launch.log"),
        ).execution.device!!

        assertEquals(SecondLineKind.DESKTOP_WAITING, line.kind)
        assertEquals("Waiting for the desktop, #2", line.label)
        assertEquals(1, line.moreCount)
        assertEquals("Waiting for the desktop, #2\nApp in the background", line.tooltip)
    }

    @Test
    fun `the desktop turn comes before the device and the headless app after it`() {
        val line = model(
            emulator = WorktreeEmulator("emulator-5556", EmulatorState.RUNNING),
            desktopTurn = WorktreeDesktopTurn.Holding(now.toEpochMilli()),
            headlessApp = HeadlessAppProcess(HeadlessAppState.RUNNING, 4242, "/home/dev/launch.log"),
        ).execution.device!!

        assertEquals(SecondLineKind.DESKTOP_HOLDER, line.kind)
        assertEquals(2, line.moreCount)
    }

    @Test
    fun `a running headless app alone shows in the background and a stopped one doesn't`() {
        val running = model(headlessApp = HeadlessAppProcess(HeadlessAppState.RUNNING, 4242, "/home/dev/launch.log")).execution.device!!
        val stopped = model(headlessApp = HeadlessAppProcess(HeadlessAppState.STOPPED, null, "/home/dev/launch.log")).execution.device

        assertEquals(SecondLineKind.HEADLESS_APP, running.kind)
        assertEquals("App in the background", running.label)
        assertNull(stopped)
    }

    @Test
    fun `the name cell hides the folder when it matches the last segment of the branch`() {
        val name = model().name

        assertEquals("feature/TASK-123", name.branch)
        assertNull(name.folder)
        assertEquals("ShogunAi", name.origin)
    }

    @Test
    fun `the name cell shows the folder when it differs from the branch`() {
        val name = model(worktree = worktree(path = "/home/dev/projects/main-repo-worktrees/task-123-wt")).name

        assertEquals("task-123-wt", name.folder)
    }

    @Test
    fun `the name cell carries the project only when asked to`() {
        assertNull(model().name.project)
        assertEquals("api", model(projectName = "api").name.project)
    }

    @Test
    fun `a detached worktree shows the detached label`() {
        val name = model(worktree = worktree(branch = null)).name

        assertEquals("(detached)", name.branch)
        assertEquals("TASK-123", name.folder)
    }

    @Test
    fun `the git cell shows changes above and divergence below naming the main branch`() {
        val git = model(status = WorktreeStatusState.Loaded(status(changes = 3, mainBranch = Divergence(2, 1)))).git

        assertEquals(GitCellState.READY, git.state)
        assertEquals("✎ 3", git.changes)
        assertEquals("3 changes", git.changesTooltip)
        assertEquals("↑2 ↓1", git.secondLine)
        assertEquals("↑2 ↓1 main", git.secondLineTooltip)
    }

    @Test
    fun `a clean worktree has no changes line`() {
        assertNull(model().git.changes)
    }

    @Test
    fun `the git cell shows merged with the main branch in its tooltip`() {
        val git = model(status = WorktreeStatusState.Loaded(status(mainBranch = Divergence(0, 5)))).git

        assertEquals("merged", git.secondLine)
        assertEquals("merged into main", git.secondLineTooltip)
    }

    @Test
    fun `a branch that never had commits is not shown as merged`() {
        val loaded = status(mainBranch = Divergence(0, 5)).copy(neverCommitted = true)

        assertEquals("↓5", model(status = WorktreeStatusState.Loaded(loaded)).git.secondLine)
    }

    @Test
    fun `the git cell reports loading and unavailable statuses`() {
        assertEquals(GitCellState.LOADING, model(status = WorktreeStatusState.Loading).git.state)

        val unavailable = model(status = WorktreeStatusState.Unavailable).git

        assertEquals(GitCellState.UNAVAILABLE, unavailable.state)
        assertEquals("Status unavailable", unavailable.unavailableTooltip)
    }

    @Test
    fun `a branch with commits of its own is dated by its last commit`() {
        assertEquals("commit 3 hours ago", model().time.activity)
    }

    @Test
    fun `a new worktree is dated by its creation, not by the base commit`() {
        val loaded = status(
            mainBranch = Divergence(0, 0),
            lastCommitAt = now.minus(Duration.ofDays(10)),
            createdAt = now.minus(Duration.ofHours(2)),
        )

        assertEquals("created 2 hours ago", model(status = WorktreeStatusState.Loaded(loaded)).time.activity)
    }

    @Test
    fun `the time cell has no date while the status is loading`() {
        assertNull(model(status = WorktreeStatusState.Loading).time.activity)
    }

    @Test
    fun `the time cell marks the main and locked worktrees`() {
        val main = worktree(path = "/home/dev/projects/main-repo", branch = "main", isMain = true, isLocked = true)

        assertEquals(listOf("main", "locked"), model(worktree = main, origin = null).time.marks)
        assertEquals(emptyList(), model().time.marks)
        assertFalse(model().isFolderMissing)
    }

    @Test
    fun `a longer inactivity threshold keeps the worktree stopped`() {
        val loaded = WorktreeStatusState.Loaded(status(lastCommitAt = now.minus(Duration.ofDays(5))))

        assertEquals(StateBadgeKind.STOPPED, model(status = loaded, inactiveAfter = Duration.ofDays(7)).badge.kind)
    }
}
