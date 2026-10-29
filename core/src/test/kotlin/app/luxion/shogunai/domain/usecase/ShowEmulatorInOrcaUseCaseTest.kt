package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeOrcaClient
import app.luxion.shogunai.domain.model.OrcaEmulatorView
import app.luxion.shogunai.domain.model.OrcaWorktree
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.io.OrcaClient
import app.luxion.shogunai.domain.model.OrcaWorkspace
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShowEmulatorInOrcaUseCaseTest {

    private val worktree = "/home/dev/projects/worktrees/TASK-8"
    private val listed = listOf(OrcaWorktree(worktree, createdByOrca = false))

    private fun useCase(orca: FakeOrcaClient, samePath: (String, String) -> Boolean = { first, second -> first == second }) =
        ShowEmulatorInOrcaUseCase(orca, samePath)

    @Test
    fun `a worktree open in Orca shows its emulator`() = runTest {
        val orca = FakeOrcaClient(psRows = listed)

        assertEquals(OrcaEmulatorView.Shown, useCase(orca)(worktree, "emulator-5556"))
        assertEquals(listOf(worktree to "emulator-5556"), orca.attachedEmulators)
    }

    @Test
    fun `a worktree open in Orca shows its simulator by UDID`() = runTest {
        val orca = FakeOrcaClient(psRows = listed)

        assertEquals(OrcaEmulatorView.Shown, useCase(orca)(worktree, SIMULATOR_UDID) { false })
        assertEquals(listOf(worktree to SIMULATOR_UDID), orca.attachedEmulators)
    }

    @Test
    fun `a device is skipped while the worktree's other device runs`() = runTest {
        val orca = FakeOrcaClient(psRows = listed)

        assertEquals(OrcaEmulatorView.Skipped, useCase(orca)(worktree, "emulator-5556") { true })
        assertTrue(orca.attachedEmulators.isEmpty())
    }

    @Test
    fun `a worktree Orca doesn't list is not open even when the other device runs`() = runTest {
        val orca = FakeOrcaClient(psRows = emptyList())
        var looked = false

        val view = useCase(orca)(worktree, "emulator-5556") {
            looked = true
            true
        }

        assertEquals(OrcaEmulatorView.NotOpen, view)
        assertFalse(looked)
    }

    @Test
    fun `an other device lookup that fails counts as not running`() = runTest {
        val orca = FakeOrcaClient(psRows = listed)

        val view = useCase(orca)(worktree, SIMULATOR_UDID) { throw IllegalStateException("adb is gone") }

        assertEquals(OrcaEmulatorView.Shown, view)
        assertEquals(listOf(worktree to SIMULATOR_UDID), orca.attachedEmulators)
    }

    @Test
    fun `Orca closed is neither started nor asked to attach`() = runTest {
        val orca = FakeOrcaClient(running = false, psRows = listed)

        assertEquals(OrcaEmulatorView.NotOpen, useCase(orca)(worktree, "emulator-5556"))
        assertEquals(0, orca.starts)
        assertEquals(0, orca.psReads)
        assertTrue(orca.attachedEmulators.isEmpty())
    }

    @Test
    fun `a worktree Orca doesn't list isn't attached`() = runTest {
        val orca = FakeOrcaClient(psRows = listOf(OrcaWorktree("/home/dev/projects/worktrees/TASK-9", createdByOrca = false)))

        assertEquals(OrcaEmulatorView.NotOpen, useCase(orca)(worktree, "emulator-5556"))
        assertTrue(orca.attachedEmulators.isEmpty())
    }

    @Test
    fun `Orca not installed is not open`() = runTest {
        val orca = FakeOrcaClient(installed = false)

        assertEquals(OrcaEmulatorView.NotOpen, useCase(orca)(worktree, "emulator-5556"))
    }

    @Test
    fun `an attach Orca rejects fails with its message`() = runTest {
        val rejection = WorktreeError.OrcaCommandFailed(listOf("orca"), "Simulator emulator-5556 not found.")
        val orca = FakeOrcaClient(psRows = listed, attachFailure = rejection)

        assertEquals(OrcaEmulatorView.Failed("Simulator emulator-5556 not found."), useCase(orca)(worktree, "emulator-5556"))
    }

    @Test
    fun `a failing status fails instead of throwing`() = runTest {
        val orca = FakeOrcaClient(psRows = listed, statusFailure = WorktreeError.OrcaCommandFailed(listOf("orca"), "Orca 1.4.200 is too old"))

        assertEquals(OrcaEmulatorView.Failed("Orca 1.4.200 is too old"), useCase(orca)(worktree, "emulator-5556"))
    }

    @Test
    fun `the worktree path is matched after resolving symbolic links`() = runTest {
        val orca = FakeOrcaClient(psRows = listOf(OrcaWorktree("/private/tmp/worktrees/TASK-8", createdByOrca = false)))
        val canonical = { path: String -> path.replace(Regex("^/tmp/"), "/private/tmp/") }

        val view = useCase(orca) { first, second -> canonical(first) == canonical(second) }("/tmp/worktrees/TASK-8", "emulator-5556")

        assertEquals(OrcaEmulatorView.Shown, view)
        assertEquals(listOf("/tmp/worktrees/TASK-8" to "emulator-5556"), orca.attachedEmulators)
    }

    @Test
    fun `an Orca that doesn't answer in time fails naming the wait`() = runTest {
        val silent = object : OrcaClient by FakeOrcaClient(psRows = listed) {
            override suspend fun attachEmulator(workspace: OrcaWorkspace, device: String) = awaitCancellation()
        }

        assertEquals(OrcaEmulatorView.Failed("Orca didn't answer within 30 seconds"), ShowEmulatorInOrcaUseCase(silent)(worktree, "emulator-5556"))
    }
}

private const val SIMULATOR_UDID = "8D2C51A4-3F0B-4C6E-9A1D-5B7E2F90C3A1"
