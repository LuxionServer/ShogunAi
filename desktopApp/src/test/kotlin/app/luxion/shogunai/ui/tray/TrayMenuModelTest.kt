package app.luxion.shogunai.ui.tray

import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.usecase.Activity
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.worktree.OpenKind
import app.luxion.shogunai.ui.worktree.WorktreeRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrayMenuModelTest {

    private val strings = EnglishStrings
    private val config = ProjectConfig(
        baseRepositoryPath = "~/projects/main-repo",
        worktreesRoot = "~/projects",
        secretFiles = emptyList(),
    )
    private val api = Project(id = "api", name = "api", config = config)
    private val mobile = Project(id = "mobile", name = "Mobile", config = config)
    private val ref = WorktreeRef("api", "~/projects/TASK-123")

    private fun worktree(
        activity: Activity = Activity.Stopped,
        isLoaded: Boolean = true,
        openActions: List<TrayOpenAction> = emptyList(),
        name: String = "feature/TASK-123",
    ) = TrayWorktree(ref, name, projectName = "api", activity, isLoaded, openActions)

    private fun menu(projects: List<Project> = emptyList(), worktrees: List<TrayWorktree> = emptyList()) =
        buildTrayMenu(strings, projects, worktrees)

    private fun submenuOf(menu: List<TrayEntry>) = menu.filterIsInstance<TrayEntry.Submenu>().single()

    @Test
    fun `without recents the menu offers the window, a new worktree, preferences and quit`() {
        assertEquals(
            listOf(
                TrayEntry.Item("Show ShogunAi", TrayAction.ShowWindow),
                TrayEntry.Item("New worktree…", TrayAction.NewWorktree),
                TrayEntry.Separator,
                TrayEntry.Item(strings.menuPreferences, TrayAction.Preferences),
                TrayEntry.Item(strings.menuQuit, TrayAction.Quit),
            ),
            menu(),
        )
    }

    @Test
    fun `recent projects follow their order and open their view`() {
        val entries = menu(projects = listOf(mobile, api))

        val projectItems = entries.filterIsInstance<TrayEntry.Item>().filter { it.action is TrayAction.OpenProject }
        assertEquals(
            listOf(
                TrayEntry.Item("Mobile", TrayAction.OpenProject("mobile")),
                TrayEntry.Item("api", TrayAction.OpenProject("api")),
            ),
            projectItems,
        )
        assertTrue(TrayEntry.Heading("Recent projects") in entries)
    }

    @Test
    fun `recent worktrees are listed under their heading with the branch and the project`() {
        val entries = menu(worktrees = listOf(worktree()))

        assertTrue(TrayEntry.Heading("Recent worktrees") in entries)
        assertEquals("feature/TASK-123 — api", submenuOf(entries).label)
    }

    @Test
    fun `a worktree submenu offers the same launch actions as the detail`() {
        val actions = listOf(
            TrayOpenAction(OpenKind.TERMINAL, "Open in iTerm2"),
            TrayOpenAction(OpenKind.EDITOR, "Open in VS Code"),
            TrayOpenAction(OpenKind.FOLDER, "Open folder"),
        )

        val submenu = submenuOf(menu(worktrees = listOf(worktree(openActions = actions))))

        assertEquals(
            listOf(
                TrayEntry.Item("Show in ShogunAi", TrayAction.ShowWorktree(ref)),
                TrayEntry.Item("Copy launch command", TrayAction.CopyLaunchCommand(ref)),
                TrayEntry.Item("Open in iTerm2", TrayAction.Open(ref, OpenKind.TERMINAL)),
                TrayEntry.Item("Open in VS Code", TrayAction.Open(ref, OpenKind.EDITOR)),
                TrayEntry.Item("Open folder", TrayAction.Open(ref, OpenKind.FOLDER)),
            ),
            submenu.entries,
        )
    }

    @Test
    fun `only the available launch actions are offered`() {
        val actions = listOf(TrayOpenAction(OpenKind.FOLDER, "Open folder"))

        val submenu = submenuOf(menu(worktrees = listOf(worktree(openActions = actions))))

        assertEquals(
            listOf("Show in ShogunAi", "Copy launch command", "Open folder"),
            submenu.entries.filterIsInstance<TrayEntry.Item>().map { it.label },
        )
    }

    @Test
    fun `a worktree of a project that hasn't loaded only offers to show it`() {
        val submenu = submenuOf(menu(worktrees = listOf(worktree(isLoaded = false, activity = Activity.Unknown))))

        assertEquals(listOf(TrayEntry.Item("Show in ShogunAi", TrayAction.ShowWorktree(ref))), submenu.entries)
        assertEquals("feature/TASK-123 — api", submenu.label)
    }

    @Test
    fun `a working agent marks the label with a dot`() {
        assertEquals("● feature/TASK-123 — api", trayWorktreeLabel(worktree(Activity.AgentWorking)))
    }

    @Test
    fun `an agent waiting for the user marks the label as needing attention`() {
        assertEquals("! feature/TASK-123 — api", trayWorktreeLabel(worktree(Activity.NeedsYou)))
    }

    @Test
    fun `other activities leave the label unmarked`() {
        listOf(Activity.New, Activity.Stopped, Activity.Merged, Activity.Inactive, Activity.Idle, Activity.Unknown).forEach {
            assertEquals("feature/TASK-123 — api", trayWorktreeLabel(worktree(it)))
        }
    }
}
