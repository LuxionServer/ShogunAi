package app.luxion.shogunai.ui.actions

import app.luxion.shogunai.domain.model.RunnerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorktreeActionCatalogTest {

    private fun WorktreeCatalog.openTargets() = opens.map { (it.id as ActionId.Open).target }

    @Test
    fun `the default runner comes first and carries the open shortcut`() {
        val catalog = catalogFor(defaultRunner = RunnerId.ORCA)

        assertEquals(ActionId.Open(OpenTarget.ORCA), catalog.primary?.id)
        assertEquals(OpenShortcut, catalog.primary?.shortcut)
        assertNull(catalog.find(ActionId.Open(OpenTarget.HERDR))?.shortcut)
        assertEquals(
            listOf(OpenTarget.ORCA, OpenTarget.HERDR, OpenTarget.TERMINAL, OpenTarget.EDITOR, OpenTarget.FOLDER),
            catalog.openTargets(),
        )
    }

    @Test
    fun `a tool that isn't installed is hidden`() {
        val catalog = catalogFor(runners = listOf(RunnerId.HERDR), terminal = null, claudeDesktop = false)

        assertEquals(Availability.Hidden, catalog.find(ActionId.Open(OpenTarget.ORCA))?.availability)
        assertEquals(Availability.Hidden, catalog.find(ActionId.Open(OpenTarget.TERMINAL))?.availability)
        assertEquals(Availability.Hidden, catalog.find(ActionId.Open(OpenTarget.CLAUDE_DESKTOP))?.availability)
        assertFalse(OpenTarget.ORCA in catalog.openTargets())
    }

    @Test
    fun `without runners the primary action opens in the terminal, else the IDE, else the folder`() {
        assertEquals(ActionId.Open(OpenTarget.TERMINAL), catalogFor(runners = emptyList(), defaultRunner = null).primary?.id)
        assertEquals(ActionId.Open(OpenTarget.EDITOR), catalogFor(runners = emptyList(), defaultRunner = null, terminal = null).primary?.id)
        assertEquals(
            ActionId.Open(OpenTarget.FOLDER),
            catalogFor(runners = emptyList(), defaultRunner = null, terminal = null, editor = null, claudeDesktop = true).primary?.id,
        )
    }

    @Test
    fun `a missing folder disables the open actions with the reason`() {
        val catalog = catalogFor(isFolderMissing = true)

        assertTrue(catalog.opens.all { it.availability == Availability.Disabled("The worktree's folder is missing") })
        assertTrue(catalog.find(ActionId.CopyLaunchCommand)!!.isAvailable)
    }

    @Test
    fun `a runner that is opening the worktree is disabled until it finishes`() {
        val catalog = catalogFor(opening = setOf(OpenTarget.HERDR))

        assertEquals(Availability.Disabled("Opening…"), catalog.find(ActionId.Open(OpenTarget.HERDR))?.availability)
        assertTrue(catalog.find(ActionId.Open(OpenTarget.ORCA))!!.isAvailable)
    }

    @Test
    fun `the main worktree can't be deleted`() {
        assertEquals(Availability.Hidden, catalogFor(isMain = true).find(ActionId.RemoveWorktree)?.availability)
        val remove = catalogFor().find(ActionId.RemoveWorktree)!!
        assertTrue(remove.isAvailable)
        assertEquals(ActionGroup.DESTRUCTIVE, remove.group)
        assertEquals(ConfirmKind.REMOVE_WORKTREE, remove.confirm)
    }

    @Test
    fun `runners, the terminal and the launch command are agent-aware`() {
        val aware = catalogFor().actions.filter { it.agentAware }.map { it.id }

        assertEquals(
            listOf(ActionId.Open(OpenTarget.HERDR), ActionId.Open(OpenTarget.ORCA), ActionId.Open(OpenTarget.TERMINAL), ActionId.CopyLaunchCommand),
            aware,
        )
    }

    @Test
    fun `agents can be chosen only when the project has several`() {
        assertEquals(listOf("Claude", "Codex"), catalogFor().agentChoices.map { it.label })
        assertTrue(catalogFor(agents = listOf("Claude")).agentChoices.isEmpty())
    }

    @Test
    fun `every action has a scope, a group and a stable key`() {
        val actions = catalogFor().actions

        assertEquals(actions.size, actions.map { it.id.key }.toSet().size)
        assertTrue(actions.all { it.scope == ActionScope.WORKTREE || it.id == ActionId.SetDefaultRunner })
        assertEquals("worktree.open.claude-desktop", ActionId.Open(OpenTarget.CLAUDE_DESKTOP).key)
    }
}
