package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.model.Divergence
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeEmulator
import app.luxion.shogunai.domain.model.WorktreeOrigin
import app.luxion.shogunai.domain.model.WorktreeSimulator
import app.luxion.shogunai.domain.model.WorktreeStatus
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorktreeRowModelTest {

    private val now = Instant.parse("2026-10-16T12:00:00Z")
    private val worktree = Worktree(path = "/home/dev/projects/TASK-123", branch = "feature/TASK-123")
    private val claude = listOf(RunningAgent("Claude"))

    private fun status(
        changes: Int = 0,
        mainBranch: Divergence? = Divergence(ahead = 0, behind = 0),
        lastCommit: Duration = Duration.ofDays(1),
    ) = WorktreeStatusState.Loaded(
        WorktreeStatus(
            uncommittedChanges = changes,
            upstream = null,
            mainBranch = mainBranch,
            lastCommitAt = now.minus(lastCommit),
        ),
    )

    private fun model(
        worktree: Worktree = this.worktree,
        status: WorktreeStatusState? = status(),
        agents: List<RunningAgent> = emptyList(),
        emulator: WorktreeEmulator? = null,
        simulator: WorktreeSimulator? = null,
    ) = worktreeRowModel(worktree, status, agents, emulator, simulator, WorktreeOrigin.SHOGUNAI, now)

    @Test
    fun `shows the branch as the label and the folder name as secondary text`() {
        val row = model()

        assertEquals("feature/TASK-123", row.branch)
        assertEquals("TASK-123", row.folderName)
        assertEquals(WorktreeOrigin.SHOGUNAI, row.origin)
    }

    @Test
    fun `a missing folder beats every other state`() {
        val row = model(worktree = worktree.copy(isPrunable = true), status = status(changes = 2), agents = claude)

        assertEquals(RowIndicator.MISSING, row.indicator)
        assertTrue(row.chips.isEmpty())
    }

    @Test
    fun `a running agent beats uncommitted changes`() {
        assertEquals(RowIndicator.AGENT_RUNNING, model(status = status(changes = 3), agents = claude).indicator)
    }

    @Test
    fun `uncommitted changes beat merged`() {
        val merged = status(changes = 1, mainBranch = Divergence(ahead = 0, behind = 4))

        assertEquals(RowIndicator.UNCOMMITTED_CHANGES, model(status = merged).indicator)
    }

    @Test
    fun `merged beats inactive`() {
        val merged = status(mainBranch = Divergence(ahead = 0, behind = 4), lastCommit = Duration.ofDays(30))

        assertEquals(RowIndicator.MERGED, model(status = merged).indicator)
    }

    @Test
    fun `an old clean worktree without agents is inactive`() {
        val row = model(status = status(lastCommit = Duration.ofDays(20)))

        assertEquals(RowIndicator.INACTIVE, row.indicator)
        assertEquals(listOf(RowChip(RowChipKind.INACTIVE)), row.chips)
    }

    @Test
    fun `a clean recent worktree has no indicator and no chips`() {
        val row = model()

        assertEquals(RowIndicator.CLEAN, row.indicator)
        assertTrue(row.chips.isEmpty())
    }

    @Test
    fun `chips follow a fixed order and only appear when they apply`() {
        val row = model(
            worktree = worktree.copy(isLocked = true),
            status = status(changes = 3, mainBranch = Divergence(ahead = 2, behind = 5)),
            agents = claude,
            emulator = WorktreeEmulator("emulator-5556", EmulatorState.RUNNING),
        )

        assertEquals(
            listOf(
                RowChip(RowChipKind.AGENT, "Claude"),
                RowChip(RowChipKind.CHANGES, "✎3"),
                RowChip(RowChipKind.DIVERGENCE, "↑2 ↓5"),
                RowChip(RowChipKind.LOCKED),
                RowChip(RowChipKind.DEVICE),
            ),
            row.chips,
        )
    }

    @Test
    fun `a merged worktree shows the merged chip instead of its divergence`() {
        val row = model(status = status(mainBranch = Divergence(ahead = 0, behind = 5)))

        assertEquals(listOf(RowChip(RowChipKind.MERGED)), row.chips)
    }

    @Test
    fun `the device chip only shows while a device is booting or running`() {
        val stopped = model(
            emulator = WorktreeEmulator("emulator-5556", EmulatorState.STOPPED),
            simulator = WorktreeSimulator("udid", "name", SimulatorState.SHUTDOWN),
        )
        val booting = model(emulator = WorktreeEmulator("emulator-5556", EmulatorState.BOOTING))
        val booted = model(simulator = WorktreeSimulator("udid", "name", SimulatorState.BOOTED))

        assertTrue(stopped.chips.isEmpty())
        assertEquals(listOf(RowChip(RowChipKind.DEVICE)), booting.chips)
        assertEquals(listOf(RowChip(RowChipKind.DEVICE)), booted.chips)
    }

    @Test
    fun `a status that is loading or unavailable adds no chips`() {
        assertTrue(model(status = WorktreeStatusState.Loading).chips.isEmpty())
        assertTrue(model(status = WorktreeStatusState.Unavailable).chips.isEmpty())
    }

    @Test
    fun `the main worktree is listed first`() {
        val other = Worktree(path = "/home/dev/projects/other", branch = "feature/other")
        val main = Worktree(path = "/home/dev/projects/main-repo", branch = "main", isMain = true)

        assertEquals(listOf(main, other), orderedForList(listOf(other, main)))
    }
}
