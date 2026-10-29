package app.luxion.shogunai.ui.machine

import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.ui.i18n.EnglishStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PoolNoticesTest {

    private fun trim(id: Long, count: Int, limit: Int = 3) =
        PoolNotice(id, MachineResource.GRADLE_DAEMONS, PoolNoticeContent.Trimmed(count, limit))

    private fun release(id: Long, holder: String) =
        PoolNotice(id, MachineResource.GRADLE_DAEMONS, PoolNoticeContent.Released(ResourceKind.GRADLE_DAEMON, holder, "TASK-9"))

    @Test
    fun `repeated trims become one summary with every report in the details`() {
        val banner = poolNoticeBanner(listOf(trim(1, 1), trim(2, 2), trim(3, 1)), EnglishStrings)!!

        assertEquals("4 idle Gradle daemons were stopped to stay within the limit of 3", banner.summary)
        assertEquals(3, banner.details.size)
        assertEquals("1 idle Gradle daemon was stopped to stay within the limit of 3", banner.details.first())
    }

    @Test
    fun `releases and trims are summarized together in one banner`() {
        val banner = poolNoticeBanner(listOf(release(1, "api · TASK-1"), trim(2, 1), trim(3, 2)), EnglishStrings)!!

        assertEquals(
            listOf(
                "A Gradle daemon of api · TASK-1 was stopped to make room for TASK-9",
                "3 idle Gradle daemons were stopped to stay within the limit of 3",
            ),
            banner.summary.lines(),
        )
        assertEquals(3, banner.details.size)
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
