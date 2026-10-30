package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.Grouping
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeSort
import app.luxion.shogunai.ui.components.MenuEntry
import app.luxion.shogunai.ui.i18n.EnglishStrings
import app.luxion.shogunai.ui.i18n.SpanishStrings
import kotlin.test.Test
import kotlin.test.assertEquals

class ViewOptionsMenuTest {

    private val config = ProjectConfig(
        baseRepositoryPath = "~/projects/main-repo",
        worktreesRoot = "~/projects",
        secretFiles = emptyList(),
        agentLaunchConfig = AgentLaunchConfig(),
    )
    private val projects = listOf(
        Project(id = "api", name = "api", config = config),
        Project(id = "main-repo", name = "main-repo", config = config),
    )
    private val calls = mutableListOf<String>()
    private val handlers = ViewOptionsHandlers(
        onProject = { calls += "project:$it" },
        onGrouping = { calls += "grouping:$it" },
        onSort = { calls += "sort:$it" },
        onReload = { calls += "reload" },
    )

    private fun options(projects: List<Project>? = this.projects, selected: String? = null, groupings: List<Grouping> = listOf(Grouping.BY_STATE, Grouping.BY_PROJECT)) =
        ViewOptions(projects, selected, groupings, Grouping.BY_PROJECT, WorktreeSort.NAME)

    private fun List<MenuEntry>.describe(): List<String> = map {
        when (it) {
            is MenuEntry.Section -> "# ${it.title}"
            is MenuEntry.Item -> listOfNotNull(it.label, "✓".takeIf { _ -> it.checked }, it.shortcut).joinToString(" ")
            MenuEntry.Separator -> "---"
            is MenuEntry.Submenu -> "> ${it.label}"
        }
    }

    @Test
    fun `all worktrees offers the project, the grouping, the order and reload`() {
        val menu = viewOptionsMenu(options(), handlers, EnglishStrings, mac = true)

        assertEquals(
            listOf(
                "# Project", "All projects ✓", "api", "main-repo",
                "# Group", "By state", "By project ✓",
                "# Sort", "Name ✓", "Last activity",
                "---", "Reload ⌘R",
            ),
            menu.describe(),
        )
    }

    @Test
    fun `a project's view only offers the order and reload`() {
        val menu = viewOptionsMenu(options(projects = null, groupings = emptyList()), handlers, EnglishStrings, mac = false)

        assertEquals(listOf("# Sort", "Name ✓", "Last activity", "---", "Reload Ctrl+R"), menu.describe())
    }

    @Test
    fun `the picked project is checked instead of all projects`() {
        val menu = viewOptionsMenu(options(selected = "api"), handlers, EnglishStrings, mac = true)

        assertEquals(listOf("All projects", "api ✓", "main-repo"), menu.describe().subList(1, 4))
    }

    @Test
    fun `each item reports its choice`() {
        val items = viewOptionsMenu(options(), handlers, EnglishStrings).filterIsInstance<MenuEntry.Item>()

        items.forEach { it.onClick() }

        assertEquals(
            listOf("project:null", "project:api", "project:main-repo", "grouping:BY_STATE", "grouping:BY_PROJECT", "sort:NAME", "sort:ACTIVITY", "reload"),
            calls,
        )
    }

    @Test
    fun `the menu is translated and names the change grouping`() {
        val menu = viewOptionsMenu(options(projects = null, groupings = listOf(Grouping.BY_CHANGE, Grouping.BY_STATE)), handlers, SpanishStrings, mac = true)

        assertEquals(listOf("# Agrupar", "Por cambio", "Por estado", "# Orden", "Nombre ✓", "Última actividad", "---", "Recargar ⌘R"), menu.describe())
    }
}
