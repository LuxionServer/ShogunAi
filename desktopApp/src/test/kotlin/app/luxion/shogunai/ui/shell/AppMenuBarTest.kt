package app.luxion.shogunai.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.FrameWindowScope
import app.luxion.shogunai.ui.actions.ActionGroup
import app.luxion.shogunai.ui.actions.ActionIcon
import app.luxion.shogunai.ui.actions.ActionId
import app.luxion.shogunai.ui.actions.ActionScope
import app.luxion.shogunai.ui.actions.ActionShortcut
import app.luxion.shogunai.ui.actions.ActionSpec
import app.luxion.shogunai.ui.actions.Availability
import app.luxion.shogunai.ui.actions.OpenTarget
import app.luxion.shogunai.ui.i18n.EnglishStrings
import org.junit.Assume.assumeFalse
import java.awt.GraphicsEnvironment
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class AppMenuBarTest {

    private val strings = EnglishStrings
    private val calls = mutableListOf<String>()
    private val windows = mutableListOf<ComposeWindow>()

    private val open = ActionSpec(
        ActionId.Open(OpenTarget.FOLDER), ActionScope.WORKTREE, ActionGroup.OPEN, "Open folder", ActionIcon.FOLDER,
        shortcut = ActionShortcut("O"),
    )
    private val unmapped = ActionSpec(
        ActionId.CopyPath, ActionScope.WORKTREE, ActionGroup.COPY, "Copy path", ActionIcon.PATH,
        shortcut = ActionShortcut("P"),
    )
    private val remove = ActionSpec(
        ActionId.RemoveWorktree, ActionScope.WORKTREE, ActionGroup.DESTRUCTIVE, "Remove worktree", ActionIcon.DELETE,
        availability = Availability.Disabled("Busy"), shortcut = ActionShortcut("⌫"),
    )

    @BeforeTest
    fun requireDisplay() {
        assumeFalse(GraphicsEnvironment.isHeadless())
    }

    @AfterTest
    fun closeWindows() {
        SwingUtilities.invokeAndWait { windows.forEach(ComposeWindow::dispose) }
    }

    private fun record(name: String): () -> Unit = { calls += name }

    @Composable
    private fun FrameWindowScope.MenuBar(
        mac: Boolean,
        everything: Boolean,
        hasSelection: Boolean = true,
        isEditingText: Boolean = false,
        worktreeMenu: List<ActionSpec> = listOf(open, unmapped, remove),
        clearsSelection: Boolean = everything,
    ) = AppMenuBar(
        strings = strings,
        onAddProject = record("add project"),
        onSave = record("save").takeIf { everything },
        onNewWorktree = record("new worktree").takeIf { everything },
        onFind = record("find").takeIf { everything },
        worktreeMenu = worktreeMenu.takeIf { everything },
        onWorktreeAction = { calls += "run $it" },
        onClearSelection = record("clear selection").takeIf { clearsSelection },
        hasWorktreeSelection = hasSelection,
        isEditingText = isEditingText,
        onShowActive = record("active").takeIf { everything },
        onShowAll = record("all").takeIf { everything },
        onReload = record("reload"),
        onCommandPalette = record("palette"),
        onToggleSidebar = record("sidebar"),
        onPreferences = record("preferences"),
        onCheckForUpdates = record("updates"),
        onTour = record("tour"),
        onCheckInstallation = record("installation"),
        onCopyDiagnosticReport = record("diagnostics"),
        onAbout = record("about"),
        onQuit = record("quit"),
        mac = mac,
    )

    private fun render(content: @Composable FrameWindowScope.() -> Unit): JMenuBar {
        lateinit var window: ComposeWindow
        SwingUtilities.invokeAndWait {
            window = ComposeWindow()
            windows += window
            window.setContent(content = content)
            window.addNotify()
        }
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            var bar: JMenuBar? = null
            SwingUtilities.invokeAndWait { bar = window.jMenuBar?.takeIf { it.menuCount > 0 } }
            bar?.let { return it }
            Thread.sleep(20)
        }
        fail("The menu bar was never composed")
    }

    private fun JMenuBar.menus(): List<JMenu> = (0 until menuCount).map(::getMenu)

    private fun JMenu.labels(): List<String> = (0 until itemCount).map { getItem(it)?.text ?: "—" }

    private fun JMenuBar.item(label: String): JMenuItem =
        menus().flatMap { menu -> (0 until menu.itemCount).mapNotNull(menu::getItem) }.single { it.text == label }

    private fun JMenuBar.click(vararg labels: String) = SwingUtilities.invokeAndWait { labels.forEach { item(it).doClick() } }

    @Test
    fun `elsewhere than macOS every menu is shown and every item reaches the app`() {
        val bar = render { MenuBar(mac = false, everything = true) }

        assertEquals(listOf(strings.menuFile, strings.menuWorktree, strings.menuView, strings.menuHelp), bar.menus().map { it.text })
        assertEquals(listOf(strings.newWorktree, strings.menuSave, strings.addProject, "—", strings.menuPreferences, "—", strings.menuQuit), bar.menus()[0].labels())
        assertEquals(listOf("Open folder", "Copy path", "—", "Remove worktree", strings.clearSelection), bar.menus()[1].labels())
        assertEquals(
            listOf(strings.menuActive, strings.menuAllWorktrees, strings.menuFind, strings.menuCommandPalette, strings.reload, strings.menuToggleSidebar),
            bar.menus()[2].labels(),
        )
        assertEquals(listOf(strings.tourMenuItem, strings.menuCheckForUpdates, strings.menuCheckInstallation, strings.menuCopyDiagnosticReport, strings.menuAbout), bar.menus()[3].labels())

        bar.click(
            strings.newWorktree, strings.menuSave, strings.addProject, strings.menuPreferences, strings.menuQuit, "Open folder", "Copy path", strings.clearSelection,
            strings.menuActive, strings.menuAllWorktrees, strings.menuFind, strings.menuCommandPalette, strings.reload, strings.menuToggleSidebar,
            strings.tourMenuItem, strings.menuCheckForUpdates, strings.menuCheckInstallation, strings.menuCopyDiagnosticReport, strings.menuAbout,
        )

        assertEquals(
            listOf(
                "new worktree", "save", "add project", "preferences", "quit", "run ${open.id}", "run ${unmapped.id}", "clear selection",
                "active", "all", "find", "palette", "reload", "sidebar", "tour", "updates", "installation", "diagnostics", "about",
            ),
            calls,
        )
        assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK), bar.item(strings.newWorktree).accelerator)
        assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK), bar.item("Open folder").accelerator)
        assertNull(bar.item("Copy path").accelerator)
        assertFalse(bar.item("Remove worktree").isEnabled)
    }

    @Test
    fun `on macOS the app menu holds settings, quit and about, and shortcuts use the command key`() {
        val bar = render { MenuBar(mac = true, everything = false) }

        assertEquals(listOf(strings.menuFile, strings.menuView, strings.menuHelp), bar.menus().map { it.text })
        assertEquals(listOf(strings.addProject), bar.menus()[0].labels())
        assertEquals(listOf(strings.menuCommandPalette, strings.reload, strings.menuToggleSidebar), bar.menus()[1].labels())
        assertEquals(listOf(strings.tourMenuItem, strings.menuCheckForUpdates, strings.menuCheckInstallation, strings.menuCopyDiagnosticReport), bar.menus()[2].labels())
        assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.META_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK), bar.item(strings.addProject).accelerator)
    }

    @Test
    fun `the worktree actions are disabled while typing or without a selection`() {
        val typing = render { MenuBar(mac = true, everything = true, isEditingText = true) }
        val unselected = render { MenuBar(mac = true, everything = true, hasSelection = false) }

        assertFalse(typing.item("Open folder").isEnabled)
        assertTrue(typing.item(strings.clearSelection).isEnabled)
        assertFalse(unselected.item("Open folder").isEnabled)
        assertFalse(unselected.item(strings.clearSelection).isEnabled)
    }

    @Test
    fun `a worktree action without a shortcut has none, and the menu can go without clearing the selection`() {
        val copy = ActionSpec(ActionId.CopyLaunchCommand, ActionScope.WORKTREE, ActionGroup.COPY, "Copy launch command", ActionIcon.COPY)

        val bar = render { MenuBar(mac = false, everything = true, worktreeMenu = listOf(copy), clearsSelection = false) }

        assertEquals(listOf("Copy launch command"), bar.menus()[1].labels())
        assertNull(bar.item("Copy launch command").accelerator)
    }
}
