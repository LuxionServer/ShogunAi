package app.luxion.shogunai.ui.shell

import kotlin.time.Duration.Companion.seconds
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.luxion.shogunai.domain.model.RunnerId
import app.luxion.shogunai.AppContainer
import app.luxion.shogunai.domain.model.GroupedView
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.diagnostics.diagnosticsExceptionHandler
import app.luxion.shogunai.ui.WindowActivity
import app.luxion.shogunai.ui.components.EmptyState
import app.luxion.shogunai.ui.components.ShogunDialog
import app.luxion.shogunai.ui.components.DangerButton
import app.luxion.shogunai.ui.components.StateAction
import app.luxion.shogunai.ui.isMacOs
import app.luxion.shogunai.ui.components.FocusRingWidth
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.PrimaryButton
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.actions.ActionId
import app.luxion.shogunai.ui.actions.menuBarEntries
import app.luxion.shogunai.ui.actions.paletteEntries
import app.luxion.shogunai.ui.components.ErrorFixActions
import app.luxion.shogunai.ui.components.ErrorNotice
import app.luxion.shogunai.ui.components.LocalErrorFixActions
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.projectconfig.ProjectSettingsView
import app.luxion.shogunai.ui.projectconfig.ProjectConfigViewModel
import app.luxion.shogunai.ui.rememberScreenViewModelStoreOwner
import app.luxion.shogunai.ui.machine.MachineResource
import app.luxion.shogunai.ui.machine.MachineResources
import app.luxion.shogunai.ui.machine.machineStatusItems
import app.luxion.shogunai.ui.palette.CommandPalette
import app.luxion.shogunai.ui.palette.PaletteAction
import app.luxion.shogunai.ui.palette.PaletteCommand
import app.luxion.shogunai.ui.palette.buildPaletteIndex
import app.luxion.shogunai.ui.palette.platformShortcutLabel
import app.luxion.shogunai.ui.palette.pruneRecent
import app.luxion.shogunai.ui.machine.MachineOverviewView
import app.luxion.shogunai.ui.machine.MachineView
import app.luxion.shogunai.ui.worktree.InactivityPreferences
import app.luxion.shogunai.ui.worktree.WorktreeBoard
import app.luxion.shogunai.ui.worktree.WorktreeRef
import app.luxion.shogunai.ui.worktree.WorktreeScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalClipboard
import app.luxion.shogunai.ui.components.copyText
import app.luxion.shogunai.ui.multirepo.AddMultirepoDialog
import app.luxion.shogunai.ui.multirepo.AddMultirepoState
import app.luxion.shogunai.ui.multirepo.MultirepoSettingsState
import app.luxion.shogunai.ui.multirepo.MultirepoSettingsView
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import app.luxion.shogunai.ui.theme.ShogunTheme

private val NarrowWindowThreshold = 900.dp

@Composable
fun AppShell(
    appContainer: AppContainer,
    viewModel: ShellViewModel,
    onOpenPreferences: () -> Unit,
    inactivity: InactivityPreferences,
    onBoardReady: (WorktreeBoard) -> Unit = {},
    messages: AppMessages = rememberAppMessages(),
    windowShown: Boolean = true,
) {
    var projectPendingRemoval by remember { mutableStateOf<Project?>(null) }
    val copiedMessage = LocalStrings.current.copied
    val board = viewModel {
        WorktreeBoard(
            useCasesFor = appContainer::worktreeUseCases,
            detectAgents = appContainer.detectRunningAgents::invoke,
            syncOrca = appContainer::syncOrca,
            preferences = appContainer.shellPreferenceRepository,
            approval = appContainer.repositoryApproval,
            rememberFetchBeforeCreate = viewModel::rememberFetchBeforeCreate,
            onWorktreeUsed = { ref -> viewModel.recordWorktree(ref.projectId, ref.path) },
            inactivity = inactivity,
            multirepoActions = appContainer.multirepoActions,
            runnerInUse = { appContainer.tools.defaultRunner ?: RunnerId.HERDR },
            desktopTurn = appContainer.desktopTurn,
            cadence = appContainer::refreshCadence,
        )
    }
    LaunchedEffect(viewModel.memberships) { board.syncMultirepos(viewModel.memberships) }
    SideEffect { onBoardReady(board) }
    SideEffect {
        appContainer.loadedBranches = { board.machineProjects.flatMap { it.worktrees.orEmpty() }.mapNotNull { it.branch } }
    }
    LaunchedEffect(viewModel.projects) { board.syncCatalog(viewModel.projects) }
    viewModel.projects.forEach { project ->
        val holder = board.holder(project.id)
        val loadedPaths = holder?.takeIf { it.hasLoaded && it.listFailure == null }?.worktrees?.mapTo(mutableSetOf()) { it.path }
        LaunchedEffect(project.id, loadedPaths) { loadedPaths?.let { viewModel.pruneRecentWorktrees(project.id, it) } }
    }
    val diagnosticsHandler = remember(appContainer) { diagnosticsExceptionHandler(appContainer.diagnosticsLog) }
    val machineScope = rememberCoroutineScope { diagnosticsHandler }
    val machine = remember {
        MachineResources(
            scope = machineScope,
            useCases = appContainer.machineUseCases,
            projects = { board.machineProjects },
            pathExists = appContainer.pathExists,
            onDevicesChanged = { projectId -> board.holder(projectId)?.refreshDevices() },
            desktopTurn = appContainer.desktopTurn,
            cadence = appContainer::refreshCadence,
        )
    }
    val declaredMachine = machine.declared
    LaunchedEffect(declaredMachine) { machine.runSummaryTicks() }
    LaunchedEffect(viewModel) { viewModel.watchCatalog(CatalogCheckInterval) }
    LaunchedEffect(viewModel.destination, board.isCatalogLoaded) {
        val destination = viewModel.destination as? ShellDestination.Machine
        machine.show(destination?.resource)
        if (destination != null && board.isCatalogLoaded && destination.resource !in machine.visibleResources) viewModel.selectActive()
    }
    val windowInfo = LocalWindowInfo.current
    val windowActivity = WindowActivity.of(windowShown, windowInfo.isWindowFocused)
    LaunchedEffect(windowActivity) {
        board.setWindowActivity(windowActivity)
        machine.setWindowActivity(windowActivity)
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
    val dropScope = rememberCoroutineScope()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .folderDropTarget(folderDrop) { folder ->
                dropScope.launch {
                    if (appContainer.multirepoSetup.belongsToRepository(folder)) viewModel.addProject(folder) else viewModel.addMultirepo(folder)
                }
            }
            .then(
                if (folderDrop.hovering) {
                    Modifier.border(FocusRingWidth, ShogunTheme.colors.accent)
                } else {
                    Modifier
                },
            ),
    ) {
        val narrow = maxWidth < NarrowWindowThreshold
        LaunchedEffect(narrow) { viewModel.onWindowWidthChanged(narrow) }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                    multirepos = viewModel.memberships.map { membership ->
                        val members = viewModel.projects.filter { it.id in membership.aliases }
                        SidebarMultirepo(
                            folder = membership.folder,
                            name = membership.name,
                            projects = members,
                            activity = members.fold(
                                ProjectActivity(board.multirepoActivityCountOf(membership.folder), board.multirepoAgentsOf(membership.folder).map { it.name }),
                            ) { total, project ->
                                total + ProjectActivity(board.activeCountOf(project.id), board.agentNamesOf(project.id))
                            },
                            collapsed = membership.folder in viewModel.collapsedMultirepos,
                            labels = multirepoChildLabels(membership),
                            needsReview = !membership.multirepo.approved,
                        )
                    },
                    selectedMultirepo = viewModel.destination?.referencedMultirepo,
                    onSelectMultirepo = { viewModel.selectMultirepo(it) },
                    onToggleMultirepo = viewModel::toggleMultirepoCollapsed,
                    onEditMultirepo = viewModel::editMultirepo,
                    onAddMultirepo = { viewModel.addMultirepo() },
                    order = viewModel.sidebarOrder,
                    onMoveEntry = viewModel::moveSidebarEntry,
                    onSortLevel = viewModel::sortSidebarAlphabetically,
                    onOpenPreferences = onOpenPreferences,
                    footer = { TourNotice(appContainer.tour, viewModel.projects.isNotEmpty(), viewModel.isRail) },
                )
                VerticalDivider(modifier = Modifier.fillMaxHeight())
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    appContainer.updates.notice?.let { UpdateNotice(appContainer.updates, Modifier.padding(Spacing.md)) }
                    LaunchedEffect(Unit) { appContainer.claudeHooks.refresh() }
                    ClaudeHooksNotice(appContainer.claudeHooks, board.hasClaudeWithoutState, Modifier.padding(Spacing.md))
                    tourProjectShown(appContainer, viewModel)?.let { project ->
                        TourBanner(
                            state = appContainer.tour,
                            project = project,
                            onEnded = viewModel::refresh,
                            agentChoices = { shown, onLaunched -> TourAgentChoices(board, shown, onLaunched) { messages.show(copiedMessage) } },
                            modifier = Modifier.padding(Spacing.md),
                        )
                    }
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
            val statusItems = machineStatusItems(machine.visibleResources, machine.summaries, machine.summaryFailures, LocalStrings.current)
            HorizontalDivider()
            MachineStatusBar(
                items = statusItems,
                selected = (viewModel.destination as? ShellDestination.Machine)?.resource,
                compact = viewModel.isRail,
                onSelect = viewModel::selectMachine,
                overviewSelected = viewModel.destination == ShellDestination.MachineOverview,
                onOpenOverview = viewModel::selectMachineOverview,
            )
        }
    }

    UpdateCheckDialog(appContainer.updates)
    ReleaseNotesDialog(appContainer.updates)

    TourDialog(
        state = appContainer.tour,
        hooks = appContainer.claudeHooks,
        onShowProject = { project ->
            viewModel.refresh()
            viewModel.selectProject(project)
        },
        agentChoices = { project, onLaunched -> TourAgentChoices(board, project, onLaunched) { messages.show(copiedMessage) } },
    )

    if (viewModel.isPaletteOpen) {
        PaletteHost(viewModel, board, machine, messages, onOpenPreferences)
    }

    if (viewModel.isLeaveConfirmationPending) {
        DiscardChangesDialog(onConfirm = viewModel::confirmLeave, onDismiss = viewModel::keepEditing)
    }

    viewModel.addMultirepoRequest?.let { request ->
        val scope = rememberCoroutineScope()
        val state = remember(request) {
            AddMultirepoState(appContainer.multirepoSetup, scope).also { state -> request.folder?.let(state::choose) }
        }
        AddMultirepoDialog(state, onDismiss = viewModel::dismissAddMultirepo, onAdded = viewModel::onMultirepoAdded)
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
    val worktreeActions = board.selectedCatalog(strings)?.paletteEntries(strings).orEmpty()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copySelectedPath: () -> Unit = {
        board.selectedWorktree?.worktree?.path?.let { path ->
            scope.launch {
                clipboard.copyText(path)
                messages.show(strings.copied)
            }
        }
    }
    val items = remember(viewModel.projects, board.paletteWorktrees, machine.visibleResources, board.canCleanUp(), strings, worktreeActions.map { it.key }) {
        buildPaletteIndex(
            projects = viewModel.projects,
            worktrees = board.paletteWorktrees,
            machineResources = machine.visibleResources,
            canCleanUp = board.canCleanUp(),
            strings = strings,
            shortcutLabel = ::platformShortcutLabel,
            worktreeActions = worktreeActions,
        )
    }
    val knownIds = remember(items) { items.mapTo(mutableSetOf()) { it.id } }
    CommandPalette(
        items = items,
        recentIds = pruneRecent(viewModel.recentPaletteItems, items),
        onRun = { item, action ->
            viewModel.closePalette()
            viewModel.rememberPaletteItem(item.id, knownIds)
            runPaletteAction(action, viewModel, board, onOpenPreferences, copySelectedPath) { messages.show(strings.copied) }
        },
        onDismiss = viewModel::closePalette,
    )
}

private fun runPaletteAction(
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

@Composable
private fun ShellContent(appContainer: AppContainer, viewModel: ShellViewModel, board: WorktreeBoard, machine: MachineResources) {
    val refreshBoard: (String) -> Unit = { projectId -> board.holder(projectId)?.refresh() }
    when (val destination = viewModel.destination) {
        null -> FirstLaunchState(onAddProject = viewModel::addProject, onTakeTour = appContainer.tour::start)
        ShellDestination.Active -> WorktreeView(destination, viewModel, board)
        is ShellDestination.All -> WorktreeView(destination, viewModel, board)
        is ShellDestination.ProjectSettings -> viewModel.projectFor(destination)?.let { project ->
            key(destination) { ProjectForm(appContainer, viewModel, existingProject = project, initialFolder = null, onRepositoryConfigWritten = refreshBoard) }
        }
        is ShellDestination.AddProject -> key(destination) {
            ProjectForm(appContainer, viewModel, existingProject = null, initialFolder = destination.folder, onRepositoryConfigWritten = refreshBoard)
        }
        ShellDestination.MachineOverview -> MachineOverviewView(
            items = machineStatusItems(machine.overviewResources, machine.overviewSummaries, machine.summaryFailures, LocalStrings.current),
            providersDeclared = machine.declared.isNotEmpty(),
            onOpen = viewModel::selectMachine,
        )
        is ShellDestination.Machine -> MachineDestination(destination.resource, viewModel, board, machine)
        is ShellDestination.MultirepoView -> WorktreeView(destination, viewModel, board)
        is ShellDestination.MultirepoSettings -> key(destination) { MultirepoSettingsDestination(appContainer, viewModel, destination.folder) }
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
    LaunchedEffect(destination) {
        val multirepo = destination as? ShellDestination.MultirepoView ?: return@LaunchedEffect
        board.showMultirepo(multirepo.folder)
        multirepo.group?.let(board::revealGroup)
    }
    LaunchedEffect(destination, board.projects) {
        if (destination is ShellDestination.MultirepoView) return@LaunchedEffect
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
                menuBar = { strings -> board.menuBarCatalog(strings).menuBarEntries() },
                onRun = board::runFromMenu,
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
        onOpenMultirepoSettings = { (destination as? ShellDestination.MultirepoView)?.let { shellViewModel.editMultirepo(it.folder) } },
        onOpenGroup = { tag -> shellViewModel.selectMultirepo(tag.multirepoFolder, tag.branch) },
        onMultirepoTrusted = shellViewModel::onMultirepoChanged,
    )
}

@Composable
private fun MultirepoSettingsDestination(appContainer: AppContainer, shellViewModel: ShellViewModel, folder: String) {
    val scope = rememberCoroutineScope()
    val state = remember(folder) { MultirepoSettingsState(folder, appContainer.multirepoSetup, scope, onChanged = shellViewModel::onMultirepoChanged) }
    MultirepoSettingsView(
        state = state,
        onClose = shellViewModel::cancelForm,
        onDeleted = { shellViewModel.onMultirepoDeleted(folder) },
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
            appContainer::detectStacks,
            appContainer::multirepoNameOfWorkspace,
            appContainer::detectMobileSecrets,
            appContainer::listLocalFolder,
            appContainer::listLocalFileCandidates,
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
internal fun FirstLaunchState(onAddProject: () -> Unit, onTakeTour: () -> Unit) {
    val strings = LocalStrings.current
    EmptyState(
        icon = Icons.Default.CreateNewFolder,
        title = strings.emptyStateTitle,
        message = strings.emptyStateMessage,
        action = StateAction(strings.addProject, onAddProject),
        link = StateAction(strings.tourTakeTour, onTakeTour),
    )
}

@Composable
private fun DiscardChangesDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    ShogunDialog(
        title = strings.discardChangesTitle,
        onDismissRequest = onDismiss,
        confirm = { PrimaryButton(onClick = onConfirm) { Text(strings.discard) } },
        dismiss = { NeutralTextButton(onClick = onDismiss) { Text(strings.keepEditing) } },
    ) {
        Text(strings.discardChangesMessage)
    }
}

@Composable
private fun RemoveProjectDialog(project: Project, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    ShogunDialog(
        title = strings.removeProjectTitle,
        onDismissRequest = onDismiss,
        confirm = { DangerButton(onClick = onConfirm) { Text(strings.remove) } },
        dismiss = { NeutralTextButton(onClick = onDismiss) { Text(strings.cancel) } },
    ) {
        Text(strings.removeProjectMessage(project.name))
    }
}

private fun tourProjectShown(appContainer: AppContainer, viewModel: ShellViewModel): Project? {
    val projectId = (viewModel.destination as? ShellDestination.All)?.projectFilter ?: return null
    return viewModel.projects.firstOrNull { it.id == projectId }?.takeIf(appContainer.tourLocation::isTour)
}

private val CatalogCheckInterval = 5.seconds
