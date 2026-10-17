package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.model.Divergence
import app.luxion.shogunai.domain.model.WorktreeStatus
import app.luxion.shogunai.ui.i18n.EnglishStrings
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class StatusSegmentsTest {

    private val now = Instant.parse("2026-10-16T12:00:00Z")

    private fun segments(status: WorktreeStatus, mainBranchName: String? = "develop") =
        statusSegments(status, mainBranchName, now, EnglishStrings)

    @Test
    fun `shows changes, divergences and the last commit date`() {
        val status = WorktreeStatus(
            uncommittedChanges = 3,
            upstream = Divergence(ahead = 2, behind = 0),
            mainBranch = Divergence(ahead = 4, behind = 1),
            lastCommitAt = now.minus(Duration.ofDays(2)),
        )

        assertEquals(listOf("3 changes", "↑2 upstream", "↑4 ↓1 develop", "2 days ago"), segments(status))
    }

    @Test
    fun `a clean worktree in sync shows only its last commit date`() {
        val status = WorktreeStatus(
            uncommittedChanges = 0,
            upstream = Divergence(ahead = 0, behind = 0),
            mainBranch = Divergence(ahead = 0, behind = 0),
            lastCommitAt = now.minus(Duration.ofHours(3)),
        )

        assertEquals(listOf("3 hours ago"), segments(status))
    }

    @Test
    fun `a merged branch leaves its main branch divergence to the merged label`() {
        val status = WorktreeStatus(
            uncommittedChanges = 0,
            upstream = null,
            mainBranch = Divergence(ahead = 0, behind = 5),
            lastCommitAt = null,
        )

        assertEquals(emptyList(), segments(status))
    }
}
