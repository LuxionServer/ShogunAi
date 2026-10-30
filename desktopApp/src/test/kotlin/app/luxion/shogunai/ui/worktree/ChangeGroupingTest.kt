package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.ui.multirepo.branchTypeOf
import app.luxion.shogunai.ui.multirepo.proposedAlias
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChangeGroupingTest {

    @Test
    fun `one worktree waiting for the user makes the whole group wait for the user`() {
        assertEquals(StateBadgeKind.WAITING_FOR_YOU, combinedState(listOf(StateBadgeKind.AGENT_WORKING, StateBadgeKind.WAITING_FOR_YOU)))
    }

    @Test
    fun `an agent working outranks unfinished work in the group`() {
        assertEquals(StateBadgeKind.AGENT_WORKING, combinedState(listOf(StateBadgeKind.UNFINISHED, StateBadgeKind.AGENT_WORKING)))
    }

    @Test
    fun `a partly merged group shows the state of the unmerged worktree`() {
        assertEquals(StateBadgeKind.STOPPED, combinedState(listOf(StateBadgeKind.MERGED, StateBadgeKind.STOPPED)))
        assertEquals(StateBadgeKind.INACTIVE, combinedState(listOf(StateBadgeKind.MERGED, StateBadgeKind.INACTIVE)))
    }

    @Test
    fun `a group is merged only when every worktree is`() {
        assertEquals(StateBadgeKind.MERGED, combinedState(listOf(StateBadgeKind.MERGED, StateBadgeKind.MERGED)))
    }

    @Test
    fun `the group title drops the branch prefix and the tag lists the other repositories`() {
        assertEquals("TASK-12", changeTitle("feature/TASK-12"))
        assertEquals("TASK-12 · +api", GroupTag("/home/dev/projects/product", "feature/TASK-12", listOf("api")).label)
        assertEquals("TASK-12 · +api +docs", GroupTag("/home/dev/projects/product", "feature/TASK-12", listOf("api", "docs")).label)
    }

    @Test
    fun `only feature and fix branches with a plain id can take more repositories`() {
        assertEquals(BranchType.FEATURE to "TASK-1", branchTypeOf("feature/TASK-1"))
        assertEquals(BranchType.FIX to "TASK-2", branchTypeOf("fix/TASK-2"))
        assertNull(branchTypeOf("bugfix/TASK-3"))
        assertNull(branchTypeOf("feature/team/TASK-4"))
    }

    @Test
    fun `aliases are proposed from the repository folder without the multirepo prefix`() {
        assertEquals("app", proposedAlias("product-app", "product"))
        assertEquals("docs", proposedAlias("Product-Docs", "product"))
        assertEquals("web-site", proposedAlias("web_site", "product"))
        assertEquals("product", proposedAlias("product", "product"))
    }
}
