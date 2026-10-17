package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.usecase.classifyOrigin
import app.luxion.shogunai.ui.components.IconAction
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.Notice
import app.luxion.shogunai.ui.components.NoticeKind
import app.luxion.shogunai.ui.components.PrimaryButton
import app.luxion.shogunai.ui.components.SecondaryButton
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.onNewItemShortcut
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.i18n.worktreeError
import app.luxion.shogunai.ui.shell.LocalAppMessages
import app.luxion.shogunai.ui.shell.MessageAction
import java.io.File

private val DetailPaneWidth = 360.dp
private val OverlayThreshold = 760.dp
private val ScrimAlpha = 0.32f
private val ButtonIconSize = 18.dp

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
    val listFocus = remember { FocusRequester() }
    LaunchedEffect(viewModel.isDialogOpen) {
        if (!viewModel.isDialogOpen) runCatching { listFocus.requestFocus() }
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .onNewItemShortcut(enabled = !viewModel.isCreateDialogOpen) { viewModel.openCreateDialog() }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape && viewModel.selectedWorktreePath != null && !viewModel.isDialogOpen) {
                    viewModel.clearSelection()
                    true
                } else {
                    false
                }
            },
    ) {
        WorktreeHeader(projectName, viewModel, onOpenProjectSettings)

        viewModel.error?.let { error ->
            Notice(
                kind = NoticeKind.ERROR,
                message = strings.worktreeError(error),
                onDismiss = { viewModel.dismissError() },
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
        }

        viewModel.cleanupIssues?.let { report ->
            Box(modifier = Modifier.padding(horizontal = Spacing.md)) {
                CleanupIssues(report, onDismiss = { viewModel.dismissCleanupIssues() })
            }
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val overlay = maxWidth < OverlayThreshold
            val selected = viewModel.selectedWorktree
            if (overlay) {
                WorktreeListPane(projectConfig, viewModel, listFocus, Modifier.fillMaxSize())
                if (selected != null) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .background(Color.Black.copy(alpha = ScrimAlpha))
                            .clickable { viewModel.clearSelection() },
                    )
                    Surface(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().widthIn(max = DetailPaneWidth).fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        shadowElevation = 8.dp,
                    ) {
                        WorktreeDetailPane(
                            worktree = selected,
                            origin = classifyOrigin(selected, projectConfig, viewModel.herdrWorktreesRoot),
                            viewModel = viewModel,
                            onClose = { viewModel.clearSelection() },
                        )
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    WorktreeListPane(projectConfig, viewModel, listFocus, Modifier.weight(1f).fillMaxHeight())
                    VerticalDivider(modifier = Modifier.fillMaxHeight())
                    Box(modifier = Modifier.width(DetailPaneWidth).fillMaxHeight()) {
                        if (selected != null) {
                            WorktreeDetailPane(
                                worktree = selected,
                                origin = classifyOrigin(selected, projectConfig, viewModel.herdrWorktreesRoot),
                                viewModel = viewModel,
                                onClose = null,
                            )
                        } else {
                            ProjectSummary(viewModel)
                        }
                    }
                }
            }
        }
    }

    if (viewModel.isCreateDialogOpen) {
        CreateWorktreeDialog(viewModel)
    }

    if (viewModel.isCleanupDialogOpen) {
        CleanupDialog(viewModel)
    }

    viewModel.worktreePendingRemoval?.let { worktree ->
        WorktreeRemovalDialog(worktree, viewModel)
    }
}

@Composable
private fun WorktreeHeader(projectName: String, viewModel: WorktreeViewModel, onOpenProjectSettings: () -> Unit) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(projectName, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f, fill = false))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (viewModel.isLoading) {
                InlineProgress()
            }
            PrimaryButton(onClick = { viewModel.openCreateDialog() }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.padding(end = Spacing.sm).size(ButtonIconSize),
                )
                Text(strings.newWorktree)
            }
            SecondaryButton(
                onClick = { viewModel.openCleanup() },
                enabled = viewModel.canCleanUp && !viewModel.isCleaningUp,
            ) { Text(strings.cleanUp) }
            IconAction(Icons.Default.Settings, strings.projectConfigTitle, onClick = onOpenProjectSettings)
            IconAction(Icons.Default.Refresh, strings.reload, onClick = { viewModel.refresh() }, enabled = !viewModel.isLoading)
        }
    }
}

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
