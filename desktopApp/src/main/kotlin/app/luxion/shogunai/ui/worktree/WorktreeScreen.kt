package app.luxion.shogunai.ui.worktree

import app.luxion.shogunai.ui.components.ButtonSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.luxion.shogunai.domain.model.HerdrOutcome
import app.luxion.shogunai.domain.model.OrcaOutcome
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.ui.components.DangerButton
import app.luxion.shogunai.ui.components.AgentIndicator
import java.time.Instant
import app.luxion.shogunai.ui.components.EmptyState
import app.luxion.shogunai.ui.components.IconAction
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.Notice
import app.luxion.shogunai.ui.components.NoticeKind
import app.luxion.shogunai.ui.components.SecondaryButton
import app.luxion.shogunai.ui.components.ErrorNotice
import app.luxion.shogunai.ui.components.ShogunDialog
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.StateAction
import app.luxion.shogunai.ui.components.handPointer
import app.luxion.shogunai.ui.components.onNewItemShortcut
import app.luxion.shogunai.ui.actions.ActionId
import app.luxion.shogunai.ui.actions.OpenTarget
import app.luxion.shogunai.ui.actions.PerformAction
import app.luxion.shogunai.ui.actions.ViewActionContext
import app.luxion.shogunai.ui.actions.toMenu
import app.luxion.shogunai.ui.actions.viewCatalog
import app.luxion.shogunai.ui.actions.viewOverflow
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.machine.MachineResource
import app.luxion.shogunai.ui.multirepo.MultirepoNotice
import app.luxion.shogunai.ui.multirepo.MultirepoReviewPane
import app.luxion.shogunai.ui.multirepo.NewChangeDialog
import app.luxion.shogunai.ui.multirepo.multirepoNoticeMessage
import app.luxion.shogunai.ui.shell.LocalAppMessages
import app.luxion.shogunai.ui.shell.MessageAction
import java.io.File
import app.luxion.shogunai.ui.theme.ShogunLayout
import app.luxion.shogunai.ui.theme.ShogunTheme

@Composable
fun WorktreeScreen(
    board: WorktreeBoard,
    onOpenProjectSettings: (Project) -> Unit,
    onSeeAll: () -> Unit,
    onOpenMachine: (MachineResource) -> Unit,
    onOpenMultirepoSettings: () -> Unit = {},
    onOpenGroup: (GroupTag) -> Unit = {},
    onMultirepoTrusted: () -> Unit = {},
) {
    val strings = LocalStrings.current
    DisposableEffect(board) {
        board.startWatching()
        onDispose { board.stopWatching() }
    }
    val messages = LocalAppMessages.current
    LaunchedEffect(board.pendingSuccess) {
        val pending = board.pendingSuccess ?: return@LaunchedEffect
        messages.show(strings.successMessage(pending.success), pending.action(strings, board))
        board.dismissSuccess(pending)
    }
    LaunchedEffect(board.multirepoNotice) {
        val notice = board.multirepoNotice ?: return@LaunchedEffect
        if (notice is MultirepoNotice.Failed) return@LaunchedEffect
        messages.show(strings.multirepoNoticeMessage(notice))
        board.dismissMultirepoNotice()
    }
    val multirepo = board.shownMultirepo
    val needsReview = multirepo?.multirepo?.approved == false

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onNewItemShortcut(enabled = !board.isCreateDialogOpen && board.newChange == null) {
                if (board.isMultirepoView) board.openNewChange() else board.openCreateDialog()
            },
    ) {
        ViewHeader(board, onOpenProjectSettings, onOpenMultirepoSettings)
        (board.multirepoNotice as? MultirepoNotice.Failed)?.let { notice ->
            ErrorNotice(
                error = notice.error,
                onDismiss = board::dismissMultirepoNotice,
                modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.sm),
            )
        }

        if (needsReview) {
            MultirepoReviewPane(
                multirepo.multirepo,
                isTrusting = board.isTrustingMultirepo,
                onTrust = { board.trustShownMultirepo(onMultirepoTrusted) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            WorktreeContent(board, onOpenProjectSettings, onSeeAll, onOpenMachine, modifier = Modifier.weight(1f).fillMaxWidth(), onOpenGroup = onOpenGroup)
        }
    }

    if (board.isCreateDialogOpen) {
        CreateWorktreeDialog(board)
    }

    board.newChange?.let { NewChangeDialog(it, board.approval, onConfirm = board::confirmNewChange, onDismiss = board::dismissNewChange) }

    if (board.isCleanupDialogOpen) {
        CleanupDialog(board)
    }

    board.pendingRemoval?.let { holder -> RemoveWorktreeDialog(holder) }
    board.keptBranchDeletion?.let { holder -> ForceDeleteBranchDialog(holder) }
    board.terminalChoice?.let { holder -> ChooseTerminalDialog(holder) }
}

@Composable
private fun ViewHeader(board: WorktreeBoard, onOpenProjectSettings: (Project) -> Unit, onOpenMultirepoSettings: () -> Unit) {
    val strings = LocalStrings.current
    val filteredProject = board.shownProjects.singleOrNull()?.takeIf { board.isProjectView }
    val multirepo = board.shownMultirepo
    val reviewReason = CleanupBlock.NeedsReview.describe(strings).takeIf { multirepo?.multirepo?.approved == false }
    val cleanUpBlock = board.cleanUpBlock()
    val shownHolders = board.shownProjects.mapNotNull { board.holder(it.id) }
    val catalog = viewCatalog(
        ViewActionContext(
            isMultirepo = multirepo != null,
            isProjectView = filteredProject != null,
            createBlocked = when {
                multirepo != null && multirepo.aliases.isEmpty() -> strings.addRepositoryFirst
                multirepo == null && board.projects.isEmpty() -> strings.addProjectFirst
                else -> null
            },
            cleanUpBlocked = reviewReason ?: cleanUpBlock?.describe(strings),
            offersCleanUp = cleanUpBlock != CleanupBlock.NothingToCleanUp,
            cleanUpCount = shownHolders.sumOf { it.cleanupCandidates.size },
            isOpeningMultirepoAgent = board.isOpeningMultirepoAgent,
            multirepoNeedsReview = reviewReason,
            orcaInstalled = board.isOrcaInstalled,
            defaultRunner = board.defaultRunner,
        ),
        strings,
    )
    val performView: PerformAction = { spec, _ ->
        when (spec.id) {
            ActionId.OpenMultirepoAgent(OpenTarget.HERDR) -> board.openMultirepoAgent()
            ActionId.OpenMultirepoAgent(OpenTarget.ORCA) -> board.openMultirepoAgentInOrca()
            ActionId.CleanUp -> board.openCleanup()
            ActionId.NewChange -> board.openNewChange()
            ActionId.NewWorktree -> board.openCreateDialog()
            else -> Unit
        }
    }
    val viewOptions = ViewOptions(
        projects = board.projects.takeIf { !board.isProjectView && !board.isMultirepoView },
        selectedProjectId = board.viewProjectFilter,
        groupings = if (board.isProjectView) emptyList() else board.groupingOptions,
        grouping = board.grouping,
        sort = board.sort,
    )
    val handlers = ViewOptionsHandlers(
        onProject = board::updateViewProjectFilter,
        onGrouping = board::updateGrouping,
        onSort = board::updateSort,
        onReload = board::refreshShown,
    )
    ViewHeaderRow(
        title = multirepo?.name ?: filteredProject?.name ?: if (board.isActiveView) strings.activeWorktrees else strings.allWorktrees,
        isBusy = board.shownProjects.any { it.id.let(board::holder)?.isLoading == true } || (board.isActiveView && board.isReadingActivity),
        filter = HeaderFilterState(board.textFilter, board::updateTextFilter, board::clearTextFilter, board.findRequests),
        viewMenu = viewOptionsMenu(viewOptions, handlers, strings),
        cleanUp = catalog.cleanUp,
        overflow = viewOverflow(catalog, strings).toMenu(strings, performView),
        primary = catalog.primary,
        onPerform = { performView(it, null) },
    ) {
        filteredProject?.let { project ->
            IconAction(
                icon = Icons.Default.Settings,
                label = strings.projectConfigTitle,
                onClick = { onOpenProjectSettings(project) },
            )
        }
        if (multirepo != null) {
            agentIndicator(board.multirepoAgentsOf(multirepo.folder), board.multirepoAgentsReadAt, Instant.now(), strings)?.let {
                AgentIndicator(it)
            }
            IconAction(icon = Icons.Default.Settings, label = strings.multirepoSettingsTitle, onClick = onOpenMultirepoSettings)
        }
    }
}

@Composable
private fun ChooseTerminalDialog(holder: ProjectWorktrees) {
    val strings = LocalStrings.current
    ShogunDialog(
        title = strings.chooseTerminalTitle,
        onDismissRequest = { holder.dismissTerminalChoice() },
        confirm = {},
        dismiss = {
            NeutralTextButton(onClick = { holder.dismissTerminalChoice() }) {
                Text(strings.cancel)
            }
        },
    ) {
        Text(strings.chooseTerminalMessage)
        holder.tools.availableTerminals.forEach { terminal ->
            SecondaryButton(onClick = { holder.chooseTerminal(terminal) }, modifier = Modifier.fillMaxWidth()) {
                Text(terminal.displayName)
            }
        }
    }
}

@Composable
private fun ForceDeleteBranchDialog(holder: ProjectWorktrees) {
    val strings = LocalStrings.current
    val branch = holder.keptBranchNotice?.branch ?: return
    ShogunDialog(
        title = strings.deleteBranchAnywayTitle,
        onDismissRequest = { holder.cancelForcedBranchDeletion() },
        dismissible = !holder.isDeletingBranch,
        dismiss = {
            if (holder.isDeletingBranch) InlineProgress()
            NeutralTextButton(onClick = { holder.cancelForcedBranchDeletion() }, enabled = !holder.isDeletingBranch) {
                Text(strings.cancel)
            }
        },
        confirm = {
            DangerButton(onClick = { holder.confirmForcedBranchDeletion() }, enabled = !holder.isDeletingBranch) {
                Text(strings.delete)
            }
        },
    ) {
        Text(strings.deleteBranchAnywayMessage(branch))
    }
}

@Composable
private fun RemoveWorktreeDialog(holder: ProjectWorktrees) {
    val strings = LocalStrings.current
    val worktree = holder.worktreePendingRemoval ?: return
    val requiresForce = holder.pendingRemovalRequiresForce
    val runnerNotices = listOfNotNull(
        strings.herdrWorkspaceWillClose.takeIf { holder.pendingRemovalOpenInHerdr },
        strings.orcaTerminalsWillClose.takeIf { holder.pendingRemovalHasOrcaTerminals },
    )
    ShogunDialog(
        title = if (requiresForce) strings.forceRemoveTitle else strings.removeWorktreeTitle,
        onDismissRequest = { holder.dismissPendingRemoval() },
        dismissible = !holder.isRemoving,
        notices = if (runnerNotices.isEmpty()) {
            null
        } else {
            { runnerNotices.forEach { Notice(kind = NoticeKind.WARNING, message = it, onDismiss = null) } }
        },
        dismiss = {
            if (holder.isRemoving) InlineProgress()
            NeutralTextButton(
                onClick = { holder.dismissPendingRemoval() },
                enabled = !holder.isRemoving,
                size = ButtonSize.Regular,
            ) {
                Text(strings.cancel)
            }
        },
        confirm = {
            DangerButton(
                onClick = { holder.confirmPendingRemoval() },
                enabled = !holder.isRemoving,
                size = ButtonSize.Regular,
            ) {
                Text(if (requiresForce) strings.forceRemove else strings.delete)
            }
        },
    ) {
        val lock = holder.pendingRemovalLock
        Text(
            when {
                lock != null -> strings.forceRemoveLockedMessage(worktree.path, lock.reason)
                requiresForce -> strings.forceRemoveMessage(worktree.path)
                else -> strings.removeWorktreeMessage(worktree.path)
            },
        )
        holder.removeError?.let { error -> ErrorNotice(error = error, onDismiss = null) }
        worktree.branch?.let { branch ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = holder.pendingRemovalDeleteBranch,
                    onCheckedChange = { holder.setPendingRemovalDeleteBranch(it) },
                    modifier = Modifier.handPointer(),
                )
                Text(strings.alsoDeleteLocalBranch(branch))
            }
        }
    }
}

@Composable
private fun WorktreeContent(
    board: WorktreeBoard,
    onOpenProjectSettings: (Project) -> Unit,
    onSeeAll: () -> Unit,
    onOpenMachine: (MachineResource) -> Unit,
    modifier: Modifier = Modifier,
    onOpenGroup: (GroupTag) -> Unit = {},
) {
    BoxWithConstraints(modifier) {
        val narrow = maxWidth < WorktreeDetailOverlayThreshold
        val selected = board.selectedWorktree
        val list: @Composable (Modifier) -> Unit = { listModifier ->
            if (board.isNothingActive) {
                NothingActive(onNewWorktree = { board.openCreateDialog() }, onSeeAll = onSeeAll, modifier = listModifier)
            } else if (board.hasNoMatch) {
                NoMatch(onClear = board::clearTextFilter, modifier = listModifier)
            } else {
                WorktreeListPane(board, onOpenProjectSettings, onOpenMachine, listModifier, onOpenGroup)
            }
        }
        if (narrow && selected != null) {
            Box(Modifier.fillMaxSize()) {
                list(Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ShogunTheme.colors.scrim)
                        .clickable(onClick = board::clearSelection),
                )
                SelectedDetail(board, selected, Modifier.align(Alignment.CenterEnd).width(WorktreeDetailWidth))
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                list(Modifier.weight(1f))
                VerticalDivider()
                if (selected != null) {
                    SelectedDetail(board, selected, Modifier.width(WorktreeDetailWidth))
                } else if (!narrow) {
                    ViewSummary(board, modifier = Modifier.width(WorktreeDetailWidth))
                }
            }
        }
    }
}

@Composable
private fun SelectedDetail(board: WorktreeBoard, selected: SelectedWorktree, modifier: Modifier) {
    WorktreeDetailPane(
        worktree = selected.worktree,
        projectConfig = selected.project.config,
        viewModel = selected.holder,
        projectName = selected.project.name.takeIf { !board.isFiltered },
        projectId = selected.project.id,
        desktopTurn = board.desktopTurn,
        openWithRequests = board.openWithRequests,
        modifier = modifier,
    )
}

@Composable
private fun ViewSummary(board: WorktreeBoard, modifier: Modifier) {
    val holders = board.shownProjects.mapNotNull { board.holder(it.id) }
    ProjectSummary(
        worktreeCount = holders.sumOf { it.worktrees.size },
        totalSize = holders.sumOf { it.totalSize },
        cleanupCandidateCount = holders.sumOf { it.cleanupCandidates.size },
        modifier = modifier,
        isLoading = holders.any { it.loadState == ProjectLoadState.Loading },
    )
}

@Composable
private fun NothingActive(onNewWorktree: () -> Unit, onSeeAll: () -> Unit, modifier: Modifier) {
    val strings = LocalStrings.current
    EmptyState(
        icon = Icons.Default.Inbox,
        title = strings.nothingActiveTitle,
        message = strings.nothingActiveMessage,
        action = StateAction(strings.newWorktree, onNewWorktree),
        link = StateAction(strings.seeAllWorktrees, onSeeAll),
        modifier = modifier,
    )
}

@Composable
private fun NoMatch(onClear: () -> Unit, modifier: Modifier) {
    val strings = LocalStrings.current
    EmptyState(
        icon = Icons.Default.SearchOff,
        message = strings.noWorktreeMatches,
        action = StateAction(strings.clearFilter, onClear),
        modifier = modifier,
    )
}

private val WorktreeDetailWidth = ShogunLayout.inspectorWidth
private val WorktreeDetailOverlayThreshold = ShogunLayout.compactBreakpoint

private fun Strings.successMessage(success: WorktreeSuccess): String = when (success) {
    is WorktreeSuccess.Created ->
        if (success.commandsSkipped) worktreeCreatedCommandsSkipped(File(success.path).name) else worktreeCreated(File(success.path).name)
    WorktreeSuccess.RepairedAfterMove -> worktreesRepairedAfterMove
    is WorktreeSuccess.BranchDeleted -> branchDeleted(success.branch)
    is WorktreeSuccess.OpenedInHerdr -> {
        val name = File(success.path).name
        when (success.outcome) {
            HerdrOutcome.OPENED -> openedInHerdr(name)
            HerdrOutcome.STARTED_AGENT -> agentStartedInHerdr(name)
            HerdrOutcome.FOCUSED -> focusedInHerdr(name)
        }
    }
    is WorktreeSuccess.OpenedInOrca -> {
        val name = File(success.path).name
        when (success.outcome) {
            OrcaOutcome.STARTED_AGENT -> agentStartedInOrca(name)
            OrcaOutcome.FOCUSED -> focusedInOrca(name)
        }
    }
    is WorktreeSuccess.CleanedUp -> listOfNotNull(
        worktreesCleanedUp(success.removedCount),
        if (success.stoppedDaemons + success.keptDaemons > 0) {
            daemonsStopped(success.stoppedDaemons, success.keptDaemons, failed = 0)
        } else {
            null
        },
        success.stoppedEmulators.takeIf { it > 0 }?.let(::emulatorsStopped),
        success.deletedSimulators.takeIf { it > 0 }?.let(::simulatorsDeleted),
        success.deletedDerivedDataFolders.takeIf { it > 0 }?.let {
            derivedDataDeleted(it, formatSize(success.derivedDataBytesFreed))
        },
        success.takenDownComposeProjects.takeIf { it > 0 }?.let(::composeProjectsTakenDown),
        success.removedComposeResources.takeIf { it > 0 }?.let(::composeResourcesRemoved),
    ).joinToString(" · ")
}

private fun PendingSuccess.action(strings: Strings, board: WorktreeBoard): MessageAction? = when (success) {
    is WorktreeSuccess.Created -> projectId?.let(board::holder)?.let { holder ->
        MessageAction(strings.copyLaunchCommand) { holder.copyLastCreatedLaunchCommand() }
    }
    else -> null
}
