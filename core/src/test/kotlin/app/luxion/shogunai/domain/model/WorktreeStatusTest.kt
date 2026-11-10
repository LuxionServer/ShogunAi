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

    @Test
    fun `a branch whose changes the main branch holds is merged into it and into the base that defaults to it`() {
        val status = statusWith(Divergence(ahead = 2, behind = 5)).copy(mainBranchHoldsChanges = true, baseHoldsChanges = true)

        assertTrue(status.isMergedIntoMainBranch)
        assertTrue(status.isMergedIntoBase)
        assertTrue(WorktreeStatus(0, null, Divergence(2, 5), null, mainBranchHoldsChanges = true).isMergedIntoBase)
    }

    @Test
    fun `a branch whose changes only its base holds is merged into the base alone`() {
        val status = statusWith(Divergence(ahead = 2, behind = 5)).copy(base = Divergence(ahead = 1, behind = 1), baseHoldsChanges = true)

        assertTrue(status.isMergedIntoBase)
        assertFalse(status.isMergedIntoMainBranch)
    }

    @Test
    fun `a branch never committed to is not merged even if the target holds its changes`() {
        assertFalse(statusWith(Divergence(ahead = 2, behind = 5)).copy(neverCommitted = true, mainBranchHoldsChanges = true, baseHoldsChanges = true).isMerged)
    }
}
