package app.luxion.shogunai.ui.shell

import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeShellPreferenceRepository
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.model.KnownMultirepos
import app.luxion.shogunai.domain.model.Multirepo
import app.luxion.shogunai.domain.model.MultirepoDeclaration
import app.luxion.shogunai.domain.model.MultirepoUnavailability
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.SidebarOrder
import app.luxion.shogunai.domain.model.UnavailableMultirepo
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.ui.machine.MachineResource
import app.luxion.shogunai.ui.multirepo.MultirepoCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShellViewModelStatesTest {

    private fun project(id: String, base: String = "~/projects/$id", root: String = "~/projects") =
        Project(id = id, name = id, config = ProjectConfig(baseRepositoryPath = base, worktreesRoot = root, secretFiles = emptyList()))

    private val api = project("api")
    private val mobile = project("mobile")
    private val folder = "/home/dev/projects/product"
    private val lostFolder = "/home/dev/projects/lost"
    private var known = emptyList<Multirepo>()
    private var unavailable = emptyList<UnavailableMultirepo>()
    private val forgotten = mutableListOf<String>()
    private val repository = FakeProjectRepository(initialProjects = listOf(api, mobile))
    private val preferences = FakeShellPreferenceRepository()

    private fun viewModel(projects: ProjectRepository = repository, settingsProblem: WorktreeError.LocalFileUnreadable? = null) = ShellViewModel(
        projects,
        preferences,
        readSettingsProblem = { settingsProblem },
        multirepoCatalog = MultirepoCatalog { KnownMultirepos(known, unavailable) },
        forgetMultirepo = { forgotten += it },
    )

    private fun productMultirepo() =
        Multirepo(folder, MultirepoDeclaration(repos = mapOf("api" to "api"), worktreesRoot = "../product-worktrees"), approved = true)

    @Test
    fun `a refresh keeps the place a form returns to while it still exists`() {
        val viewModel = viewModel()
        viewModel.selectProject(api)
        viewModel.editProject(mobile)

        viewModel.refresh()
        viewModel.cancelForm()

        assertEquals(ShellDestination.All(projectFilter = "api"), viewModel.destination)
    }

    @Test
    fun `a form whose origin is removed returns to the fallback`() {
        val viewModel = viewModel()
        viewModel.selectProject(api)
        viewModel.editProject(mobile)

        repository.delete("api")
        viewModel.refresh()
        viewModel.cancelForm()

        assertEquals(ShellDestination.Active, viewModel.destination)
    }

    @Test
    fun `a form opened without a previous place returns to the fallback`() {
        preferences.lastDestination = null
        val viewModel = ShellViewModel(FakeProjectRepository(), preferences)

        viewModel.addProject()
        viewModel.cancelForm()

        assertNull(viewModel.destination)
    }

    @Test
    fun `a form whose origin disappeared while it was open returns to the fallback`() {
        known = listOf(productMultirepo())
        val viewModel = viewModel()
        viewModel.selectMultirepo(folder)
        viewModel.editProject(api)

        known = emptyList()
        viewModel.onProjectSaved(api, previous = api)
        viewModel.cancelForm()

        assertEquals(ShellDestination.Active, viewModel.destination)
    }

    @Test
    fun `a new project opens its worktrees with nothing to repair`() {
        val viewModel = viewModel()

        viewModel.onProjectSaved(api, previous = null)

        assertEquals(ShellDestination.All(projectFilter = "api"), viewModel.destination)
    }

    @Test
    fun `saving is only requested while the form can be saved`() {
        val viewModel = viewModel()

        viewModel.requestSaveSettings()
        assertEquals(0, viewModel.saveSettingsRequests)
        viewModel.updateCanSaveSettings(true)
        viewModel.requestSaveSettings()

        assertEquals(1, viewModel.saveSettingsRequests)
    }

    @Test
    fun `confirming a leave nobody asked for does nothing`() {
        val viewModel = viewModel()
        viewModel.selectAll()

        viewModel.confirmLeave()

        assertEquals(ShellDestination.All(), viewModel.destination)
    }

    @Test
    fun `selecting a machine resource opens it and is restored`() {
        viewModel().selectMachine(MachineResource.GRADLE_DAEMONS)

        assertEquals("machine:GRADLE_DAEMONS", preferences.lastDestination)
        assertEquals(ShellDestination.Machine(MachineResource.GRADLE_DAEMONS), viewModel().destination)
    }

    @Test
    fun `a machine resource isn't restored without projects`() {
        preferences.lastDestination = "machine:GRADLE_DAEMONS"

        assertNull(viewModel(FakeProjectRepository()).destination)
    }

    @Test
    fun `an unknown folder has no sidebar entries`() {
        assertEquals(emptyList(), viewModel().sidebarKeys(SidebarLevel.Folder("/home/dev/projects/unknown")))
    }

    @Test
    fun `moving an entry to its own place saves the level once and then nothing more`() {
        val viewModel = viewModel()
        val keys = viewModel.sidebarKeys(SidebarLevel.TopLevel)

        viewModel.moveSidebarEntry(SidebarLevel.TopLevel, keys.first(), 0)
        assertEquals(keys, preferences.sidebarOrder.topLevel)
        preferences.sidebarOrder = SidebarOrder()
        viewModel.moveSidebarEntry(SidebarLevel.TopLevel, keys.first(), 0)

        assertEquals(SidebarOrder(), preferences.sidebarOrder)
    }

    @Test
    fun `adding a multirepo asks for it, can be dismissed and opens the one added`() {
        val viewModel = viewModel()

        viewModel.addMultirepo(folder)
        assertEquals(AddMultirepoRequest(folder), viewModel.addMultirepoRequest)
        viewModel.dismissAddMultirepo()
        assertNull(viewModel.addMultirepoRequest)
        viewModel.addMultirepo()
        known = listOf(productMultirepo())
        viewModel.onMultirepoAdded(folder)

        assertNull(viewModel.addMultirepoRequest)
        assertEquals(ShellDestination.MultirepoView(folder), viewModel.destination)
        assertEquals(listOf(folder), viewModel.memberships.map { it.folder })
    }

    @Test
    fun `a changed multirepo is read again`() {
        val viewModel = viewModel()
        known = listOf(productMultirepo())

        viewModel.onMultirepoChanged()

        assertEquals(listOf(folder), viewModel.memberships.map { it.folder })
    }

    @Test
    fun `confirming a removal nobody asked for forgets nothing`() {
        viewModel().confirmMultirepoRemoval()

        assertTrue(forgotten.isEmpty())
    }

    @Test
    fun `removing a collapsed unavailable multirepo also forgets that it was collapsed`() {
        unavailable = listOf(UnavailableMultirepo(lostFolder, MultirepoUnavailability.FolderUnreachable))
        val viewModel = viewModel()
        viewModel.toggleMultirepoCollapsed(lostFolder)

        viewModel.requestMultirepoRemoval(lostFolder)
        unavailable = emptyList()
        viewModel.confirmMultirepoRemoval()

        assertEquals(listOf(lostFolder), forgotten)
        assertTrue(lostFolder !in preferences.collapsedMultirepos)
    }

    @Test
    fun `deleting a collapsed multirepo forgets that it was collapsed and returns to the fallback`() {
        known = listOf(productMultirepo())
        val viewModel = viewModel()
        viewModel.toggleMultirepoCollapsed(folder)
        viewModel.editMultirepo(folder)

        known = emptyList()
        viewModel.onMultirepoDeleted(folder)

        assertTrue(folder !in preferences.collapsedMultirepos)
        assertEquals(ShellDestination.Active, viewModel.destination)
    }

    @Test
    fun `a project saved without moving opens its worktrees with nothing to repair`() {
        val viewModel = viewModel()

        viewModel.onProjectSaved(api, previous = api)

        assertEquals(ShellDestination.All(projectFilter = "api"), viewModel.destination)
    }

    @Test
    fun `a project whose repository or worktrees folder moved opens its worktrees to repair them`() {
        val viewModel = viewModel()

        viewModel.onProjectSaved(project("api", base = "~/elsewhere/api"), previous = api)
        assertEquals(ShellDestination.All("api", previousWorktreesRoot = "~/projects"), viewModel.destination)
        viewModel.onProjectSaved(project("api", root = "~/elsewhere"), previous = api)
        assertEquals(ShellDestination.All("api", previousWorktreesRoot = "~/projects"), viewModel.destination)
    }

    @Test
    fun `remembering the fetch for an unknown project saves nothing`() {
        viewModel().rememberFetchBeforeCreate("unknown", fetch = true)

        assertEquals(listOf(api, mobile), repository.list())
    }

    @Test
    fun `the project of a destination is the one it references`() {
        val viewModel = viewModel()

        assertEquals(api, viewModel.projectFor(ShellDestination.ProjectSettings("api")))
        assertNull(viewModel.projectFor(ShellDestination.ProjectSettings("unknown")))
        assertNull(viewModel.projectFor(ShellDestination.Active))
    }

    @Test
    fun `removing the last project leaves no destination`() {
        val repository = FakeProjectRepository(initialProjects = listOf(api))
        val viewModel = viewModel(repository)

        viewModel.removeProject(api)

        assertNull(viewModel.destination)
        assertNull(preferences.lastDestination)
    }

    @Test
    fun `a settings problem already reported by the projects file isn't reported twice`() {
        val problem = WorktreeError.LocalFileUnreadable("~/.shogunai/projects.json", "unexpected token")
        val unreadable = object : ProjectRepository by repository {
            override fun list(): List<Project> = throw problem
        }

        val viewModel = viewModel(unreadable, settingsProblem = problem)

        assertEquals(listOf(problem), viewModel.unreadableFiles)
    }
}
