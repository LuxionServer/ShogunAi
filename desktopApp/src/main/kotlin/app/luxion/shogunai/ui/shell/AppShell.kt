package app.luxion.shogunai.ui.shell

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import app.luxion.shogunai.ui.isMacOs
import app.luxion.shogunai.ui.components.FocusRingWidth
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.PrimaryButton
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.ErrorFixActions
import app.luxion.shogunai.ui.components.ErrorNotice
import app.luxion.shogunai.ui.components.LocalErrorFixActions
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.projectconfig.ProjectSettingsView
import app.luxion.shogunai.ui.projectconfig.ProjectConfigViewModel
import app.luxion.shogunai.ui.rememberScreenViewModelStoreOwner
import app.luxion.shogunai.ui.machine.MachineResource
import app.luxion.shogunai.ui.machine.MachineResources
import app.luxion.shogunai.ui.palette.CommandPalette
import app.luxion.shogunai.ui.palette.PaletteAction
import app.luxion.shogunai.ui.palette.PaletteCommand
import app.luxion.shogunai.ui.palette.buildPaletteIndex
import app.luxion.shogunai.ui.palette.platformShortcutLabel
import app.luxion.shogunai.ui.palette.pruneRecent
import app.luxion.shogunai.ui.machine.MachineView
import app.luxion.shogunai.ui.worktree.WorktreeBoard
import app.luxion.shogunai.ui.worktree.WorktreeRef
import app.luxion.shogunai.ui.worktree.WorktreeScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

private val NarrowWindowThreshold = 900.dp
private val EmptyStateMaxWidth = 420.dp

@Composable
fun AppShell(
    appContainer: AppContainer,
    viewModel: ShellViewModel,
    onOpenPreferences: () -> Unit,
    onBoardReady: (WorktreeBoard) -> Unit = {},
) {
    var projectPendingRemoval by remember { mutableStateOf<Project?>(null) }
    val messages = rememberAppMessages()
    val board = viewModel {
        WorktreeBoard(
            useCasesFor = appContainer::worktreeUseCases,
            detectAgents = appContainer.detectRunningAgents::invoke,
            preferences = appContainer.shellPreferenceRepository,
            approval = appContainer.postCreateApproval,
            rememberFetchBeforeCreate = viewModel::rememberFetchBeforeCreate,
            onWorktreeUsed = { ref -> viewModel.recordWorktree(ref.projectId, ref.path) },
        )
    }
    SideEffect { onBoardReady(board) }
    LaunchedEffect(viewModel.projects) { board.syncCatalog(viewModel.projects) }
    viewModel.projects.forEach { project ->
        val holder = board.holder(project.id)
        val loadedPaths = holder?.takeIf { it.hasLoaded && it.listFailure == null }?.worktrees?.mapTo(mutableSetOf()) { it.path }
        LaunchedEffect(project.id, loadedPaths) { loadedPaths?.let { viewModel.pruneRecentWorktrees(project.id, it) } }
    }
    val machineScope = rememberCoroutineScope()
    val machine = remember {
        MachineResources(
            scope = machineScope,
            useCases = appContainer.machineUseCases,
            projects = { board.machineProjects },
            pathExists = appContainer.pathExists,
            onDevicesChanged = { projectId -> board.holder(projectId)?.refreshDevices() },
        )
    }
    val declaredMachine = machine.declared
    LaunchedEffect(declaredMachine) { machine.runSummaryTicks() }
    LaunchedEffect(viewModel.destination, board.isCatalogLoaded) {
        val destination = viewModel.destination as? ShellDestination.Machine
        machine.show(destination?.resource)
        if (destination != null && board.isCatalogLoaded && destination.resource !in machine.declared) viewModel.selectActive()
    }
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(windowInfo) {
        snapshotFlow { windowInfo.isWindowFocused }.collect(board::setWindowActive)
    }
    LaunchedEffect(windowInfo) {
        snapshotFlow { windowInfo.isWindowFocused }
            .drop(1)
            .filter { it }
            .collectLatest {
                viewModel.refresh()
                board.refreshOnFocus()
            }
    }

    val folderDrop = rememberFolderDropState()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .folderDropTarget(folderDrop) { folder -> viewModel.addProject(folder) }
            .then(
                if (folderDrop.hovering) {
                    Modifier.border(FocusRingWidth, MaterialTheme.colorScheme.primary)
                } else {
                    Modifier
                },
            ),
    ) {
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
                machineResources = machine.visibleResources,
                machineSummaries = machine.summaries,
                selectedMachine = (viewModel.destination as? ShellDestination.Machine)?.resource,
                rail = viewModel.isRail,
                onSelectActive = viewModel::selectActive,
                onSelectAll = viewModel::selectAll,
                onSelectProject = viewModel::selectProject,
                onEditProject = viewModel::editProject,
                onRemoveProject = { projectPendingRemoval = it },
                onAddProject = viewModel::addProject,
                onSelectMachine = viewModel::selectMachine,
                onToggle = viewModel::toggleSidebar,
            )
            VerticalDivider(modifier = Modifier.fillMaxHeight())
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                appContainer.updates.notice?.let { UpdateNotice(appContainer.updates, Modifier.padding(Spacing.md)) }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val fixActions = remember(viewModel, onOpenPreferences) {
                        ErrorFixActions(
                            openProjectSettings = { projectId -> viewModel.projects.firstOrNull { it.id == projectId }?.let(viewModel::editProject) },
                            openPreferences = onOpenPreferences,
                        )
                    }
                    CompositionLocalProvider(LocalAppMessages provides messages, LocalErrorFixActions provides fixActions) {
                        ShellContent(appContainer, viewModel, board, machine)
                    }
                    AppMessagesHost(messages, modifier = Modifier.align(Alignment.BottomCenter))
                    if (viewModel.unreadableFiles.isNotEmpty()) {
                        Column(
                            modifier = Modifier.align(Alignment.TopCenter).padding(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            viewModel.unreadableFiles.forEach { problem ->
                                ErrorNotice(error = problem, onDismiss = viewModel::dismissUnreadableFiles)
                            }
                        }
                    }
                }
            }
        }
    }

    UpdateCheckDialog(appContainer.updates)

    if (viewModel.isPaletteOpen) {
        PaletteHost(viewModel, board, machine, messages, onOpenPreferences)
    }

    if (viewModel.isLeaveConfirmationPending) {
        DiscardChangesDialog(onConfirm = viewModel::confirmLeave, onDismiss = viewModel::keepEditing)
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
private fun PaletteHost(
    viewModel: ShellViewModel,
    board: WorktreeBoard,
    machine: MachineResources,
    messages: AppMessages,
    onOpenPreferences: () -> Unit,
) {
    val strings = LocalStrings.current
    val openActions = board.selectedOpenActions(strings)
    val launchChoices = board.selectedLaunchChoices(strings)
    val items = remember(viewModel.projects, board.paletteWorktrees, machine.visibleResources, board.canCleanUp(), strings, openActions.map { it.label }, launchChoices.map { it.label }) {
        buildPaletteIndex(
            projects = viewModel.projects,
            worktrees = board.paletteWorktrees,
            machineResources = machine.visibleResources,
            canCleanUp = board.canCleanUp(),
            strings = strings,
            shortcutLabel = ::platformShortcutLabel,
            openActions = openActions,
            launchChoices = launchChoices,
        )
    }
    val knownIds = remember(items) { items.mapTo(mutableSetOf()) { it.id } }
    CommandPalette(
        items = items,
        recentIds = pruneRecent(viewModel.recentPaletteItems, items),
        onRun = { item, action ->
            viewModel.closePalette()
            viewModel.rememberPaletteItem(item.id, knownIds)
            runPaletteAction(action, viewModel, board, onOpenPreferences) { messages.show(strings.copied) }
        },
        onDismiss = viewModel::closePalette,
    )
}

private fun runPaletteAction(
    action: PaletteAction,
    viewModel: ShellViewModel,
    board: WorktreeBoard,
    onOpenPreferences: () -> Unit,
    onCopied: () -> Unit,
) {
    when (action) {
        is PaletteAction.Open -> viewModel.navigate(action.destination)
        is PaletteAction.SelectWorktree -> viewModel.navigate(ShellDestination.All()) {
            board.revealInAllWorktrees(action.ref)
        }
        is PaletteAction.CopyLaunchCommand -> board.copyLaunchCommand(action.ref, onCopied)
        is PaletteAction.OpenSelected -> board.openSelected(action.kind)
        is PaletteAction.LaunchSelected -> board.launchSelected(action.kind, action.agent)
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

@Composable
private fun ShellContent(appContainer: AppContainer, viewModel: ShellViewModel, board: WorktreeBoard, machine: MachineResources) {
    val refreshBoard: (String) -> Unit = { projectId -> board.holder(projectId)?.refresh() }
    when (val destination = viewModel.destination) {
        null -> EmptyState(onAddProject = viewModel::addProject)
        ShellDestination.Active -> WorktreeView(destination, viewModel, board)
        is ShellDestination.All -> WorktreeView(destination, viewModel, board)
        is ShellDestination.ProjectSettings -> viewModel.projectFor(destination)?.let { project ->
            key(destination) { ProjectForm(appContainer, viewModel, existingProject = project, initialFolder = null, onRepositoryConfigWritten = refreshBoard) }
        }
        is ShellDestination.AddProject -> key(destination) {
            ProjectForm(appContainer, viewModel, existingProject = null, initialFolder = destination.folder, onRepositoryConfigWritten = refreshBoard)
        }
        is ShellDestination.Machine -> MachineDestination(destination.resource, viewModel, board, machine)
    }
}

@Composable
private fun MachineDestination(
    resource: MachineResource,
    shellViewModel: ShellViewModel,
    board: WorktreeBoard,
    machine: MachineResources,
) {
    val reloadRequestsOnEntry = remember { shellViewModel.reloadRequests }
    LaunchedEffect(shellViewModel.reloadRequests) {
        if (shellViewModel.reloadRequests != reloadRequestsOnEntry) machine.reload()
    }
    MachineView(
        resource = resource,
        machine = machine,
        onOpenWorktree = { projectId, path ->
            board.revealInAllWorktrees(WorktreeRef(projectId, path))
            shellViewModel.selectAll()
        },
    )
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
                onOpenSelectedInHerdr = board::openSelectedInHerdr,
                onDeleteSelectedWorktree = board::deleteSelectedWorktree,
                onClearSelection = board::clearSelection,
                onEscape = board::escape,
                hasSelection = { board.selectedWorktree != null },
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
        onOpenProjectSettings = shellViewModel::editProject,
        onSeeAll = shellViewModel::selectAll,
        onOpenMachine = shellViewModel::selectMachine,
    )
}

@Composable
private fun ProjectForm(
    appContainer: AppContainer,
    shellViewModel: ShellViewModel,
    existingProject: Project?,
    initialFolder: String?,
    onRepositoryConfigWritten: (projectId: String) -> Unit,
) {
    val viewModel = viewModel(viewModelStoreOwner = rememberScreenViewModelStoreOwner()) {
        ProjectConfigViewModel(
            appContainer.projectRepository,
            appContainer.repositoryProvisioningSource,
            existingProject,
            appContainer::loadAvds,
            appContainer.simulatorSetup,
            appContainer::inspectRepository,
            isMacOs,
            appContainer.repositoryTrust,
            appContainer.tools,
        )
    }
    ProjectSettingsView(
        viewModel = viewModel,
        saveRequests = shellViewModel.saveSettingsRequests,
        initialFolder = initialFolder,
        onAttachLeaveGuard = shellViewModel::attachLeaveGuard,
        onDetachLeaveGuard = shellViewModel::detachLeaveGuard,
        onCanSaveChanged = shellViewModel::updateCanSaveSettings,
        onSaved = { saved, repositoryWritten ->
            if (repositoryWritten) onRepositoryConfigWritten(saved.id)
            shellViewModel.onProjectSaved(saved, previous = existingProject)
        },
        onCancel = shellViewModel::cancelForm,
        onDiscard = shellViewModel::discardForm,
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
private fun DiscardChangesDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.discardChangesTitle) },
        text = { Text(strings.discardChangesMessage) },
        confirmButton = { PrimaryButton(onClick = onConfirm) { Text(strings.discard) } },
        dismissButton = { NeutralTextButton(onClick = onDismiss) { Text(strings.keepEditing) } },
    )
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
