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

    @Test
    fun `the state is shown only when some daemon's state is known`() {
        assertEquals(false, showsDaemonState(listOf(daemon(1, DaemonState.Unknown), daemon(2, DaemonState.Untracked, DaemonKind.KOTLIN))))
        assertEquals(true, showsDaemonState(listOf(daemon(1, DaemonState.Unknown), daemon(2, idle))))
        assertEquals(true, showsDaemonState(listOf(daemon(1, DaemonState.Busy))))
    }

    @Test
    fun `stop idle says how many are busy or unknown when none is idle`() {
        val busy = (1L..9L).map { daemon(it, DaemonState.Busy) }
        assertEquals("All 9 are busy, none is idle", stopIdleReason(busy, DaemonStopBlock.NONE_IDLE, EnglishStrings))

        val unknown = (1L..4L).map { daemon(it, DaemonState.Unknown) }
        assertEquals("The state of all 4 is unknown, none can be stopped safely", stopIdleReason(unknown, DaemonStopBlock.NONE_IDLE, EnglishStrings))

        val mixed = listOf(daemon(1, DaemonState.Busy), daemon(2, DaemonState.Busy)) + (3L..5L).map { daemon(it, DaemonState.Unknown) }
        assertEquals("2 are busy and the state of 3 is unknown, none is idle", stopIdleReason(mixed, DaemonStopBlock.NONE_IDLE, EnglishStrings))
        assertEquals(
            "2 están ocupados y el estado de 3 es desconocido, ninguno está inactivo",
            stopIdleReason(mixed, DaemonStopBlock.NONE_IDLE, SpanishStrings),
        )
    }

    @Test
    fun `stop idle reasons read well for a single daemon`() {
        assertEquals("It's busy, it isn't idle", stopIdleReason(listOf(daemon(1, DaemonState.Busy)), DaemonStopBlock.NONE_IDLE, EnglishStrings))
        assertEquals(
            "Its state is unknown, it can't be stopped safely",
            stopIdleReason(listOf(daemon(1, DaemonState.Unknown)), DaemonStopBlock.NONE_IDLE, EnglishStrings),
        )
        assertEquals(
            "1 is busy and the state of 1 is unknown, none is idle",
            stopIdleReason(listOf(daemon(1, DaemonState.Busy), daemon(2, DaemonState.Unknown)), DaemonStopBlock.NONE_IDLE, EnglishStrings),
        )
        assertEquals("Está ocupado, no está inactivo", stopIdleReason(listOf(daemon(1, DaemonState.Busy)), DaemonStopBlock.NONE_IDLE, SpanishStrings))
        assertEquals(
            "Los 2 están ocupados, ninguno está inactivo",
            stopIdleReason(listOf(daemon(1, DaemonState.Busy), daemon(2, DaemonState.Busy)), DaemonStopBlock.NONE_IDLE, SpanishStrings),
        )
        assertEquals(
            "Su estado es desconocido, no se puede parar con seguridad",
            stopIdleReason(listOf(daemon(1, DaemonState.Unknown)), DaemonStopBlock.NONE_IDLE, SpanishStrings),
        )
        assertEquals(
            "El estado de los 2 es desconocido, ninguno se puede parar con seguridad",
            stopIdleReason(listOf(daemon(1, DaemonState.Unknown), daemon(2, DaemonState.Unknown)), DaemonStopBlock.NONE_IDLE, SpanishStrings),
        )
        assertEquals(
            "1 está ocupado y el estado de 1 es desconocido, ninguno está inactivo",
            stopIdleReason(listOf(daemon(1, DaemonState.Busy), daemon(2, DaemonState.Unknown)), DaemonStopBlock.NONE_IDLE, SpanishStrings),
        )
    }

    @Test
    fun `other blocks and kotlin-only lists keep their reason`() {
        val kotlin = listOf(daemon(1, DaemonState.Untracked, DaemonKind.KOTLIN))
        assertEquals("No daemon is idle", stopIdleReason(kotlin, DaemonStopBlock.NONE_IDLE, EnglishStrings))
        assertEquals(DaemonStopBlock.STOPPING.describe(EnglishStrings), stopIdleReason(kotlin, DaemonStopBlock.STOPPING, EnglishStrings))
    }

    @Test
    fun `the build cache notice names one project or groups several`() {
        assertNull(buildCacheNotice(emptyList(), EnglishStrings))
        assertEquals(
            BuildCacheNotice("main-repo: ${EnglishStrings.gradleBuildCacheOff}", emptyList()),
            buildCacheNotice(listOf("main-repo"), EnglishStrings),
        )
        val grouped = buildCacheNotice(listOf("api", "main-repo", "web"), EnglishStrings)
        assertEquals(listOf("api", "main-repo", "web"), grouped?.projects)
        assertEquals(true, grouped?.message?.startsWith("Gradle's build cache is off in 3 projects"))
        assertEquals(true, buildCacheNotice(listOf("api", "web"), SpanishStrings)?.message?.startsWith("La caché de build de Gradle está desactivada en 2 proyectos"))
    }
}
