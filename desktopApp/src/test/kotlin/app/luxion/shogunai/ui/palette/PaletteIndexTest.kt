package app.luxion.shogunai.ui.palette

import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.machine.MachineResource
import app.luxion.shogunai.ui.shell.ShellDestination
import app.luxion.shogunai.ui.worktree.OpenAction
import app.luxion.shogunai.ui.worktree.OpenKind
import app.luxion.shogunai.ui.worktree.PaletteWorktree
import app.luxion.shogunai.ui.worktree.WorktreeRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaletteIndexTest {

    private val config = ProjectConfig(
        baseRepositoryPath = "~/projects/main-repo",
        worktreesRoot = "~/projects",
        secretFiles = emptyList(),
    )
    private val api = Project(id = "api", name = "api", config = config)
    private val mainRepo = Project(id = "main-repo", name = "main-repo", config = config)
    private val task9 = Worktree(path = "~/projects/api-TASK-9", branch = "feature/TASK-9")

    private fun index(
        projects: List<Project> = listOf(api, mainRepo),
        worktrees: List<PaletteWorktree> = listOf(PaletteWorktree(api, task9, null)),
        machine: List<MachineResource> = listOf(MachineResource.GRADLE_DAEMONS),
        canCleanUp: Boolean = true,
    ) = buildPaletteIndex(projects, worktrees, machine, canCleanUp, EnglishStrings) { key, shift -> "${if (shift) "S" else ""}$key" }

    @Test
    fun `the selected worktree's open actions are listed and run on the selection`() {
        val actions = listOf(
            OpenAction(OpenKind.TERMINAL, "Open in iTerm2") {},
            OpenAction(OpenKind.FOLDER, "Open folder") {},
        )

        val items = buildPaletteIndex(listOf(api), emptyList(), emptyList(), false, EnglishStrings, actions) { key, _ -> key }

        assertEquals(
            listOf(PaletteAction.OpenSelected(OpenKind.TERMINAL), PaletteAction.OpenSelected(OpenKind.FOLDER)),
            items.filter { it.title.startsWith("Open") }.map { it.primary },
        )
        assertTrue(index().none { it.primary is PaletteAction.OpenSelected })
    }

    @Test
    fun `every kind of item is present`() {
        val kinds = index().map { it.kind }.toSet()

        assertEquals(PaletteKind.entries.toSet(), kinds)
    }

    @Test
    fun `lists the views, each project, its worktrees and the actions`() {
        val titles = index().map { it.title }

        listOf(
            "Active", "All worktrees", "Gradle daemons", "Preferences", "api", "main-repo", "feature/TASK-9",
            "Add project", "New worktree in api", "Settings of main-repo", "Clean up", "Reload", "Toggle sidebar",
        ).forEach { assertTrue(it in titles, "missing $it in $titles") }
    }

    @Test
    fun `machine entries are only listed when visible`() {
        assertTrue(index(machine = emptyList()).none { it.machineResource != null })
        assertTrue(index().any { it.machineResource == MachineResource.GRADLE_DAEMONS })
    }

    @Test
    fun `clean up is only offered when there is something to clean`() {
        assertFalse(index(canCleanUp = false).any { it.title == "Clean up" })
    }

    @Test
    fun `without projects only the preferences view and the app actions remain`() {
        val items = index(projects = emptyList(), worktrees = emptyList(), machine = emptyList(), canCleanUp = false)

        assertEquals(listOf("Preferences", "Add project", "Toggle sidebar"), items.map { it.title })
    }

    @Test
    fun `a worktree selects it and copies its launch command with the secondary action`() {
        val worktree = index().first { it.kind == PaletteKind.WORKTREE }
        val ref = WorktreeRef("api", task9.path)

        assertEquals(PaletteAction.SelectWorktree(ref), worktree.primary)
        assertEquals(PaletteAction.CopyLaunchCommand(ref), worktree.secondary)
        assertEquals("api", worktree.context)
    }

    @Test
    fun `a project opens its filtered view and offers new worktree and settings as separate results`() {
        val items = index()

        assertEquals(PaletteAction.Open(ShellDestination.All(projectFilter = "api")), items.first { it.id == "project:api" }.primary)
        assertEquals(PaletteAction.NewWorktreeIn("api"), items.first { it.id == "action:new-worktree:api" }.primary)
        assertEquals(PaletteAction.Open(ShellDestination.ProjectSettings("api")), items.first { it.id == "action:settings:api" }.primary)
    }

    @Test
    fun `no item deletes anything`() {
        val items = index()

        assertTrue(items.none { it.title.contains("delete", ignoreCase = true) || it.title.contains("remove", ignoreCase = true) })
        assertEquals(emptyList(), rankPaletteItems(items, "delete", emptyList()))
    }

    @Test
    fun `recent items that no longer exist are dropped`() {
        val items = index()

        assertEquals(listOf("project:api"), pruneRecent(listOf("worktree:gone", "project:api", "project:api"), items))
    }

    @Test
    fun `ids are unique`() {
        val ids = index().map { it.id }

        assertEquals(ids.size, ids.toSet().size)
    }
}
