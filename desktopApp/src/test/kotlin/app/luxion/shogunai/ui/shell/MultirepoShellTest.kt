package app.luxion.shogunai.ui.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeShellPreferenceRepository
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.MultirepoDeclaration
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.ui.multirepo.MultirepoCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MultirepoShellTest {

    private fun project(name: String) =
        Project(id = name, name = name, config = ProjectConfig("/home/dev/projects/product/$name", "/home/dev/projects/worktrees", emptyList()))

    private val app = project("app")
    private val api = project("api")
    private val docs = project("docs")
    private val tools = Project(id = "tools", name = "tools", config = ProjectConfig("/home/dev/projects/tools", "/home/dev/projects/worktrees", emptyList()))
    private val folder = "/home/dev/projects/product"
    private var known = listOf(
        Multirepo(folder, MultirepoDeclaration(repos = mapOf("app" to "app", "api" to "api", "docs" to "docs"), worktreesRoot = "../product-worktrees")),
    )
    private val repository = FakeProjectRepository(initialProjects = listOf(tools, docs, app, api))
    private val preferences = FakeShellPreferenceRepository()

    private fun viewModel() = ShellViewModel(repository, preferences, multirepoCatalog = MultirepoCatalog { known })

    @Test
    fun `the projects of a multirepo are its members`() {
        val viewModel = viewModel()

        val membership = viewModel.memberships.single()
        assertEquals("product", membership.name)
        assertEquals(mapOf("app" to "app", "api" to "api", "docs" to "docs"), membership.aliases)
        assertEquals(membership, viewModel.multirepoOf(app))
        assertEquals(null, viewModel.multirepoOf(tools))
    }

    @Test
    fun `selecting a multirepo opens its view and is restored after a restart`() {
        val viewModel = viewModel()

        viewModel.selectMultirepo(folder)

        assertEquals(ShellDestination.MultirepoView(folder), viewModel.destination)
        assertEquals(ShellDestination.MultirepoView(folder), viewModel().destination)
    }

    @Test
    fun `a collapsed multirepo stays collapsed after a restart`() {
        viewModel().toggleMultirepoCollapsed(folder)

        assertTrue(folder in viewModel().collapsedMultirepos)
    }

    @Test
    fun `a deleted multirepo falls back and its projects stay`() {
        val viewModel = viewModel()
        viewModel.selectMultirepo(folder)
        known = emptyList()

        viewModel.onMultirepoDeleted(folder)

        assertEquals(ShellDestination.Active, viewModel.destination)
        assertTrue(viewModel.memberships.isEmpty())
        assertEquals(4, viewModel.projects.size)
    }

    @Test
    fun `a repository added from the command line shows after the next refresh`() {
        known = listOf(Multirepo(folder, MultirepoDeclaration(repos = mapOf("app" to "app"), worktreesRoot = "../w")))
        val viewModel = viewModel()
        assertEquals(setOf("app"), viewModel.memberships.single().aliases.values.toSet())

        known = listOf(Multirepo(folder, MultirepoDeclaration(repos = mapOf("app" to "app", "docs" to "docs"), worktreesRoot = "../w")))
        viewModel.refresh()

        assertEquals(setOf("app", "docs"), viewModel.memberships.single().aliases.values.toSet())
    }

    @Test
    fun `the sidebar nests a multirepo's projects in its folder in alphabetical order`() {
        val folderEntry = SidebarMultirepo(folder, "product", listOf(docs, app, api), ProjectActivity.NONE, collapsed = false)

        val entries = sidebarEntries(listOf(tools, docs, app, api), listOf(folderEntry))

        assertEquals(listOf("product", "tools"), entries.map { it.name })
        assertEquals(listOf("api", "app", "docs"), (entries.first() as SidebarEntry.FolderEntry).multirepo.projects.map { it.name })
    }

    @Test
    fun `activity of a multirepo sums its projects`() {
        val total = ProjectActivity(2, emptyList()) + ProjectActivity(1, listOf("codex"))

        assertEquals(ProjectActivity(3, listOf("codex")), total)
    }

    @Test
    fun `the sidebar shows the folder with its count and selects it`() = runComposeUiTest {
        var selected: String? = null
        var toggled: String? = null
        var collapsed = false
        setContent {
            MaterialTheme {
                Sidebar(
                    projects = listOf(api, app, docs, tools),
                    selectedProjectId = null,
                    isActiveSelected = false,
                    isAllSelected = false,
                    activeCount = 9,
                    totalWorktreeCount = 12,
                    activityOf = { ProjectActivity.NONE },
                    machineResources = emptyList(),
                    machineSummaries = emptyMap(),
                    selectedMachine = null,
                    rail = false,
                    onSelectActive = {},
                    onSelectAll = {},
                    onSelectProject = {},
                    onEditProject = {},
                    onRemoveProject = {},
                    onAddProject = {},
                    onSelectMachine = {},
                    onToggle = {},
                    multirepos = listOf(
                        SidebarMultirepo(folder, "product", listOf(api, app, docs), ProjectActivity(3, listOf("codex")), collapsed = collapsed),
                    ),
                    onSelectMultirepo = { selected = it },
                    onToggleMultirepo = { toggled = it },
                )
            }
        }

        onNodeWithText("product").assertIsDisplayed()
        onNodeWithText("docs").assertIsDisplayed()
        onNodeWithText("tools").assertIsDisplayed()
        onNodeWithText("3").assertIsDisplayed()
        onNodeWithText("product").performClick()
        assertEquals(folder, selected)
        onNodeWithContentDescription("Collapse").performClick()
        assertEquals(folder, toggled)
    }

    @Test
    fun `a collapsed folder hides its projects`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Sidebar(
                    projects = listOf(api, app, tools),
                    selectedProjectId = null,
                    isActiveSelected = false,
                    isAllSelected = false,
                    activeCount = 0,
                    totalWorktreeCount = 0,
                    activityOf = { ProjectActivity.NONE },
                    machineResources = emptyList(),
                    machineSummaries = emptyMap(),
                    selectedMachine = null,
                    rail = false,
                    onSelectActive = {},
                    onSelectAll = {},
                    onSelectProject = {},
                    onEditProject = {},
                    onRemoveProject = {},
                    onAddProject = {},
                    onSelectMachine = {},
                    onToggle = {},
                    multirepos = listOf(SidebarMultirepo(folder, "product", listOf(api, app), ProjectActivity.NONE, collapsed = true)),
                )
            }
        }

        onNodeWithText("product").assertIsDisplayed()
        onNodeWithText("tools").assertIsDisplayed()
        assertFalse(onAllNodesWithText("api").fetchSemanticsNodes().isNotEmpty())
    }
}
