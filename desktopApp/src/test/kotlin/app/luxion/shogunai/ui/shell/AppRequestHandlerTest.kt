package app.luxion.shogunai.ui.shell

import app.luxion.shogunai.domain.FakeProjectRepository
import app.luxion.shogunai.domain.FakeShellPreferenceRepository
import app.luxion.shogunai.domain.model.AppRequest
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppRequestHandlerTest {

    private fun project(id: String) = Project(
        id = id,
        name = id,
        config = ProjectConfig(baseRepositoryPath = "~/projects/$id", worktreesRoot = "~/projects", secretFiles = emptyList()),
    )

    private val api = project("api")
    private val mobile = project("mobile")
    private val repository = FakeProjectRepository(listOf(api, mobile))
    private val viewModel = ShellViewModel(repository, FakeShellPreferenceRepository())
    private var windowShown = 0
    private val handler = AppRequestHandler(viewModel) { windowShown++ }

    private class FakeLeaveGuard(override var hasUnsavedChanges: Boolean) : LeaveGuard

    private fun open(projectId: String, settings: Boolean = false) =
        handler.handle(AppRequest.openProject(createdAt = 1, projectId = projectId, settings = settings))

    @Test
    fun `opens the project's worktrees and brings the window forward`() {
        open("mobile")

        assertEquals(ShellDestination.All(projectFilter = "mobile"), viewModel.destination)
        assertEquals(1, windowShown)
    }

    @Test
    fun `opens the project's settings`() {
        open("mobile", settings = true)

        assertEquals(ShellDestination.ProjectSettings("mobile"), viewModel.destination)
    }

    @Test
    fun `open app only brings the window forward`() {
        viewModel.selectAll()

        handler.handle(AppRequest.openApp(createdAt = 1))

        assertEquals(ShellDestination.All(), viewModel.destination)
        assertEquals(1, windowShown)
    }

    @Test
    fun `finds a project added to the catalog after the app started`() {
        repository.save(project("added"))

        open("added")

        assertEquals(ShellDestination.All(projectFilter = "added"), viewModel.destination)
    }

    @Test
    fun `ignores a project that no longer exists`() {
        viewModel.selectAll()

        open("removed")

        assertEquals(ShellDestination.All(), viewModel.destination)
        assertEquals(0, windowShown)
    }

    @Test
    fun `does not renavigate when the project's settings are already open`() {
        viewModel.editProject(mobile)
        val guard = FakeLeaveGuard(hasUnsavedChanges = true)
        viewModel.attachLeaveGuard(guard)

        open("mobile", settings = true)

        assertEquals(ShellDestination.ProjectSettings("mobile"), viewModel.destination)
        assertFalse(viewModel.isLeaveConfirmationPending)
        assertEquals(1, windowShown)
    }

    @Test
    fun `asks before leaving a form with unsaved changes`() {
        viewModel.editProject(api)
        viewModel.attachLeaveGuard(FakeLeaveGuard(hasUnsavedChanges = true))

        open("mobile")

        assertEquals(ShellDestination.ProjectSettings("api"), viewModel.destination)
        assertTrue(viewModel.isLeaveConfirmationPending)
        assertEquals(1, windowShown)
    }

    @Test
    fun `keeps the unsaved form when the user chooses to keep editing`() {
        viewModel.editProject(api)
        viewModel.attachLeaveGuard(FakeLeaveGuard(hasUnsavedChanges = true))
        open("mobile")

        viewModel.keepEditing()

        assertEquals(ShellDestination.ProjectSettings("api"), viewModel.destination)
        assertFalse(viewModel.isLeaveConfirmationPending)
    }

    @Test
    fun `leaves the form when the user confirms`() {
        viewModel.editProject(api)
        viewModel.attachLeaveGuard(FakeLeaveGuard(hasUnsavedChanges = true))
        open("mobile")

        viewModel.confirmLeave()

        assertEquals(ShellDestination.All(projectFilter = "mobile"), viewModel.destination)
    }
}
