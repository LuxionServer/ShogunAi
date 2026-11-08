package app.luxion.shogunai.ui.machine

import app.luxion.shogunai.domain.model.ComposeResourceKind
import app.luxion.shogunai.domain.model.DaemonKind
import app.luxion.shogunai.domain.model.DaemonProcess
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.ui.i18n.EnglishStrings
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MachineListActionsTest {

    private val strings = EnglishStrings
    private val idle = DaemonState.Idle(Instant.parse("2026-10-28T12:57:00Z"))

    @Test
    fun `none idle reason counts busy and unknown daemons`() {
        assertEquals(strings.stopIdleBlockedNoneIdle, noneIdleReason(emptyList(), strings))
        assertEquals("All 3 are busy, none is idle", noneIdleReason(List(3) { DaemonState.Busy }, strings))
        assertEquals(strings.stopIdleAllUnknown(2), noneIdleReason(listOf(DaemonState.Unknown, DaemonState.Untracked), strings))
        assertEquals(strings.stopIdleBusyAndUnknown(2, 3), noneIdleReason(List(2) { DaemonState.Busy } + List(3) { DaemonState.Unknown }, strings))
    }

    @Test
    fun `a maven daemon can be stopped only when idle and nothing is being stopped`() {
        assertNull(mavenStopBlock(idle, stopping = false))
        assertEquals(DaemonStopBlock.STOPPING, mavenStopBlock(idle, stopping = true))
        assertEquals(DaemonStopBlock.BUSY, mavenStopBlock(DaemonState.Busy, stopping = false))
        assertEquals(DaemonStopBlock.STATE_UNKNOWN, mavenStopBlock(DaemonState.Unknown, stopping = false))
        assertEquals(DaemonStopBlock.STATE_UNKNOWN, mavenStopBlock(DaemonState.Untracked, stopping = false))
    }

    @Test
    fun `stopping idle maven daemons is blocked while stopping or with none idle`() {
        assertNull(mavenStopIdleBlock(listOf(idle, DaemonState.Busy), stopping = false, strings))
        assertEquals(ListActionBlock(strings.stopIdleBlockedStopping, running = true), mavenStopIdleBlock(listOf(idle), stopping = true, strings))
        assertEquals(
            ListActionBlock(strings.stopIdleBusyAndUnknown(1, 1), running = false),
            mavenStopIdleBlock(listOf(DaemonState.Busy, DaemonState.Unknown), stopping = false, strings),
        )
    }

    @Test
    fun `deleting orphaned derived data is blocked while deleting or without orphans`() {
        assertNull(derivedDataDeleteBlock(orphans = 2, deleting = false, strings))
        assertEquals(ListActionBlock(strings.derivedDataDeleteBlockedDeleting, running = true), derivedDataDeleteBlock(2, deleting = true, strings))
        assertEquals(ListActionBlock("No folder is orphaned", running = false), derivedDataDeleteBlock(0, deleting = false, strings))
    }

    @Test
    fun `deleting orphaned simulators is blocked only while deleting`() {
        assertNull(simulatorOrphansDeleteBlock(deleting = false, strings))
        assertEquals(ListActionBlock(strings.simulatorOrphansDeleteBlockedDeleting, running = true), simulatorOrphansDeleteBlock(deleting = true, strings))
    }

    @Test
    fun `taking down compose projects is blocked while running or without removed worktrees`() {
        assertNull(composeTakeDownBlock(removedProjects = 1, takingDown = false, strings))
        assertEquals(ListActionBlock(strings.composeTakeDownBlockedRunning, running = true), composeTakeDownBlock(1, takingDown = true, strings))
        assertEquals(ListActionBlock(strings.composeTakeDownBlockedNone, running = false), composeTakeDownBlock(0, takingDown = false, strings))
    }

    @Test
    fun `removing compose leftovers is blocked while removing or with only volumes`() {
        val mixed = listOf(ComposeResourceKind.NETWORK, ComposeResourceKind.VOLUME)
        val removing = ListActionBlock(strings.composeLeftoversBlockedRemoving, running = true)
        assertNull(composeLeftoversBlock(mixed, removing = false, strings))
        assertEquals(removing, composeLeftoversBlock(mixed, removing = true, strings))
        assertEquals(
            ListActionBlock(strings.composeLeftoversBlockedOnlyVolumes, running = false),
            composeLeftoversBlock(listOf(ComposeResourceKind.VOLUME), removing = false, strings),
        )
        assertNull(composeVolumesBlock(removing = false, strings))
        assertEquals(removing, composeVolumesBlock(removing = true, strings))
    }

    @Test
    fun `the gradle stop idle block is running only while stopping`() {
        val busy = MachineDaemon(DaemonProcess(1, DaemonKind.GRADLE, "8.10", 0, null, DaemonState.Busy), null)
        assertEquals(ListActionBlock("It's busy, it isn't idle", running = false), stopIdleListBlock(listOf(busy), stopping = false, strings))
        assertEquals(ListActionBlock(strings.stopIdleBlockedStopping, running = true), stopIdleListBlock(listOf(busy), stopping = true, strings))
    }
}
