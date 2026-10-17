package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.ui.components.DangerButton
import app.luxion.shogunai.ui.components.DangerTextButton
import app.luxion.shogunai.ui.components.IconAction
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.Notice
import app.luxion.shogunai.ui.components.NoticeKind
import app.luxion.shogunai.ui.components.PrimaryButton
import app.luxion.shogunai.ui.components.SecondaryButton
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.handPointer
import app.luxion.shogunai.ui.components.onNewItemShortcut
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.i18n.worktreeError
import app.luxion.shogunai.ui.shell.LocalAppMessages
import app.luxion.shogunai.ui.shell.MessageAction
import java.io.File

@Composable
fun WorktreeScreen(
    projectName: String,
    projectConfig: ProjectConfig,
    viewModel: WorktreeViewModel,
    onOpenProjectSettings: () -> Unit,
) {
    val strings = LocalStrings.current
    DisposableEffect(viewModel) {
        viewModel.startWatching()
        onDispose { viewModel.stopWatching() }
    }
    val messages = LocalAppMessages.current
    LaunchedEffect(viewModel.success) {
        val success = viewModel.success ?: return@LaunchedEffect
        messages.show(strings.successMessage(success), success.action(strings, viewModel))
        viewModel.dismissSuccess()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onNewItemShortcut(enabled = !viewModel.isCreateDialogOpen) { viewModel.openCreateDialog() },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.md),
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
                    modifier = Modifier.padding(end = Spacing.sm),
                ) { Text(strings.reload) }
                IconAction(
                    icon = Icons.Default.Settings,
                    label = strings.projectConfigTitle,
                    onClick = onOpenProjectSettings,
                    modifier = Modifier.padding(end = Spacing.sm),
                )
                DangerTextButton(
                    onClick = { viewModel.openCleanup() },
                    enabled = viewModel.canCleanUp && !viewModel.isCleaningUp,
                    modifier = Modifier.padding(end = Spacing.sm),
                ) { Text(strings.cleanUp) }
                PrimaryButton(onClick = { viewModel.openCreateDialog() }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(ButtonIconSize))
                    Text(strings.newWorktree, modifier = Modifier.padding(start = Spacing.sm))
                }
            }
        }

        viewModel.error?.let { error ->
            Notice(
                kind = NoticeKind.ERROR,
                message = strings.worktreeError(error),
                onDismiss = { viewModel.dismissError() },
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
        }

        viewModel.cleanupIssues?.let { report ->
            CleanupIssues(report, onDismiss = { viewModel.dismissCleanupIssues() })
        }

        WorktreeContent(projectConfig, viewModel, modifier = Modifier.weight(1f).fillMaxWidth())
    }

    if (viewModel.isCreateDialogOpen) {
        CreateWorktreeDialog(viewModel)
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
private fun WorktreeContent(
    projectConfig: ProjectConfig,
    viewModel: WorktreeViewModel,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val narrow = maxWidth < WorktreeDetailOverlayThreshold
        val selected = viewModel.selectedWorktree
        if (narrow && selected != null) {
            Box(Modifier.fillMaxSize()) {
                WorktreeListPane(viewModel, projectConfig, Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(WorktreeOverlayScrim)
                        .clickable(onClick = viewModel::clearSelection),
                )
                WorktreeDetailPane(
                    worktree = selected,
                    projectConfig = projectConfig,
                    viewModel = viewModel,
                    modifier = Modifier.align(Alignment.CenterEnd).width(WorktreeDetailWidth),
                )
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                WorktreeListPane(viewModel, projectConfig, Modifier.weight(1f))
                VerticalDivider()
                if (selected != null) {
                    WorktreeDetailPane(
                        worktree = selected,
                        projectConfig = projectConfig,
                        viewModel = viewModel,
                        modifier = Modifier.width(WorktreeDetailWidth),
                    )
                } else if (!narrow) {
                    ProjectSummary(viewModel, modifier = Modifier.width(WorktreeDetailWidth))
                }
            }
        }
    }
}

private val ButtonIconSize = 18.dp
private val WorktreeDetailWidth = 360.dp
private val WorktreeDetailOverlayThreshold = 760.dp
private val WorktreeOverlayScrim = Color(0x66000000)

private fun Strings.successMessage(success: WorktreeSuccess): String = when (success) {
    is WorktreeSuccess.Created -> worktreeCreated(File(success.path).name)
    WorktreeSuccess.RepairedAfterMove -> worktreesRepairedAfterMove
    is WorktreeSuccess.OpenedInHerdr -> {
        val name = File(success.path).name
        if (success.focused) focusedInHerdr(name) else openedInHerdr(name)
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
    ).joinToString(" · ")
}

private fun WorktreeSuccess.action(strings: Strings, viewModel: WorktreeViewModel): MessageAction? = when (this) {
    is WorktreeSuccess.Created -> MessageAction(strings.copyLaunchCommand) { viewModel.copyLastCreatedLaunchCommand() }
    else -> null
}
