package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.Grouping
import app.luxion.shogunai.domain.model.HerdrOutcome
import app.luxion.shogunai.domain.model.WorktreeSort
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.ui.components.DangerButton
import app.luxion.shogunai.ui.components.DangerTextButton
import app.luxion.shogunai.ui.components.DropdownSelector
import app.luxion.shogunai.ui.components.IconAction
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.PrimaryButton
import app.luxion.shogunai.ui.components.SecondaryButton
import app.luxion.shogunai.ui.components.SegmentedSelector
import app.luxion.shogunai.ui.components.ErrorNotice
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.trackTextInputFocus
import app.luxion.shogunai.ui.components.handPointer
import app.luxion.shogunai.ui.components.onNewItemShortcut
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

@OptIn(ExperimentalLayoutApi::class)
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
    val filteredProject = board.shownProjects.singleOrNull()?.takeIf { board.isProjectView }
    val multirepo = board.shownMultirepo
    val needsReview = multirepo?.multirepo?.approved == false

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onNewItemShortcut(enabled = !board.isCreateDialogOpen && board.newChange == null) {
                if (board.isMultirepoView) board.openNewChange() else board.openCreateDialog()
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                multirepo?.name ?: filteredProject?.name ?: if (board.isActiveView) strings.activeWorktrees else strings.allWorktrees,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (board.shownProjects.any { it.id.let(board::holder)?.isLoading == true }) InlineProgress()
            SecondaryButton(onClick = { board.refreshShown() }) { Text(strings.reload) }
            filteredProject?.let { project ->
                IconAction(
                    icon = Icons.Default.Settings,
                    label = strings.projectConfigTitle,
                    onClick = { onOpenProjectSettings(project) },
                )
            }
            if (multirepo != null) {
                board.multirepoAgentsOf(multirepo.folder).forEach { agent ->
                    Text(strings.agentLabel(agent.name, agent.state), style = MaterialTheme.typography.bodySmall)
                }
                IconAction(icon = Icons.Default.Settings, label = strings.multirepoSettingsTitle, onClick = onOpenMultirepoSettings)
                SecondaryButton(onClick = board::openMultirepoAgent, enabled = !needsReview && !board.isOpeningMultirepoAgent) {
                    Text(strings.openMultirepoAgent)
                }
            }
            DangerTextButton(onClick = { board.openCleanup() }, enabled = !needsReview && board.canCleanUp()) { Text(strings.cleanUp) }
            if (multirepo != null) {
                PrimaryButton(onClick = board::openNewChange, enabled = !needsReview && multirepo.aliases.isNotEmpty()) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(ButtonIconSize))
                    Text(strings.newChange, modifier = Modifier.padding(start = Spacing.sm))
                }
            } else {
                PrimaryButton(onClick = { board.openCreateDialog() }, enabled = board.projects.isNotEmpty()) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(ButtonIconSize))
                    Text(strings.newWorktree, modifier = Modifier.padding(start = Spacing.sm))
                }
            }
        }
        (board.multirepoNotice as? MultirepoNotice.Failed)?.let { notice ->
            ErrorNotice(
                error = notice.error,
                onDismiss = board::dismissMultirepoNotice,
                modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.sm),
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (!board.isProjectView && !board.isMultirepoView) {
                ViewProjectFilter(board.projects, board.viewProjectFilter, board::updateViewProjectFilter)
            }
            TextFilterField(board)
            if (!board.isProjectView) GroupingSelector(board)
            SortSelector(board.sort, board::updateSort)
            if (board.isActiveView && board.isReadingActivity) {
                InlineProgress()
                Text(strings.readingWorktreeStatus, style = MaterialTheme.typography.bodySmall)
            }
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
private fun GroupingSelector(board: WorktreeBoard) {
    val strings = LocalStrings.current
    SegmentedSelector(
        options = board.groupingOptions,
        selected = board.grouping,
        onSelect = board::updateGrouping,
        label = {
            when (it) {
                Grouping.BY_STATE -> strings.groupByState
                Grouping.BY_PROJECT -> strings.groupByProject
                Grouping.BY_CHANGE -> strings.groupByChange
            }
        },
    )
}

@Composable
internal fun SortSelector(selected: WorktreeSort, onSelect: (WorktreeSort) -> Unit) {
    val strings = LocalStrings.current
    SegmentedSelector(
        options = WorktreeSort.entries,
        selected = selected,
        onSelect = onSelect,
        label = { if (it == WorktreeSort.NAME) strings.sortByName else strings.sortByLastActivity },
    )
}

@Composable
internal fun ViewProjectFilter(projects: List<Project>, selectedId: String?, onSelect: (String?) -> Unit) {
    val strings = LocalStrings.current
    val options = listOf<Project?>(null) + projects
    DropdownSelector(
        options = options,
        selected = projects.firstOrNull { it.id == selectedId },
        onSelect = { onSelect(it?.id) },
        label = { it?.name ?: strings.allProjects },
        placeholder = strings.allProjects,
        modifier = Modifier.width(FilterFieldWidth),
    )
}

@Composable
private fun TextFilterField(board: WorktreeBoard) {
    val strings = LocalStrings.current
    val focusRequester = remember { FocusRequester() }
    val findRequestsOnEntry = remember { board.findRequests }
    LaunchedEffect(board.findRequests) {
        if (board.findRequests != findRequestsOnEntry) focusRequester.requestFocus()
    }
    OutlinedTextField(
        value = board.textFilter,
        onValueChange = board::updateTextFilter,
        placeholder = { Text(strings.filterWorktreesPlaceholder, style = MaterialTheme.typography.bodySmall) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(ButtonIconSize)) },
        trailingIcon = if (board.textFilter.isNotEmpty()) {
            { IconAction(icon = Icons.Default.Close, label = strings.clearFilter, onClick = board::clearTextFilter) }
        } else {
            null
        },
        textStyle = MaterialTheme.typography.bodySmall,
        singleLine = true,
        modifier = Modifier
            .width(FilterFieldWidth)
            .focusRequester(focusRequester)
            .trackTextInputFocus(),
    )
}

@Composable
private fun ChooseTerminalDialog(holder: ProjectWorktrees) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = { holder.dismissTerminalChoice() },
        title = { Text(strings.chooseTerminalTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(strings.chooseTerminalMessage)
                holder.tools.availableTerminals.forEach { terminal ->
                    SecondaryButton(onClick = { holder.chooseTerminal(terminal) }, modifier = Modifier.fillMaxWidth()) {
                        Text(terminal.displayName)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            NeutralTextButton(onClick = { holder.dismissTerminalChoice() }) {
                Text(strings.cancel)
            }
        },
    )
}

@Composable
private fun ForceDeleteBranchDialog(holder: ProjectWorktrees) {
    val strings = LocalStrings.current
    val branch = holder.keptBranchNotice?.branch ?: return
    AlertDialog(
        onDismissRequest = { holder.cancelForcedBranchDeletion() },
        title = { Text(strings.deleteBranchAnywayTitle) },
        text = { Text(strings.deleteBranchAnywayMessage(branch)) },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (holder.isDeletingBranch) {
                    InlineProgress(modifier = Modifier.padding(end = Spacing.sm))
                }
                DangerButton(onClick = { holder.confirmForcedBranchDeletion() }, enabled = !holder.isDeletingBranch) {
                    Text(strings.delete)
                }
            }
        },
        dismissButton = {
            NeutralTextButton(onClick = { holder.cancelForcedBranchDeletion() }, enabled = !holder.isDeletingBranch) {
                Text(strings.cancel)
            }
        },
    )
}

@Composable
private fun RemoveWorktreeDialog(holder: ProjectWorktrees) {
    val strings = LocalStrings.current
    val worktree = holder.worktreePendingRemoval ?: return
    val requiresForce = holder.pendingRemovalRequiresForce
    AlertDialog(
        onDismissRequest = { holder.dismissPendingRemoval() },
        title = { Text(if (requiresForce) strings.forceRemoveTitle else strings.removeWorktreeTitle) },
        text = {
            Column {
                Text(
                    if (requiresForce) {
                        strings.forceRemoveMessage(worktree.path)
                    } else {
                        strings.removeWorktreeMessage(worktree.path)
                    },
                )
                if (holder.pendingRemovalOpenInHerdr) {
                    Text(
                        strings.herdrWorkspaceWillClose,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
                holder.removeError?.let { error ->
                    ErrorNotice(error = error, onDismiss = null, modifier = Modifier.padding(top = Spacing.sm))
                }
                worktree.branch?.let { branch ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = Spacing.sm),
                    ) {
                        Checkbox(
                            checked = holder.pendingRemovalDeleteBranch,
                            onCheckedChange = { holder.setPendingRemovalDeleteBranch(it) },
                            modifier = Modifier.handPointer(),
                        )
                        Text(strings.alsoDeleteLocalBranch(branch))
                    }
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (holder.isRemoving) {
                    InlineProgress(modifier = Modifier.padding(end = Spacing.sm))
                }
                DangerButton(
                    onClick = { holder.confirmPendingRemoval() },
                    enabled = !holder.isRemoving,
                ) {
                    Text(if (requiresForce) strings.forceRemove else strings.delete)
                }
            }
        },
        dismissButton = {
            NeutralTextButton(
                onClick = { holder.dismissPendingRemoval() },
                enabled = !holder.isRemoving,
            ) {
                Text(strings.cancel)
            }
        },
    )
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
                        .background(WorktreeOverlayScrim)
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
        canCleanUp = board.canCleanUp(),
        onCleanUp = { board.openCleanup() },
        modifier = modifier,
    )
}

@Composable
private fun NothingActive(onNewWorktree: () -> Unit, onSeeAll: () -> Unit, modifier: Modifier) {
    val strings = LocalStrings.current
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
    ) {
        Text(strings.nothingActiveTitle, style = MaterialTheme.typography.headlineSmall)
        Text(
            strings.nothingActiveMessage,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PrimaryButton(onClick = onNewWorktree) { Text(strings.newWorktree) }
            SecondaryButton(onClick = onSeeAll) { Text(strings.seeAllWorktrees) }
        }
    }
}

@Composable
private fun NoMatch(onClear: () -> Unit, modifier: Modifier) {
    val strings = LocalStrings.current
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
    ) {
        Text(
            strings.noWorktreeMatches,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SecondaryButton(onClick = onClear) { Text(strings.clearFilter) }
    }
}

private val ButtonIconSize = 18.dp
private val FilterFieldWidth = 240.dp
private val WorktreeDetailWidth = 360.dp
private val WorktreeDetailOverlayThreshold = 760.dp
private val WorktreeOverlayScrim = Color(0x66000000)

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
