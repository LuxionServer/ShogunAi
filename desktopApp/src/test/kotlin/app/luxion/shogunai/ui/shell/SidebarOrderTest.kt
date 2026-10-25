package app.luxion.shogunai.ui.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeShellPreferenceRepository
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.MultirepoDeclaration
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.SidebarOrder
import app.luxion.shogunai.ui.multirepo.MultirepoCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SidebarOrderTest {

    private fun project(name: String, folder: String = "/home/dev/projects") =
        Project(id = name, name = name, config = ProjectConfig("$folder/$name", "/home/dev/projects/worktrees", emptyList()))

    private val productFolder = "/home/dev/projects/product"
    private val api = project("api")
    private val mainRepo = project("main-repo")
    private val mobile = project("mobile")
    private val app = project("app", productFolder)
    private val docs = project("docs", productFolder)
    private val web = project("web", productFolder)
    private val product = Multirepo(productFolder, MultirepoDeclaration(repos = mapOf("app" to "app", "docs" to "docs", "web" to "web"), worktreesRoot = "../w"), approved = true)
    private val repository = FakeProjectRepository(initialProjects = listOf(api, mainRepo, mobile, app, docs, web))
    private val preferences = FakeShellPreferenceRepository()
    private var known = listOf(product)
    private var forgotten = 0

    private fun viewModel() = ShellViewModel(
        repository,
        preferences,
        multirepoCatalog = MultirepoCatalog { known },
        forgetMissingMultirepos = { forgotten++ },
    )

    private fun ShellViewModel.topLevelNames(): List<String> =
        sidebarKeys(SidebarLevel.TopLevel).map { it.substringAfter(':').substringAfterLast('/') }

    @Test
    fun `without a saved order the levels are alphabetical`() {
        assertEquals(listOf("api", "main-repo", "mobile", "product"), viewModel().topLevelNames())
        assertEquals(listOf("app", "docs", "web"), viewModel().sidebarKeys(SidebarLevel.Folder(productFolder)))
    }

    @Test
    fun `moving down saves the whole level and survives a restart`() {
        viewModel().moveSidebarEntry(SidebarLevel.TopLevel, projectSidebarKey("api"), 1)

        assertEquals(listOf("main-repo", "api", "mobile", "product"), viewModel().topLevelNames())
        assertEquals(
            listOf(projectSidebarKey("main-repo"), projectSidebarKey("api"), projectSidebarKey("mobile"), multirepoSidebarKey(productFolder)),
            preferences.sidebarOrder.topLevel,
        )
    }

    @Test
    fun `a move beyond the level is clamped to its edge`() {
        val viewModel = viewModel()

        viewModel.moveSidebarEntry(SidebarLevel.TopLevel, multirepoSidebarKey(productFolder), -5)

        assertEquals(listOf("product", "api", "main-repo", "mobile"), viewModel.topLevelNames())
    }

    @Test
    fun `projects inside a folder move only within it`() {
        val viewModel = viewModel()

        viewModel.moveSidebarEntry(SidebarLevel.Folder(productFolder), "web", 0)

        assertEquals(listOf("web", "app", "docs"), viewModel.sidebarKeys(SidebarLevel.Folder(productFolder)))
        assertEquals(listOf("api", "main-repo", "mobile", "product"), viewModel.topLevelNames())
    }

    @Test
    fun `sorting one level alphabetically keeps the order of the others`() {
        val viewModel = viewModel()
        viewModel.moveSidebarEntry(SidebarLevel.TopLevel, projectSidebarKey("mobile"), 0)
        viewModel.moveSidebarEntry(SidebarLevel.Folder(productFolder), "web", 0)

        viewModel.sortSidebarAlphabetically(SidebarLevel.TopLevel)

        assertEquals(listOf("api", "main-repo", "mobile", "product"), viewModel.topLevelNames())
        assertEquals(listOf("web", "app", "docs"), viewModel.sidebarKeys(SidebarLevel.Folder(productFolder)))
        assertTrue(preferences.sidebarOrder.topLevel.isEmpty())
    }

    @Test
    fun `new entries go after the ordered ones and removed ones are forgotten`() {
        val ordered = listOf(projectSidebarKey("mobile"), projectSidebarKey("api"), projectSidebarKey("main-repo"))
        val order = SidebarOrder(topLevel = ordered + projectSidebarKey("gone"))
        val billing = project("billing")
        val zeta = project("zeta")

        val entries = sidebarEntries(listOf(api, mainRepo, mobile, zeta, billing), emptyList(), order)

        assertEquals(listOf("mobile", "api", "main-repo", "billing", "zeta"), entries.map { it.name })
    }

    @Test
    fun `a re-added project with a new id goes to the end`() {
        val order = SidebarOrder(topLevel = listOf(projectSidebarKey("api"), projectSidebarKey("mobile"), projectSidebarKey("main-repo")))
        val readded = api.copy(id = "api-2")

        val entries = sidebarEntries(listOf(readded, mainRepo, mobile), emptyList(), order)

        assertEquals(listOf("mobile", "main-repo", "api"), entries.map { it.name })
    }

    @Test
    fun `reloading forgets the multirepos whose folder is gone and their projects return to the top level`() {
        val viewModel = viewModel()
        known = emptyList()

        viewModel.refresh()

        assertEquals(2, forgotten)
        assertTrue(viewModel.memberships.isEmpty())
        assertEquals(listOf("api", "app", "docs", "main-repo", "mobile", "web"), viewModel.topLevelNames())
    }

    @Test
    fun `a tall block takes a place once its leading edge passes a neighbour's middle`() {
        val state = SidebarDragState().apply {
            keys = listOf("mobile", "folder", "api")
            place("mobile", 0f, 30f)
            place("folder", 30f, 90f)
            place("api", 120f, 30f)
        }

        state.start("folder")
        state.drag(-20f)
        assertEquals(0, state.targetIndex())

        state.drag(30f)
        assertEquals(1, state.targetIndex())

        state.drag(30f)
        assertEquals(2, state.targetIndex())
    }

    private fun SemanticsNodeInteractionsProvider.texts(vararg names: String): List<Float> =
        names.map { onNodeWithText(it).fetchSemanticsNode().positionInRoot.y }

    @Test
    fun `the context menu moves an entry within its level and hides the move at its edge`() = runComposeUiTest {
        val viewModel = viewModel()
        setContent { MaterialTheme { SidebarUnderTest(viewModel) } }

        onNodeWithText("api").performMouseInput { rightClick() }
        assertTrue(onAllNodesWithText("Move up").fetchSemanticsNodes().isEmpty())
        onNodeWithText("Move down").performClick()
        waitForIdle()

        assertEquals(listOf("main-repo", "api", "mobile", "product"), viewModel.topLevelNames())
        val (mainRepoY, apiY) = texts("main-repo", "api")
        assertTrue(mainRepoY < apiY)

        onNodeWithText("api").performMouseInput { rightClick() }
        onNodeWithText("Sort alphabetically").performClick()
        waitForIdle()
        assertEquals(listOf("api", "main-repo", "mobile", "product"), viewModel.topLevelNames())
    }

    @Test
    fun `dragging a row moves it and a click without moving selects it`() = runComposeUiTest {
        val viewModel = viewModel()
        var selected: String? = null
        setContent { MaterialTheme { SidebarUnderTest(viewModel) { selected = it.name } } }
        val (apiY, mobileY) = texts("api", "mobile")

        onNodeWithText("mobile").performMouseInput {
            moveTo(center)
            press()
            repeat(10) { moveBy(Offset(0f, (apiY - mobileY - 20f) / 10)) }
            release()
        }
        waitForIdle()

        assertEquals(listOf("mobile", "api", "main-repo", "product"), viewModel.topLevelNames())
        assertEquals(null, selected)

        onNodeWithText("main-repo").performClick()
        assertEquals("main-repo", selected)
        assertEquals(listOf("mobile", "api", "main-repo", "product"), viewModel.topLevelNames())
    }

    @Composable
    private fun SidebarUnderTest(viewModel: ShellViewModel, onSelect: (Project) -> Unit = {}) {
        var collapsed by remember { mutableStateOf(false) }
        Sidebar(
            projects = viewModel.projects,
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
            onSelectProject = onSelect,
            onEditProject = {},
            onRemoveProject = {},
            onAddProject = {},
            onSelectMachine = {},
            onToggle = {},
            multirepos = viewModel.memberships.map { membership ->
                SidebarMultirepo(membership.folder, membership.name, viewModel.projects.filter { it.id in membership.aliases }, ProjectActivity.NONE, collapsed)
            },
            onToggleMultirepo = { collapsed = !collapsed },
            order = viewModel.sidebarOrder,
            onMoveEntry = viewModel::moveSidebarEntry,
            onSortLevel = viewModel::sortSidebarAlphabetically,
        )
    }
}
