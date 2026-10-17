package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeEmulator
import app.luxion.shogunai.domain.model.WorktreeOrigin
import app.luxion.shogunai.domain.model.WorktreeSimulator
import app.luxion.shogunai.domain.usecase.classifyOrigin
import app.luxion.shogunai.domain.usecase.isInactive
import app.luxion.shogunai.ui.components.DangerButton
import app.luxion.shogunai.ui.components.DangerTextButton
import app.luxion.shogunai.ui.components.DropdownSelector
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.Notice
import app.luxion.shogunai.ui.components.NoticeKind
import app.luxion.shogunai.ui.components.PrimaryButton
import app.luxion.shogunai.ui.components.SecondaryButton
import app.luxion.shogunai.ui.components.SectionCard
import app.luxion.shogunai.ui.components.SegmentedSelector
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.StatusChip
import app.luxion.shogunai.ui.components.handPointer
import app.luxion.shogunai.ui.components.onEnterKey
import app.luxion.shogunai.ui.components.onNewItemShortcut
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.i18n.worktreeError
import app.luxion.shogunai.ui.theme.ShogunTheme
import kotlinx.coroutines.delay
import java.time.Instant

private fun CreateMode.label(strings: Strings): String = when (this) {
    CreateMode.NEW_BRANCH -> strings.newBranchMode
    CreateMode.EXISTING_BRANCH -> strings.existingBranchMode
}

@Composable
fun WorktreeScreen(
    projectName: String,
    projectConfig: ProjectConfig,
    viewModel: WorktreeViewModel,
) {
    val strings = LocalStrings.current
    DisposableEffect(viewModel) {
        viewModel.startWatching()
        onDispose { viewModel.stopWatching() }
    }
    LaunchedEffect(viewModel.createMode) {
        if (viewModel.createMode == CreateMode.EXISTING_BRANCH) {
            viewModel.loadLocalBranches()
        }
    }

    val branchGroupExpansion = remember { mutableStateMapOf<String, Boolean>() }
    var isCreateSectionExpanded by remember { mutableStateOf(true) }

    val isTaskIdValid = viewModel.taskId.isNotBlank() && viewModel.isTaskIdValid(viewModel.taskId)
    val createNewBranchWorktree = {
        viewModel.create(viewModel.taskId, viewModel.branchType)
        viewModel.updateTaskId("")
    }
    val createFromSelectedBranch = {
        viewModel.selectedBranch?.let { viewModel.createFromBranch(it) }
        viewModel.selectedBranch = null
    }
    val isCreateEnabled = when (viewModel.createMode) {
        CreateMode.NEW_BRANCH -> isTaskIdValid
        CreateMode.EXISTING_BRANCH -> viewModel.isSelectedBranchAvailable
    }
    val createWorktree = when (viewModel.createMode) {
        CreateMode.NEW_BRANCH -> createNewBranchWorktree
        CreateMode.EXISTING_BRANCH -> createFromSelectedBranch
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.md)
            .verticalScroll(rememberScrollState())
            .onNewItemShortcut(enabled = isCreateEnabled && !viewModel.isCreating, action = createWorktree),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(projectName, style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (viewModel.isLoading) {
                    InlineProgress(modifier = Modifier.padding(end = Spacing.sm))
                }
                SecondaryButton(
                    onClick = { viewModel.refresh() },
                    enabled = !viewModel.isLoading,
                ) { Text(strings.reload) }
            }
        }

        viewModel.error?.let { error ->
            Notice(
                kind = NoticeKind.ERROR,
                message = strings.worktreeError(error),
                onDismiss = { viewModel.dismissError() },
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }

        viewModel.success?.let { success ->
            val message = when (success) {
                is WorktreeSuccess.Created -> strings.worktreeCreated(success.path)
                WorktreeSuccess.RepairedAfterMove -> strings.worktreesRepairedAfterMove
                is WorktreeSuccess.CleanedUp -> listOfNotNull(
                    strings.worktreesCleanedUp(success.removedCount),
                    if (success.stoppedDaemons + success.keptDaemons > 0) {
                        strings.daemonsStopped(success.stoppedDaemons, success.keptDaemons, failed = 0)
                    } else {
                        null
                    },
                    success.stoppedEmulators.takeIf { it > 0 }?.let(strings::emulatorsStopped),
                    success.deletedSimulators.takeIf { it > 0 }?.let(strings::simulatorsDeleted),
                    success.deletedDerivedDataFolders.takeIf { it > 0 }?.let {
                        strings.derivedDataDeleted(it, formatSize(success.derivedDataBytesFreed))
                    },
                ).joinToString(" · ")
            }
            LaunchedEffect(success) {
                delay(4000)
                viewModel.dismissSuccess()
            }
            Notice(
                kind = NoticeKind.SUCCESS,
                message = message,
                onDismiss = { viewModel.dismissSuccess() },
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }

        SectionCard(
            title = strings.newWorktreeSection,
            modifier = Modifier.padding(top = Spacing.md),
            collapsible = true,
            expanded = isCreateSectionExpanded,
            onExpandedChange = { isCreateSectionExpanded = it },
        ) {
            SegmentedSelector(
                options = CreateMode.entries,
                selected = viewModel.createMode,
                onSelect = { viewModel.createMode = it },
                label = { it.label(strings) },
                enabled = !viewModel.isCreating,
            )
            when (viewModel.createMode) {
                CreateMode.NEW_BRANCH -> {
                    OutlinedTextField(
                        value = viewModel.taskId,
                        onValueChange = { viewModel.updateTaskId(it) },
                        label = { Text(strings.taskIdLabel) },
                        textStyle = ShogunTheme.typography.data,
                        singleLine = true,
                        supportingText = { Text(strings.taskIdHint) },
                        isError = viewModel.taskId.isNotBlank() && !viewModel.isTaskIdValid(viewModel.taskId),
                        modifier = Modifier.fillMaxWidth().onEnterKey(enabled = isTaskIdValid, action = createNewBranchWorktree),
                    )
                    if (viewModel.taskId.isNotBlank() && !viewModel.isTaskIdValid(viewModel.taskId)) {
                        Text(
                            strings.invalidTaskId,
                            color = ShogunTheme.status.danger.color,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    SegmentedSelector(
                        options = BranchType.entries,
                        selected = viewModel.branchType,
                        onSelect = { viewModel.branchType = it },
                        label = { it.prefix },
                        enabled = !viewModel.isCreating,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (viewModel.isCreating) {
                            InlineProgress(modifier = Modifier.padding(end = Spacing.sm))
                        }
                        PrimaryButton(
                            onClick = createWorktree,
                            enabled = isCreateEnabled && !viewModel.isCreating,
                        ) {
                            Text(strings.createWorktree)
                        }
                    }
                }
                CreateMode.EXISTING_BRANCH -> {
                    when {
                        viewModel.isLoadingBranches -> Row(verticalAlignment = Alignment.CenterVertically) {
                            InlineProgress(modifier = Modifier.padding(end = Spacing.sm))
                            Text(strings.loadingBranches)
                        }
                        viewModel.localBranches.isEmpty() -> Text(strings.noBranchesAvailable)
                        else -> DropdownSelector(
                            options = viewModel.localBranches,
                            selected = viewModel.localBranches.find { it.name == viewModel.selectedBranch },
                            onSelect = { viewModel.selectedBranch = it.name },
                            enabled = { !it.isCheckedOut },
                            label = { if (it.isCheckedOut) strings.branchAlreadyHasWorktree(it.name) else it.name },
                            placeholder = strings.selectBranchPlaceholder,
                            groupBy = { it.name.substringBefore('/', strings.otherBranchesGroup) },
                            expandedGroups = branchGroupExpansion,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (viewModel.isCreating) {
                            InlineProgress(modifier = Modifier.padding(end = Spacing.sm))
                        }
                        PrimaryButton(
                            onClick = createWorktree,
                            enabled = isCreateEnabled && !viewModel.isCreating,
                        ) {
                            Text(strings.createWorktree)
                        }
                    }
                }
            }
        }

        viewModel.cleanupIssues?.let { report ->
            CleanupIssues(report, onDismiss = { viewModel.dismissCleanupIssues() })
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(strings.worktreesTitle, style = MaterialTheme.typography.titleMedium)
                Text(
                    strings.worktreesSummary(viewModel.worktrees.size, formatSize(viewModel.totalSize)),
                    style = ShogunTheme.typography.dataSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
            DangerTextButton(
                onClick = { viewModel.openCleanup() },
                enabled = viewModel.canCleanUp && !viewModel.isCleaningUp,
            ) { Text(strings.cleanUp) }
        }
        viewModel.worktrees.forEach { worktree ->
            val status = viewModel.statuses[worktree.path]
            val agents = viewModel.agents?.get(worktree.path).orEmpty()
            WorktreeRow(
                worktree = worktree,
                status = status,
                mainBranchName = viewModel.mainBranch?.name,
                origin = classifyOrigin(worktree, projectConfig),
                agents = agents,
                size = viewModel.sizes[worktree.path],
                ports = viewModel.ports[worktree.path].orEmpty(),
                showEmulator = viewModel.isEmulatorRepository,
                emulator = viewModel.emulators[worktree.path],
                isStartingEmulator = worktree.path in viewModel.startingEmulators,
                onStartEmulator = { viewModel.startEmulator(worktree) },
                onStopEmulator = { viewModel.stopEmulator(worktree) },
                showSimulator = viewModel.isSimulatorRepository,
                simulator = viewModel.simulators[worktree.path],
                isStartingSimulator = worktree.path in viewModel.startingSimulators,
                onStartSimulator = { viewModel.startSimulator(worktree) },
                onStopSimulator = { viewModel.stopSimulator(worktree) },
                isInactive = isInactive(worktree, (status as? WorktreeStatusState.Loaded)?.status, agents, Instant.now()),
                isCopied = viewModel.copiedWorktreePath == worktree.path,
                onRemove = { viewModel.requestRemoval(worktree) },
                onCopyLaunchCommand = { viewModel.copyLaunchCommand(worktree) },
            )
            HorizontalDivider()
        }
        if (viewModel.isGradleRepository) {
            GradleDaemonsSection(viewModel, modifier = Modifier.padding(vertical = Spacing.lg))
        }
        if (viewModel.isXcodeRepository) {
            XcodeDerivedDataSection(viewModel, modifier = Modifier.padding(vertical = Spacing.lg))
        }
    }

    if (viewModel.isCleanupDialogOpen) {
        CleanupDialog(viewModel)
    }

    viewModel.worktreePendingRemoval?.let { worktree ->
        val requiresForce = viewModel.pendingRemovalRequiresForce
        AlertDialog(
            onDismissRequest = { viewModel.dismissPendingRemoval() },
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
                    worktree.branch?.let { branch ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = Spacing.sm),
                        ) {
                            Checkbox(
                                checked = viewModel.pendingRemovalDeleteBranch,
                                onCheckedChange = { viewModel.setPendingRemovalDeleteBranch(it) },
                                modifier = Modifier.handPointer(),
                            )
                            Text(strings.alsoDeleteLocalBranch(branch))
                        }
                    }
                }
            },
            confirmButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (viewModel.isRemoving) {
                        InlineProgress(modifier = Modifier.padding(end = Spacing.sm))
                    }
                    DangerButton(
                        onClick = { viewModel.confirmPendingRemoval() },
                        enabled = !viewModel.isRemoving,
                    ) {
                        Text(if (requiresForce) strings.forceRemove else strings.delete)
                    }
                }
            },
            dismissButton = {
                NeutralTextButton(
                    onClick = { viewModel.dismissPendingRemoval() },
                    enabled = !viewModel.isRemoving,
                ) {
                    Text(strings.cancel)
                }
            },
        )
    }
}

@Composable
private fun WorktreeRow(
    worktree: Worktree,
    status: WorktreeStatusState?,
    mainBranchName: String?,
    origin: WorktreeOrigin?,
    agents: List<RunningAgent>,
    size: Long?,
    ports: Map<String, Int>,
    showEmulator: Boolean,
    emulator: WorktreeEmulator?,
    isStartingEmulator: Boolean,
    onStartEmulator: () -> Unit,
    onStopEmulator: () -> Unit,
    showSimulator: Boolean,
    simulator: WorktreeSimulator?,
    isStartingSimulator: Boolean,
    onStartSimulator: () -> Unit,
    onStopSimulator: () -> Unit,
    isInactive: Boolean,
    isCopied: Boolean,
    onRemove: () -> Unit,
    onCopyLaunchCommand: () -> Unit,
) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Text(worktree.path, style = ShogunTheme.typography.data, maxLines = 1, overflow = TextOverflow.Ellipsis)
            WorktreeBranchLine(worktree, origin, agents)
            if (worktree.isPrunable) {
                StatusChip(WorktreeIndicator.FOLDER_MISSING.status(), strings.folderMissing)
            } else {
                WorktreeStatusLine(status, mainBranchName)
                WorktreeOverviewLine(size, isInactive, ports)
                if (showEmulator) EmulatorLine(emulator, isStartingEmulator, onStartEmulator, onStopEmulator)
                if (showSimulator) SimulatorLine(simulator, isStartingSimulator, onStartSimulator, onStopSimulator)
            }
        }
        Row {
            SecondaryButton(onClick = onCopyLaunchCommand, modifier = Modifier.padding(end = Spacing.sm)) {
                Icon(
                    imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.padding(end = Spacing.sm).size(ButtonIconSize),
                )
                Text(if (isCopied) strings.copied else strings.copyCommand)
            }
            if (!worktree.isMain) {
                DangerTextButton(onClick = onRemove) {
                    Text(strings.delete)
                }
            }
        }
    }
}

private val ButtonIconSize = 18.dp
