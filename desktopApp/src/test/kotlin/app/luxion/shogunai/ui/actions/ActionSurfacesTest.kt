package app.luxion.shogunai.ui.actions

import app.luxion.shogunai.domain.model.RunnerId
import app.luxion.shogunai.ui.i18n.EnglishStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ActionSurfacesTest {

    private val strings = EnglishStrings
    private val catalog = catalogFor()

    private fun List<ActionEntry>.runs(): List<ActionEntry.Run> = flatMap {
        when (it) {
            is ActionEntry.Run -> listOf(it)
            is ActionEntry.Nested -> listOfNotNull(it.run) + it.entries.runs()
            else -> emptyList()
        }
    }

    @Test
    fun `open with lists the default runner first and marked, then the other tools, copy and the agents`() {
        val entries = catalog.openWithEntries(strings)

        val first = assertIs<ActionEntry.Nested>(entries.first())
        assertEquals("Open in Herdr", first.label)
        assertTrue(first.run!!.marked)
        assertEquals(listOf("Claude", "Codex"), first.entries.map { (it as ActionEntry.Run).label })
        assertTrue(ActionEntry.Heading("With agent") in entries)
        val withAgent = entries.dropWhile { it != ActionEntry.Heading("With agent") }.drop(1).map { it as ActionEntry.Run }
        assertEquals(listOf("Claude" to true, "Codex" to false), withAgent.map { it.label to it.checked })
    }

    @Test
    fun `with one agent the runners are plain items and there is no agent section`() {
        val entries = catalogFor(agents = listOf("Claude")).openWithEntries(strings)

        assertTrue(entries.none { it is ActionEntry.Nested })
        assertTrue(entries.none { it is ActionEntry.Heading })
    }

    @Test
    fun `the overflow and the context menu end with the destructive action after a separator`() {
        listOf(catalog.overflowEntries(), catalog.contextMenuEntries(strings)).forEach { entries ->
            assertEquals(ActionEntry.Divider, entries[entries.size - 2])
            assertEquals(ActionId.RemoveWorktree, (entries.last() as ActionEntry.Run).spec.id)
        }
        assertTrue(catalogFor(isMain = true).overflowEntries().none { it is ActionEntry.Divider })
    }

    @Test
    fun `the context menu opens in the default runner and nests open with`() {
        val entries = catalog.contextMenuEntries(strings)

        assertEquals(
            listOf("Open in Herdr", "Open with", "Copy launch command", "Copy path"),
            entries.take(4).map { if (it is ActionEntry.Nested) it.label else (it as ActionEntry.Run).label },
        )
        assertEquals(catalog.openWithEntries(strings), (entries[1] as ActionEntry.Nested).entries)
    }

    @Test
    fun `the icons are copy launch command, the terminal and the folder`() {
        assertEquals(
            listOf(ActionId.CopyLaunchCommand, ActionId.Open(OpenTarget.TERMINAL), ActionId.Open(OpenTarget.FOLDER)),
            catalog.iconActions.map { it.id },
        )
        assertEquals(2, catalogFor(terminal = null).iconActions.size)
    }

    @Test
    fun `every surface takes its labels and availability from the same catalog`() {
        val catalog = catalogFor(defaultRunner = RunnerId.ORCA, opening = setOf(OpenTarget.HERDR))
        val specs = catalog.actions.associateBy { it.id }

        val surfaces = catalog.openWithEntries(strings).runs().map { it.spec } +
            catalog.contextMenuEntries(strings).runs().map { it.spec } +
            catalog.overflowEntries().runs().map { it.spec } +
            catalog.paletteEntries(strings).map { it.spec } +
            catalog.trayEntries().let { listOfNotNull(it.primary, it.copyLaunchCommand) + it.openWith } +
            catalog.menuBarEntries() +
            catalog.iconActions

        surfaces.forEach { assertEquals(specs.getValue(it.id), it) }
        assertEquals("Open in Orca", catalog.menuBarEntries().first().label)
        assertTrue(catalog.paletteEntries(strings).none { it.spec.id == ActionId.Open(OpenTarget.HERDR) })
        assertTrue(catalog.trayEntries().openWith.none { it.id == ActionId.Open(OpenTarget.HERDR) })
    }

    @Test
    fun `the palette has no destructive or menu-only actions`() {
        val ids = catalog.paletteEntries(strings).map { it.spec.id }

        assertTrue(ActionId.RemoveWorktree !in ids)
        assertTrue(ActionId.OpenWith !in ids)
        assertTrue(ids.none { it is ActionId.ChooseAgent })
        assertTrue(ActionId.CopyPath in ids)
    }

    @Test
    fun `the view header keeps one primary and moves clean up last in the overflow`() {
        val view = viewCatalog(
            ViewActionContext(isMultirepo = true, isProjectView = false, createBlocked = null, cleanUpBlocked = "Nothing to clean up"),
            strings,
        )

        assertEquals(ActionId.NewChange, view.primary?.id)
        val overflow = viewOverflow(view)
        assertEquals(listOf(ActionId.OpenMultirepoAgent, ActionId.CleanUp), overflow.filterIsInstance<ActionEntry.Run>().map { it.spec.id })
        assertEquals(ActionEntry.Divider, overflow[1])
        assertEquals(Availability.Disabled("Nothing to clean up"), view.find(ActionId.CleanUp)?.availability)
    }

    @Test
    fun `a project view has no multirepo actions and creates worktrees`() {
        val view = viewCatalog(
            ViewActionContext(isMultirepo = false, isProjectView = true, createBlocked = null, cleanUpBlocked = null),
            strings,
        )

        assertEquals(ActionId.NewWorktree, view.primary?.id)
        assertEquals(listOf(ActionId.CleanUp), view.overflow.map { it.id })
        assertTrue(view.find(ActionId.ProjectSettings)!!.isVisible)
        assertEquals(Availability.Hidden, view.find(ActionId.OpenAllInHerdr)?.availability)
    }

    @Test
    fun `shortcut labels follow the platform`() {
        assertEquals("⇧⌘O", shortcutLabel(OpenWithShortcut, mac = true))
        assertEquals("Ctrl+Shift+O", shortcutLabel(OpenWithShortcut, mac = false))
        assertEquals("Ctrl+Backspace", shortcutLabel(RemoveWorktreeShortcut, mac = false))
    }
}
