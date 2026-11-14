package app.luxion.shogunai.ui.shell

import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.RunnerId
import app.luxion.shogunai.ui.actions.ActionId
import app.luxion.shogunai.ui.machine.MachineProject
import app.luxion.shogunai.ui.machine.MachineResource
import app.luxion.shogunai.ui.palette.PaletteAction
import app.luxion.shogunai.ui.palette.PaletteCommand
import app.luxion.shogunai.ui.worktree.WorktreeBoard

internal fun runnerInUse(tools: ToolsPreferences): RunnerId = tools.defaultRunner ?: RunnerId.HERDR

internal fun declaresMobileProviders(projects: List<MachineProject>): Boolean =
    projects.any { MachineResource.ANDROID_EMULATORS in it.declares || MachineResource.IOS_SIMULATORS in it.declares }

internal fun loadedBranches(projects: List<MachineProject>): List<String> =
    projects.flatMap { it.worktrees.orEmpty() }.mapNotNull { it.branch }

internal fun sidebarMultirepos(viewModel: ShellViewModel, board: WorktreeBoard): List<SidebarMultirepo> =
    viewModel.memberships.map { membership ->
        val members = viewModel.projects.filter { it.id in membership.aliases }
        val own = ProjectActivity(board.multirepoWaitingCountOf(membership.folder), board.multirepoAgentsOf(membership.folder).map { it.name })
        SidebarMultirepo(
            folder = membership.folder,
            name = membership.name,
            projects = members,
            activity = members.fold(own) { total, project -> total + ProjectActivity(board.waitingCountOf(project.id), board.agentNamesOf(project.id)) },
            collapsed = membership.folder in viewModel.collapsedMultirepos,
            labels = multirepoChildLabels(membership),
            needsReview = !membership.multirepo.approved,
        )
    } + viewModel.unavailableMultirepos.map { unavailableSidebarFolder(it, it.folder in viewModel.collapsedMultirepos) }

internal suspend fun openDroppedFolder(folder: String, belongsToRepository: suspend (String) -> Boolean, viewModel: ShellViewModel) {
    if (belongsToRepository(folder)) viewModel.addProject(folder) else viewModel.addMultirepo(folder)
}

internal fun openProjectSettings(viewModel: ShellViewModel, projectId: String) {
    viewModel.projects.firstOrNull { it.id == projectId }?.let(viewModel::editProject)
}

internal fun tourProjectShown(destination: ShellDestination?, projects: List<Project>, isTour: (Project) -> Boolean): Project? {
    val projectId = (destination as? ShellDestination.All)?.projectFilter ?: return null
    return projects.firstOrNull { it.id == projectId }?.takeIf(isTour)
}

internal fun runPaletteAction(
    action: PaletteAction,
    viewModel: ShellViewModel,
    board: WorktreeBoard,
    onOpenPreferences: () -> Unit,
    copySelectedPath: () -> Unit,
    onCopied: () -> Unit,
) {
    when (action) {
        is PaletteAction.Open -> viewModel.navigate(action.destination)
        is PaletteAction.SelectWorktree -> viewModel.navigate(ShellDestination.All()) {
            board.revealInAllWorktrees(action.ref)
        }
        is PaletteAction.CopyLaunchCommand -> board.copyLaunchCommand(action.ref, onCopied)
        is PaletteAction.RunSelected -> if (action.id == ActionId.CopyPath) copySelectedPath() else board.performOnSelected(action.id, action.agent)
        is PaletteAction.NewWorktreeIn -> viewModel.navigate(ShellDestination.All(projectFilter = action.projectId)) {
            board.openCreateDialog(action.projectId)
        }
        is PaletteAction.Run -> when (action.command) {
            PaletteCommand.ADD_PROJECT -> viewModel.addProject()
            PaletteCommand.CLEAN_UP -> viewModel.navigate(ShellDestination.All()) { board.openCleanup() }
            PaletteCommand.RELOAD -> viewModel.reload()
            PaletteCommand.TOGGLE_SIDEBAR -> viewModel.toggleSidebar()
            PaletteCommand.OPEN_PREFERENCES -> onOpenPreferences()
        }
    }
}
