package app.luxion.shogunai.ui.shell

import app.luxion.shogunai.domain.model.AppRequest

class AppRequestHandler(
    private val viewModel: ShellViewModel,
    private val showMainWindow: () -> Unit,
) {
    fun handle(request: AppRequest) {
        when (request.action) {
            AppRequest.OPEN_APP -> showMainWindow()
            AppRequest.OPEN_PROJECT -> openProject(request)
        }
    }

    private fun openProject(request: AppRequest) {
        viewModel.refresh()
        val project = viewModel.projects.firstOrNull { it.id == request.projectId } ?: return
        showMainWindow()
        val target = if (request.settings) ShellDestination.ProjectSettings(project.id) else ShellDestination.All(projectFilter = project.id)
        if (viewModel.destination == target) return
        if (request.settings) viewModel.editProject(project) else viewModel.selectProject(project)
    }
}
