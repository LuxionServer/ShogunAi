package app.luxion.shogunai.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorktreeStatusTest {

    private fun statusWith(mainBranch: Divergence?) = WorktreeStatus(
        uncommittedChanges = 0,
        upstream = null,
        mainBranch = mainBranch,
        lastCommitAt = null,
    )

    @Test
    fun `a branch with nothing the main branch lacks and behind it is merged`() {
        assertTrue(statusWith(Divergence(ahead = 0, behind = 3)).isMerged)
    }

    @Test
    fun `a branch at the main branch tip is not merged`() {
        assertFalse(statusWith(Divergence(ahead = 0, behind = 0)).isMerged)
    }

    @Test
    fun `a branch with its own commits is not merged`() {
        assertFalse(statusWith(Divergence(ahead = 2, behind = 5)).isMerged)
    }

    @Test
    fun `without a main branch comparison nothing is merged`() {
        assertFalse(statusWith(null).isMerged)
    }
}
