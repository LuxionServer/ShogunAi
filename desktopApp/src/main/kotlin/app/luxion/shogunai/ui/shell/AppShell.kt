package app.luxion.shogunai.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.luxion.shogunai.AppContainer
import app.luxion.shogunai.domain.model.GroupedView
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.ui.components.DangerButton
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.PrimaryButton
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.projectconfig.ProjectConfigScreen
import app.luxion.shogunai.ui.projectconfig.ProjectConfigViewModel
import app.luxion.shogunai.ui.rememberScreenViewModelStoreOwner
import app.luxion.shogunai.ui.worktree.WorktreeBoard
import app.luxion.shogunai.ui.worktree.WorktreeScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

private val NarrowWindowThreshold = 900.dp
private val EmptyStateMaxWidth = 420.dp

@Composable
fun AppShell(appContainer: AppContainer, viewModel: ShellViewModel) {
    var projectPendingRemoval by remember { mutableStateOf<Project?>(null) }
    val messages = rememberAppMessages()
    val board = viewModel {
        WorktreeBoard(
            useCasesFor = appContainer::worktreeUseCases,
            detectAgents = appContainer.detectRunningAgents::invoke,
            preferences = appContainer.shellPreferenceRepository,
        )
    }
    LaunchedEffect(viewModel.projects) { board.syncCatalog(viewModel.projects) }
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(windowInfo) {
        snapshotFlow { windowInfo.isWindowFocused }
            .drop(1)
            .filter { it }
            .collectLatest {
                viewModel.refresh()
                board.refreshShown()
            }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val narrow = maxWidth < NarrowWindowThreshold
        LaunchedEffect(narrow) { viewModel.onWindowWidthChanged(narrow) }

        Row(modifier = Modifier.fillMaxSize()) {
            Sidebar(
                projects = viewModel.projects,
                selectedProjectId = viewModel.destination?.referencedProjectId,
                isActiveSelected = viewModel.destination == ShellDestination.Active,
                isAllSelected = viewModel.destination == ShellDestination.All(),
                activeCount = board.activeCount,
                totalWorktreeCount = board.totalWorktreeCount,
                activityOf = { project -> ProjectActivity(board.activeCountOf(project.id), board.agentNamesOf(project.id)) },
                rail = viewModel.isRail,
                onSelectActive = viewModel::selectActive,
                onSelectAll = viewModel::selectAll,
                onSelectProject = viewModel::selectProject,
                onEditProject = viewModel::editProject,
                onRemoveProject = { projectPendingRemoval = it },
                onAddProject = viewModel::addProject,
                onToggle = viewModel::toggleSidebar,
            )
            VerticalDivider(modifier = Modifier.fillMaxHeight())
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                CompositionLocalProvider(LocalAppMessages provides messages) {
                    ShellContent(appContainer, viewModel, board)
                }
                AppMessagesHost(messages, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    projectPendingRemoval?.let { project ->
        RemoveProjectDialog(
            project = project,
            onConfirm = {
                viewModel.removeProject(project)
                projectPendingRemoval = null
            },
            onDismiss = { projectPendingRemoval = null },
        )
    }
}

@Composable
private fun ShellContent(appContainer: AppContainer, viewModel: ShellViewModel, board: WorktreeBoard) {
    when (val destination = viewModel.destination) {
        null -> EmptyState(onAddProject = viewModel::addProject)
        ShellDestination.Active -> WorktreeView(destination, viewModel, board)
        is ShellDestination.All -> WorktreeView(destination, viewModel, board)
        is ShellDestination.ProjectSettings -> viewModel.projectFor(destination)?.let { project ->
            key(destination) { ProjectForm(appContainer, viewModel, existingProject = project) }
        }
        ShellDestination.AddProject -> key(destination) { ProjectForm(appContainer, viewModel, existingProject = null) }
    }
}

@Composable
private fun WorktreeView(destination: ShellDestination, shellViewModel: ShellViewModel, board: WorktreeBoard) {
    LaunchedEffect(destination, board.projects) {
        val all = destination as? ShellDestination.All
        board.show(all?.projectFilter, if (all == null) GroupedView.ACTIVE else GroupedView.ALL)
        val projectId = all?.projectFilter
        val previousRoot = all?.previousWorktreesRoot
        if (projectId != null && previousRoot != null) board.repairAfterMove(projectId, previousRoot)
    }
    DisposableEffect(board) {
        shellViewModel.updateWorktreeActions(
            WorktreeActions(
                onFind = board::requestFind,
                onCopySelectedLaunchCommand = board::copySelectedLaunchCommand,
                onDeleteSelectedWorktree = board::deleteSelectedWorktree,
                onClearSelection = board::clearSelection,
                onEscape = board::escape,
            ),
        )
        onDispose { shellViewModel.updateWorktreeActions(null) }
    }
    val reloadRequestsOnEntry = remember { shellViewModel.reloadRequests }
    LaunchedEffect(shellViewModel.reloadRequests) {
        if (shellViewModel.reloadRequests != reloadRequestsOnEntry) board.refreshShown()
    }
    val newWorktreeRequestsOnEntry = remember { shellViewModel.newWorktreeRequests }
    LaunchedEffect(shellViewModel.newWorktreeRequests) {
        if (shellViewModel.newWorktreeRequests != newWorktreeRequestsOnEntry) board.openCreateDialog()
    }
    WorktreeScreen(
        board = board,
        onSelectFilter = shellViewModel::selectFilter,
        onOpenProjectSettings = shellViewModel::editProject,
        onSeeAll = shellViewModel::selectAll,
    )
}

@Composable
private fun ProjectForm(appContainer: AppContainer, shellViewModel: ShellViewModel, existingProject: Project?) {
    val viewModel = viewModel(viewModelStoreOwner = rememberScreenViewModelStoreOwner()) {
        ProjectConfigViewModel(
            appContainer.projectRepository,
            appContainer.repositoryProvisioningSource,
            existingProject,
            appContainer::loadAvds,
            appContainer.simulatorSetup,
        )
    }
    ProjectConfigScreen(
        viewModel = viewModel,
        onSaved = { saved -> shellViewModel.onProjectSaved(saved, previous = existingProject) },
        onCancel = shellViewModel::cancelForm,
    )
}

@Composable
private fun EmptyState(onAddProject: () -> Unit) {
    val strings = LocalStrings.current
    Box(modifier = Modifier.fillMaxSize().padding(Spacing.xl), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = EmptyStateMaxWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(strings.emptyStateTitle, style = MaterialTheme.typography.headlineSmall)
            Text(
                strings.emptyStateMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            PrimaryButton(onClick = onAddProject) { Text(strings.addProject) }
        }
    }
}

@Composable
private fun RemoveProjectDialog(project: Project, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.removeProjectTitle) },
        text = { Text(strings.removeProjectMessage(project.name)) },
        confirmButton = { DangerButton(onClick = onConfirm) { Text(strings.remove) } },
        dismissButton = { NeutralTextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}
