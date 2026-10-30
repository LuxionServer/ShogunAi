package app.luxion.shogunai.ui.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeShellPreferenceRepository
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.MultirepoDeclaration
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorkspaceSetting
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
        Multirepo(folder, MultirepoDeclaration(repos = mapOf("app" to "app", "api" to "api", "docs" to "docs"), worktreesRoot = "../product-worktrees"), approved = true),
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
    fun `a multirepo that needs review keeps its repositories as loose projects`() {
        known = known.map { it.copy(approved = false) }
        val viewModel = viewModel()

        val membership = viewModel.memberships.single()
        assertEquals(emptyMap(), membership.aliases)
        assertEquals(null, viewModel.multirepoOf(app))
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
        known = listOf(Multirepo(folder, MultirepoDeclaration(repos = mapOf("app" to "app"), worktreesRoot = "../w"), approved = true))
        val viewModel = viewModel()
        assertEquals(setOf("app"), viewModel.memberships.single().aliases.values.toSet())

        known = listOf(Multirepo(folder, MultirepoDeclaration(repos = mapOf("app" to "app", "docs" to "docs"), worktreesRoot = "../w"), approved = true))
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
                    waitingCount = 9,
                    totalWorktreeCount = 12,
                    activityOf = { ProjectActivity.NONE },
                    rail = false,
                    onSelectActive = {},
                    onSelectAll = {},
                    onSelectProject = {},
                    onEditProject = {},
                    onRemoveProject = {},
                    onAddProject = {},
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
    fun `a project with a running agent shows the agent icon next to its count`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Sidebar(
                    projects = listOf(api, tools),
                    selectedProjectId = null,
                    isActiveSelected = false,
                    isAllSelected = false,
                    waitingCount = 3,
                    totalWorktreeCount = 3,
                    activityOf = { if (it.id == api.id) ProjectActivity(2, listOf("claude")) else ProjectActivity(1, emptyList()) },
                    rail = false,
                    onSelectActive = {},
                    onSelectAll = {},
                    onSelectProject = {},
                    onEditProject = {},
                    onRemoveProject = {},
                    onAddProject = {},
                    onToggle = {},
                )
            }
        }

        val agent = onNodeWithContentDescription("Agents running: claude", useUnmergedTree = true)
            .assertWidthIsEqualTo(12.dp)
            .assertHeightIsEqualTo(12.dp)
            .getBoundsInRoot()
        val count = onNodeWithText("2", useUnmergedTree = true).getBoundsInRoot()
        assertTrue(agent.right <= count.left)
        assertEquals(1, onAllNodesWithContentDescription("Agents running", substring = true, useUnmergedTree = true).fetchSemanticsNodes().size)
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
                    waitingCount = 0,
                    totalWorktreeCount = 0,
                    activityOf = { ProjectActivity.NONE },
                    rail = false,
                    onSelectActive = {},
                    onSelectAll = {},
                    onSelectProject = {},
                    onEditProject = {},
                    onRemoveProject = {},
                    onAddProject = {},
                    onToggle = {},
                    multirepos = listOf(SidebarMultirepo(folder, "product", listOf(api, app), ProjectActivity.NONE, collapsed = true)),
                )
            }
        }

        onNodeWithText("product").assertIsDisplayed()
        onNodeWithText("tools").assertIsDisplayed()
        assertFalse(onAllNodesWithText("api").fetchSemanticsNodes().isNotEmpty())
    }

    private val workspaceProject = Project("workspace", "product", ProjectConfig(folder, "/home/dev/projects/product-worktrees/workspace", emptyList()))

    private fun workspaceMultirepo(workspace: WorkspaceSetting? = null) = Multirepo(
        folder,
        MultirepoDeclaration(repos = mapOf("app" to "app"), worktreesRoot = "../product-worktrees", workspace = workspace),
        folderIsRepository = true,
        approved = true,
    )

    @Test
    fun `the project of the multirepo folder is a member and not a loose project`() {
        repository.save(workspaceProject)
        known = listOf(workspaceMultirepo())

        val viewModel = viewModel()

        val membership = viewModel.memberships.single()
        assertEquals("workspace", membership.aliasOf("workspace"))
        assertEquals(membership, viewModel.multirepoOf(workspaceProject))
    }

    @Test
    fun `with the workspace turned off the folder's project stays a loose project`() {
        repository.save(workspaceProject)
        known = listOf(workspaceMultirepo(WorkspaceSetting(enabled = false)))

        val viewModel = viewModel()

        assertEquals(null, viewModel.multirepoOf(workspaceProject))
    }

    @Test
    fun `the workspace project is registered once when the multirepo loads`() {
        known = listOf(workspaceMultirepo())
        val registered = mutableListOf<String>()
        val viewModel = ShellViewModel(
            repository,
            preferences,
            multirepoCatalog = MultirepoCatalog { known },
            registerWorkspaceProject = { multirepo ->
                registered += multirepo.folder
                repository.save(workspaceProject)
                true
            },
        )

        assertEquals(listOf(folder), registered)
        assertEquals("workspace", viewModel.memberships.single().aliasOf("workspace"))
    }

    @Test
    fun `the workspace project of a multirepo that needs review isn't registered`() {
        known = listOf(workspaceMultirepo().copy(approved = false))
        val registered = mutableListOf<String>()

        ShellViewModel(
            repository,
            preferences,
            multirepoCatalog = MultirepoCatalog { known },
            registerWorkspaceProject = { multirepo -> registered += multirepo.folder; true },
        )

        assertTrue(registered.isEmpty())
    }

    @Test
    fun `the workspace project is labelled with its alias inside its multirepo`() {
        repository.save(workspaceProject)
        known = listOf(workspaceMultirepo())
        val viewModel = viewModel()
        val membership = viewModel.memberships.single()

        val labels = multirepoChildLabels(membership)

        assertEquals(mapOf("workspace" to "workspace"), labels)
        val folderEntry = sidebarEntries(
            viewModel.projects,
            listOf(SidebarMultirepo(folder, "product", listOf(workspaceProject, app), ProjectActivity.NONE, collapsed = false, labels = labels)),
        ).filterIsInstance<SidebarEntry.FolderEntry>().single()
        assertEquals(listOf("app", "workspace"), folderEntry.multirepo.projects.map(folderEntry.multirepo::labelOf))
    }

    @Test
    fun `a multirepo without a workspace repository keeps its projects' names`() {
        val viewModel = viewModel()

        assertEquals(emptyMap(), multirepoChildLabels(viewModel.memberships.single()))
    }

    @Test
    fun `the sidebar never shows the multirepo's name twice`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SidebarWith(
                    projects = listOf(workspaceProject, app, tools),
                    multirepo = SidebarMultirepo(
                        folder,
                        "product",
                        listOf(workspaceProject, app),
                        ProjectActivity.NONE,
                        collapsed = false,
                        labels = mapOf(workspaceProject.id to "workspace"),
                    ),
                )
            }
        }

        assertEquals(1, onAllNodesWithText("product").fetchSemanticsNodes().size)
        onNodeWithText("workspace").assertIsDisplayed()
        assertTrue(onAllNodesWithText("Machine").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `children start right of the multirepo's name and top-level projects align with it`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SidebarWith(
                    projects = listOf(api, app, tools),
                    multirepo = SidebarMultirepo(folder, "product", listOf(api, app), ProjectActivity.NONE, collapsed = false),
                )
            }
        }

        val folderLeft = onNodeWithText("product", useUnmergedTree = true).getBoundsInRoot().left
        assertEquals(folderLeft, onNodeWithText("tools", useUnmergedTree = true).getBoundsInRoot().left)
        assertTrue(onNodeWithText("api", useUnmergedTree = true).getBoundsInRoot().left > folderLeft)
        assertTrue(onNodeWithText("Projects", useUnmergedTree = true).getBoundsInRoot().left < folderLeft)
    }

    @Composable
    private fun SidebarWith(projects: List<Project>, multirepo: SidebarMultirepo) {
        Sidebar(
            projects = projects,
            selectedProjectId = null,
            isActiveSelected = false,
            isAllSelected = false,
            waitingCount = 0,
            totalWorktreeCount = 0,
            activityOf = { ProjectActivity.NONE },
            rail = false,
            onSelectActive = {},
            onSelectAll = {},
            onSelectProject = {},
            onEditProject = {},
            onRemoveProject = {},
            onAddProject = {},
            onToggle = {},
            multirepos = listOf(multirepo),
        )
    }
}
