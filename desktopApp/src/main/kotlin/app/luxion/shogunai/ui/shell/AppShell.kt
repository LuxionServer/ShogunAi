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
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.ui.components.DangerButton
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.PrimaryButton
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.projectconfig.ProjectConfigScreen
import app.luxion.shogunai.ui.projectconfig.ProjectConfigViewModel
import app.luxion.shogunai.ui.rememberScreenViewModelStoreOwner
import app.luxion.shogunai.ui.worktree.WorktreeScreen
import app.luxion.shogunai.ui.worktree.WorktreeViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

private val NarrowWindowThreshold = 900.dp
private val EmptyStateMaxWidth = 420.dp

@Composable
fun AppShell(appContainer: AppContainer, viewModel: ShellViewModel) {
    var projectPendingRemoval by remember { mutableStateOf<Project?>(null) }
    val messages = rememberAppMessages()
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(windowInfo) {
        snapshotFlow { windowInfo.isWindowFocused }
            .drop(1)
            .filter { it }
            .collectLatest { viewModel.refresh() }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val narrow = maxWidth < NarrowWindowThreshold
        LaunchedEffect(narrow) { viewModel.onWindowWidthChanged(narrow) }

        Row(modifier = Modifier.fillMaxSize()) {
            Sidebar(
                projects = viewModel.projects,
                selectedProjectId = viewModel.destination?.referencedProjectId,
                rail = viewModel.isRail,
                onSelectProject = viewModel::selectProject,
                onEditProject = viewModel::editProject,
                onRemoveProject = { projectPendingRemoval = it },
                onAddProject = viewModel::addProject,
                onToggle = viewModel::toggleSidebar,
            )
            VerticalDivider(modifier = Modifier.fillMaxHeight())
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                CompositionLocalProvider(LocalAppMessages provides messages) {
                    ShellContent(appContainer, viewModel)
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
private fun ShellContent(appContainer: AppContainer, viewModel: ShellViewModel) {
    when (val destination = viewModel.destination) {
        null -> EmptyState(onAddProject = viewModel::addProject)
        is ShellDestination.Project -> viewModel.projectFor(destination)?.let { project ->
            ProjectWorktrees(
                appContainer,
                project,
                destination.previousWorktreesRoot,
                viewModel.reloadRequests,
                viewModel.newWorktreeRequests,
                viewModel.copyLaunchCommandRequests,
                viewModel.deleteWorktreeRequests,
                onOpenProjectSettings = { viewModel.editProject(project) },
            )
        }
        is ShellDestination.ProjectSettings -> viewModel.projectFor(destination)?.let { project ->
            key(destination) { ProjectForm(appContainer, viewModel, existingProject = project) }
        }
        ShellDestination.AddProject -> key(destination) { ProjectForm(appContainer, viewModel, existingProject = null) }
    }
}

@Composable
private fun ProjectWorktrees(
    appContainer: AppContainer,
    project: Project,
    previousWorktreesRoot: String?,
    reloadRequests: Int,
    newWorktreeRequests: Int,
    copyLaunchCommandRequests: Int,
    deleteWorktreeRequests: Int,
    onOpenProjectSettings: () -> Unit,
) {
    val viewModelKey = "${project.id}-${project.config.hashCode()}"
    val viewModel = viewModel(key = viewModelKey) {
        WorktreeViewModel(appContainer.worktreeUseCases(project.config))
    }
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(viewModelKey) {
        previousWorktreesRoot?.let(viewModel::repairAfterMove) ?: viewModel.refresh()
    }
    LaunchedEffect(viewModelKey, windowInfo) {
        snapshotFlow { windowInfo.isWindowFocused }
            .drop(1)
            .filter { it }
            .collectLatest { viewModel.refresh() }
    }
    val reloadRequestsOnEntry = remember(viewModelKey) { reloadRequests }
    LaunchedEffect(reloadRequests) {
        if (reloadRequests != reloadRequestsOnEntry) viewModel.refresh()
    }
    val newWorktreeRequestsOnEntry = remember(viewModelKey) { newWorktreeRequests }
    LaunchedEffect(newWorktreeRequests) {
        if (newWorktreeRequests != newWorktreeRequestsOnEntry) viewModel.openCreateDialog()
    }
    val copyRequestsOnEntry = remember(viewModelKey) { copyLaunchCommandRequests }
    LaunchedEffect(copyLaunchCommandRequests) {
        if (copyLaunchCommandRequests != copyRequestsOnEntry) viewModel.copySelectedLaunchCommand()
    }
    val deleteRequestsOnEntry = remember(viewModelKey) { deleteWorktreeRequests }
    LaunchedEffect(deleteWorktreeRequests) {
        if (deleteWorktreeRequests != deleteRequestsOnEntry) viewModel.requestSelectedRemoval()
    }
    WorktreeScreen(
        projectName = project.name,
        projectConfig = project.config,
        viewModel = viewModel,
        onOpenProjectSettings = onOpenProjectSettings,
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
