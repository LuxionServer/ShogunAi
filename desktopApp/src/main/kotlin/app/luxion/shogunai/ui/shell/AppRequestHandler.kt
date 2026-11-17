package app.luxion.shogunai.ui.shell

import app.luxion.shogunai.domain.model.AppRequest
import app.luxion.shogunai.ui.worktree.WorktreeRef

interface WorktreeReveal {
    fun knowsWorktree(ref: WorktreeRef): Boolean
    fun revealInAllWorktrees(ref: WorktreeRef)
}

class AppRequestHandler(
    private val viewModel: ShellViewModel,
    private val board: () -> WorktreeReveal? = { null },
    private val showMainWindow: () -> Unit,
) {
    fun handle(request: AppRequest) {
        when (request.action) {
            AppRequest.OPEN_APP -> showMainWindow()
            AppRequest.OPEN_PROJECT -> openProject(request)
            AppRequest.OPEN_WORKTREE -> openWorktree(request)
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

    private fun openWorktree(request: AppRequest) {
        viewModel.refresh()
        val project = viewModel.projects.firstOrNull { it.id == request.projectId } ?: return
        showMainWindow()
        val ref = WorktreeRef(project.id, request.worktreePath.orEmpty())
        val reveal = board()?.takeIf { it.knowsWorktree(ref) } ?: return viewModel.selectProject(project)
        viewModel.navigate(ShellDestination.All()) { reveal.revealInAllWorktrees(ref) }
    }
}
