package app.luxion.shogunai.ui.machine

import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.toLocalClockTime
import app.luxion.shogunai.ui.i18n.EnglishStrings
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PoolNoticesTest {

    private val lastAt = Instant.parse("2026-01-01T10:42:00Z")
    private val lastTime = lastAt.toLocalClockTime()

    private fun trim(id: Long, count: Int, trims: Int, limit: Int = 3) =
        PoolNotice(id, MachineResource.GRADLE_DAEMONS, PoolNoticeContent.Trimmed(count, limit, trims, lastAt))

    private fun release(id: Long, holder: String) =
        PoolNotice(id, MachineResource.GRADLE_DAEMONS, PoolNoticeContent.Released(ResourceKind.GRADLE_DAEMON, holder, "TASK-9"))

    @Test
    fun `a single trim says when it happened`() {
        val banner = poolNoticeBanner(listOf(trim(1, 1, trims = 1)), EnglishStrings)!!

        assertEquals("1 idle Gradle daemon was stopped to stay within the limit of 3 at $lastTime", banner.summary)
        assertEquals(1, banner.details.size)
    }

    @Test
    fun `an accumulated trim reads as one line with its total, trims and last time`() {
        val banner = poolNoticeBanner(listOf(trim(1, 4, trims = 3)), EnglishStrings)!!

        assertEquals("4 idle Gradle daemons were stopped to stay within the limit of 3 · 3 times, last at $lastTime", banner.summary)
        assertEquals(1, banner.details.size)
    }

    @Test
    fun `releases and the accumulated trim are summarized together in one banner`() {
        val banner = poolNoticeBanner(listOf(release(1, "api · TASK-1"), trim(2, 3, trims = 2)), EnglishStrings)!!

        assertEquals(
            listOf(
                "A Gradle daemon of api · TASK-1 was stopped to make room for TASK-9",
                "3 idle Gradle daemons were stopped to stay within the limit of 3 · 2 times, last at $lastTime",
            ),
            banner.summary.lines(),
        )
        assertEquals(2, banner.details.size)
    }

    @Test
    fun `several releases are counted`() {
        val banner = poolNoticeBanner(listOf(release(1, "api · TASK-1"), release(2, "api · TASK-2")), EnglishStrings)!!

        assertEquals("2 Gradle daemons were stopped to make room for other worktrees", banner.summary)
    }

    @Test
    fun `no notices, no banner`() {
        assertNull(poolNoticeBanner(emptyList(), EnglishStrings))
    }
}
