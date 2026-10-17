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
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.theme.Status
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        mainBranch: Divergence? = Divergence(0, 0),
        lastCommitAt: Instant? = now.minus(Duration.ofDays(1)),
    ) = WorktreeStatus(
        uncommittedChanges = changes,
        upstream = upstream,
        mainBranch = mainBranch,
        lastCommitAt = lastCommitAt,
    )

    private fun model(
        worktree: Worktree = worktree(),
        status: WorktreeStatusState? = WorktreeStatusState.Loaded(status()),
        mainBranchName: String? = "main",
        agents: List<RunningAgent> = emptyList(),
        emulator: WorktreeEmulator? = null,
        simulator: WorktreeSimulator? = null,
        origin: WorktreeOrigin? = WorktreeOrigin.SHOGUNAI,
    ) = worktreeRowModel(worktree, status, mainBranchName, agents, emulator, simulator, origin, now, EnglishStrings)

    private fun chipsOf(model: WorktreeRowModel) = model.chips.map { it.kind }

    @Test
    fun `a missing folder indicator beats a running agent`() {
        val model = model(worktree(isPrunable = true), agents = listOf(RunningAgent("claude")))

        assertEquals(RowIndicator.FOLDER_MISSING, model.indicator)
        assertTrue(RowChipKind.FOLDER_MISSING in chipsOf(model))
    }

    @Test
    fun `a running agent indicator beats uncommitted changes`() {
        val model = model(status = WorktreeStatusState.Loaded(status(changes = 3)), agents = listOf(RunningAgent("claude")))

        assertEquals(RowIndicator.AGENT_RUNNING, model.indicator)
        assertEquals(listOf(RowChipKind.AGENT, RowChipKind.CHANGES), chipsOf(model))
    }

    @Test
    fun `uncommitted changes beat merged`() {
        val model = model(status = WorktreeStatusState.Loaded(status(changes = 3, mainBranch = Divergence(0, 5))))

        assertEquals(RowIndicator.UNCOMMITTED_CHANGES, model.indicator)
        assertEquals(listOf(RowChipKind.CHANGES, RowChipKind.MERGED), chipsOf(model))
    }

    @Test
    fun `merged beats inactive`() {
        val model = model(status = WorktreeStatusState.Loaded(status(mainBranch = Divergence(0, 5), lastCommitAt = now.minus(Duration.ofDays(20)))))

        assertEquals(RowIndicator.MERGED, model.indicator)
        assertEquals(listOf(RowChipKind.MERGED, RowChipKind.INACTIVE), chipsOf(model))
    }

    @Test
    fun `inactive is the indicator when nothing more urgent applies`() {
        val model = model(status = WorktreeStatusState.Loaded(status(lastCommitAt = now.minus(Duration.ofDays(20)))))

        assertEquals(RowIndicator.INACTIVE, model.indicator)
        assertEquals(listOf(RowChipKind.INACTIVE), chipsOf(model))
    }

    @Test
    fun `chips are omitted when no state applies`() {
        val model = model(status = WorktreeStatusState.Loaded(status(lastCommitAt = now.minus(Duration.ofDays(3)))))

        assertNull(model.indicator)
        assertEquals(emptyList(), chipsOf(model))
        assertEquals("3 days ago", model.lastCommitLabel)
    }

    @Test
    fun `divergence against the main branch is a chip that names it`() {
        val model = model(status = WorktreeStatusState.Loaded(status(mainBranch = Divergence(2, 5))))

        assertEquals(
            RowChip(RowChipKind.DIVERGENCE, Status.NEUTRAL, "↑2 ↓5 main"),
            model.chips.single(),
        )
    }

    @Test
    fun `the device chip appears only while the emulator or the simulator is active`() {
        val booting = model(emulator = WorktreeEmulator("emulator-5556", EmulatorState.BOOTING))
        val running = model(emulator = WorktreeEmulator("emulator-5556", EmulatorState.RUNNING))
        val stopped = model(emulator = WorktreeEmulator("emulator-5556", EmulatorState.STOPPED))
        val bootedSimulator = model(simulator = WorktreeSimulator("ABCD-EFGH", "iPhone 16", SimulatorState.BOOTED))
        val shutdownSimulator = model(simulator = WorktreeSimulator("ABCD-EFGH", "iPhone 16", SimulatorState.SHUTDOWN))

        assertEquals(listOf(RowChipKind.DEVICE), chipsOf(booting))
        assertEquals(listOf(RowChipKind.DEVICE), chipsOf(running))
        assertEquals(emptyList(), chipsOf(stopped))
        assertEquals(listOf(RowChipKind.DEVICE), chipsOf(bootedSimulator))
        assertEquals(emptyList(), chipsOf(shutdownSimulator))
    }

    @Test
    fun `loading and unavailable statuses are shown without failing the other chips`() {
        val loading = model(status = WorktreeStatusState.Loading, agents = listOf(RunningAgent("claude")))

        assertTrue(loading.isStatusLoading)
        assertEquals(listOf(RowChipKind.AGENT), chipsOf(loading))

        val unavailable = model(status = WorktreeStatusState.Unavailable)

        assertEquals(listOf(RowChipKind.STATUS_UNAVAILABLE), chipsOf(unavailable))
    }

    @Test
    fun `the main worktree is marked and ordered first`() {
        val branch = worktree(path = "/home/dev/projects/main-repo-worktrees/feature/TASK-123")
        val main = worktree(path = "/home/dev/projects/main-repo", branch = "main", isMain = true)

        val model = model(worktree = main, origin = null)

        assertEquals(listOf(RowChipKind.MAIN), chipsOf(model))
        assertEquals(listOf(main, branch), orderMainFirst(listOf(branch, main)))
    }

    @Test
    fun `the branch is the main label and the folder is the secondary one`() {
        val model = model()

        assertEquals("feature/TASK-123", model.branchLabel)
        assertEquals("TASK-123", model.folderName)
        assertEquals("ShogunAi", model.originLabel)
    }

    @Test
    fun `a detached worktree shows the detached label`() {
        val model = model(worktree = worktree(branch = null))

        assertEquals("(detached)", model.branchLabel)
    }

    @Test
    fun `a locked worktree carries its chip`() {
        val model = model(worktree = worktree(isLocked = true))

        assertEquals(listOf(RowChipKind.LOCKED), chipsOf(model))
    }
}
