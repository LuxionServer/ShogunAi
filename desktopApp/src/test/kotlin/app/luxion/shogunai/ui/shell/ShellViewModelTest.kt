package app.luxion.shogunai.ui.shell

import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeShellPreferenceRepository
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShellViewModelTest {

    private val config = ProjectConfig(
        baseRepositoryPath = "~/projects/main-repo",
        worktreesRoot = "~/projects",
        secretFiles = emptyList(),
    )
    private val api = Project(id = "api", name = "api", config = config)
    private val mainRepo = Project(id = "main-repo", name = "main-repo", config = config)
    private val mobile = Project(id = "mobile", name = "Mobile", config = config)

    private val repository = FakeProjectRepository(initialProjects = listOf(mobile, mainRepo, api))
    private val preferences = FakeShellPreferenceRepository()

    private fun viewModel() = ShellViewModel(repository, preferences)

    @Test
    fun `lists projects alphabetically ignoring case`() {
        assertEquals(listOf(api, mainRepo, mobile), viewModel().projects)
    }

    @Test
    fun `a project saved by the previous version opens the view filtered to it`() {
        preferences.lastDestination = "project:mobile"

        assertEquals(ShellDestination.All(projectFilter = "mobile"), viewModel().destination)
    }

    @Test
    fun `restores all worktrees`() {
        preferences.lastDestination = "all"

        assertEquals(ShellDestination.All(), viewModel().destination)
    }

    @Test
    fun `restores active`() {
        preferences.lastDestination = "active"

        assertEquals(ShellDestination.Active, viewModel().destination)
    }

    @Test
    fun `selecting active saves it and allows creating a worktree`() {
        val viewModel = viewModel()
        viewModel.selectProject(api)

        viewModel.selectActive()

        assertEquals(ShellDestination.Active, viewModel.destination)
        assertEquals("active", preferences.lastDestination)
        assertTrue(viewModel.canCreateWorktree)
    }

    @Test
    fun `opens active without a saved destination`() {
        assertEquals(ShellDestination.Active, viewModel().destination)
    }

    @Test
    fun `falls back to active when the saved project was deleted`() {
        preferences.lastDestination = "project:deleted"

        assertEquals(ShellDestination.Active, viewModel().destination)
    }

    @Test
    fun `falls back to active when the saved value is unknown`() {
        preferences.lastDestination = "settings:api"

        assertEquals(ShellDestination.Active, viewModel().destination)
    }

    @Test
    fun `picking a filter saves it and all projects saves the unfiltered view`() {
        val viewModel = viewModel()

        viewModel.selectFilter("api")
        assertEquals(ShellDestination.All(projectFilter = "api"), viewModel.destination)
        assertEquals("project:api", preferences.lastDestination)

        viewModel.selectFilter(null)
        assertEquals(ShellDestination.All(), viewModel.destination)
        assertEquals("all", preferences.lastDestination)

        viewModel.selectProject(mobile)
        viewModel.selectAll()
        assertEquals(ShellDestination.All(), viewModel.destination)
    }

    @Test
    fun `opens on the empty state without projects`() {
        val viewModel = ShellViewModel(FakeProjectRepository(), preferences)

        assertTrue(viewModel.projects.isEmpty())
        assertNull(viewModel.destination)
    }

    @Test
    fun `selecting a project saves it as the last destination`() {
        val viewModel = viewModel()

        viewModel.selectProject(mobile)

        assertEquals(ShellDestination.All(projectFilter = "mobile"), viewModel.destination)
        assertEquals("project:mobile", preferences.lastDestination)
    }

    @Test
    fun `forms are never saved as the last destination`() {
        val viewModel = viewModel()
        viewModel.selectProject(mobile)

        viewModel.editProject(api)
        viewModel.addProject()

        assertEquals("project:mobile", preferences.lastDestination)
    }

    @Test
    fun `removing the filtered project goes back to active`() {
        val viewModel = viewModel()
        viewModel.selectProject(api)

        viewModel.removeProject(api)

        assertEquals(listOf(mainRepo, mobile), viewModel.projects)
        assertEquals(listOf(mobile, mainRepo), repository.list())
        assertEquals(ShellDestination.Active, viewModel.destination)
    }

    @Test
    fun `removing another project keeps the selection`() {
        val viewModel = viewModel()
        viewModel.selectProject(mobile)

        viewModel.removeProject(api)

        assertEquals(ShellDestination.All(projectFilter = "mobile"), viewModel.destination)
    }

    @Test
    fun `removing the last project shows the empty state`() {
        val viewModel = ShellViewModel(FakeProjectRepository(initialProjects = listOf(api)), preferences)

        viewModel.removeProject(api)

        assertTrue(viewModel.projects.isEmpty())
        assertNull(viewModel.destination)
    }

    @Test
    fun `cancel returns to the destination selected before the form`() {
        val viewModel = viewModel()
        viewModel.selectProject(mobile)
        viewModel.editProject(api)
        viewModel.addProject()

        viewModel.cancelForm()

        assertEquals(ShellDestination.All(projectFilter = "mobile"), viewModel.destination)
    }

    @Test
    fun `new worktree is requested only from a worktree view`() {
        val viewModel = viewModel()
        viewModel.selectProject(mobile)

        viewModel.requestNewWorktree()
        viewModel.addProject()
        viewModel.requestNewWorktree()

        assertEquals(1, viewModel.newWorktreeRequests)
        assertFalse(viewModel.canCreateWorktree)
    }

    @Test
    fun `cancel from the empty state returns to the empty state`() {
        val viewModel = ShellViewModel(FakeProjectRepository(), preferences)
        viewModel.addProject()

        viewModel.cancelForm()

        assertNull(viewModel.destination)
    }

    @Test
    fun `saving a project selects it`() {
        val viewModel = viewModel()
        viewModel.addProject()
        val created = Project(id = "web", name = "web", config = config)
        repository.save(created)

        viewModel.onProjectSaved(created, previous = null)

        assertEquals(ShellDestination.All(projectFilter = "web"), viewModel.destination)
        assertTrue(created in viewModel.projects)
    }

    @Test
    fun `saving a moved project passes the previous worktrees root`() {
        val viewModel = viewModel()
        viewModel.editProject(api)
        val moved = api.copy(config = config.copy(worktreesRoot = "~/elsewhere"))
        repository.save(moved)

        viewModel.onProjectSaved(moved, previous = api)

        assertEquals(ShellDestination.All("api", previousWorktreesRoot = "~/projects"), viewModel.destination)
    }

    @Test
    fun `refresh re-reads the catalog`() {
        val viewModel = viewModel()
        repository.save(Project(id = "web", name = "web", config = config))
        repository.delete(mobile.id)

        viewModel.refresh()

        assertEquals(listOf("api", "main-repo", "web"), viewModel.projects.map { it.id })
    }

    @Test
    fun `refresh moves away from a project deleted outside the app`() {
        val viewModel = viewModel()
        viewModel.selectProject(mobile)
        repository.delete(mobile.id)

        viewModel.refresh()

        assertEquals(ShellDestination.Active, viewModel.destination)
    }

    @Test
    fun `reload re-reads the catalog and asks the current view to reload`() {
        val viewModel = viewModel()
        repository.save(Project(id = "web", name = "web", config = config))

        viewModel.reload()

        assertEquals(1, viewModel.reloadRequests)
        assertTrue(viewModel.projects.any { it.id == "web" })
    }

    @Test
    fun `toggling the sidebar is remembered`() {
        val viewModel = viewModel()

        viewModel.toggleSidebar()

        assertTrue(viewModel.sidebarCollapsed)
        assertTrue(preferences.sidebarCollapsed)
        assertTrue(ShellViewModel(repository, preferences).sidebarCollapsed)
    }

    @Test
    fun `a narrow window shows the rail regardless of the saved choice`() {
        val viewModel = viewModel()

        viewModel.onWindowWidthChanged(narrow = true)

        assertTrue(viewModel.isRail)
    }

    @Test
    fun `toggling on a narrow window expands it for now without saving`() {
        val viewModel = viewModel()
        viewModel.onWindowWidthChanged(narrow = true)

        viewModel.toggleSidebar()

        assertFalse(viewModel.isRail)
        assertFalse(preferences.sidebarCollapsed)
    }

    @Test
    fun `widening the window goes back to the saved choice`() {
        preferences.sidebarCollapsed = true
        val viewModel = viewModel()
        viewModel.onWindowWidthChanged(narrow = true)
        viewModel.toggleSidebar()

        viewModel.onWindowWidthChanged(narrow = false)

        assertTrue(viewModel.isRail)
    }

    @Test
    fun `worktree actions are stored and cleared`() {
        val viewModel = viewModel()
        var found = 0
        var copied = 0
        var deleted = 0
        var cleared = 0

        viewModel.updateWorktreeActions(
            WorktreeActions(
                onFind = { found++ },
                onCopySelectedLaunchCommand = { copied++ },
                onDeleteSelectedWorktree = { deleted++ },
                onClearSelection = { cleared++ },
                onEscape = {},
            ),
        )
        val actions = viewModel.worktreeActions
        actions?.onFind?.invoke()
        actions?.onCopySelectedLaunchCommand?.invoke()
        actions?.onDeleteSelectedWorktree?.invoke()
        actions?.onClearSelection?.invoke()

        assertEquals(1, found)
        assertEquals(1, copied)
        assertEquals(1, deleted)
        assertEquals(1, cleared)

        viewModel.updateWorktreeActions(null)

        assertNull(viewModel.worktreeActions)
    }
}
