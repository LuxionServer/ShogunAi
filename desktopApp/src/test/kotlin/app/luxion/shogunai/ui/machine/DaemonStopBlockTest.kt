package app.luxion.shogunai.ui.machine

import app.luxion.shogunai.domain.model.DaemonKind
import app.luxion.shogunai.domain.model.DaemonProcess
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.SpanishStrings
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class DaemonStopBlockTest {

    private val idle = DaemonState.Idle(Instant.parse("2026-01-01T00:00:00Z"))

    private fun daemon(pid: Long, state: DaemonState, kind: DaemonKind = DaemonKind.GRADLE) =
        MachineDaemon(DaemonProcess(pid, kind, "8.10", 0, null, state), owner = null)

    @Test
    fun `an idle Gradle daemon can be stopped`() {
        val daemons = listOf(daemon(1, idle))
        assertNull(daemons.single().stopBlock(daemons))
        assertNull(stopIdleBlock(daemons, stopping = false))
    }

    @Test
    fun `a busy or unknown Gradle daemon says why it can't be stopped`() {
        val busy = daemon(1, DaemonState.Busy)
        val unknown = daemon(2, DaemonState.Unknown)
        val daemons = listOf(busy, unknown)

        assertEquals(DaemonStopBlock.BUSY, busy.stopBlock(daemons))
        assertEquals(DaemonStopBlock.STATE_UNKNOWN, unknown.stopBlock(daemons))
        assertEquals(DaemonStopBlock.NONE_IDLE, stopIdleBlock(daemons, stopping = false))
    }

    @Test
    fun `a Kotlin daemon waits while a Gradle daemon is busy`() {
        val kotlin = daemon(3, DaemonState.Untracked, DaemonKind.KOTLIN)
        assertEquals(DaemonStopBlock.GRADLE_BUSY, kotlin.stopBlock(listOf(kotlin, daemon(1, DaemonState.Busy))))
        assertNull(kotlin.stopBlock(listOf(kotlin, daemon(1, idle))))
    }

    @Test
    fun `nothing can be stopped while daemons are being stopped`() {
        val daemons = listOf(daemon(1, idle))
        assertEquals(DaemonStopBlock.STOPPING, daemons.single().stopBlock(daemons, stopping = true))
        assertEquals(DaemonStopBlock.STOPPING, stopIdleBlock(daemons, stopping = true))
    }

    @Test
    fun `every reason is written in both languages`() {
        DaemonStopBlock.entries.forEach { block ->
            assertNotEquals(block.describe(EnglishStrings), block.describe(SpanishStrings))
        }
    }
}
