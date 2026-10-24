package app.luxion.shogunai.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.usecase.CommandLineToolState
import app.luxion.shogunai.ui.components.DangerButton
import app.luxion.shogunai.ui.components.ErrorNotice
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.Notice
import app.luxion.shogunai.ui.components.NoticeKind
import app.luxion.shogunai.ui.components.PrimaryButton
import app.luxion.shogunai.ui.components.SecondaryButton
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.worktree.OpenKind
import app.luxion.shogunai.ui.worktree.WorktreeBoard
import app.luxion.shogunai.ui.worktree.WorktreeRef
import kotlinx.coroutines.launch

private val TourDialogWidth = 520.dp

@Composable
fun TourDialog(
    state: TourState,
    hooks: ClaudeHooksState,
    onShowProject: (Project) -> Unit,
    agentChoices: @Composable (project: Project, onLaunched: () -> Unit) -> Unit,
    os: HostOs = HostOs.current(),
) {
    val step = state.step ?: return
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (step != TourStep.CREATING) state.close() },
        modifier = Modifier.widthIn(max = TourDialogWidth),
        title = { Text(strings.tourDialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                when (step) {
                    TourStep.EXISTING -> Text(strings.tourExistingMessage, style = MaterialTheme.typography.bodyMedium)
                    TourStep.WELCOME -> Text(strings.tourWelcomeMessage(state.folder), style = MaterialTheme.typography.bodyMedium)
                    TourStep.TOOL -> {
                        Text(strings.tourToolMessage, style = MaterialTheme.typography.bodyMedium)
                        CommandLineToolPrerequisite(hooks, os, showWhenReady = true)
                    }
                    TourStep.CREATING -> if (state.error == null) Text(strings.tourCreating, style = MaterialTheme.typography.bodyMedium)
                    TourStep.OPEN_AGENT -> state.project?.let { project ->
                        LaunchedEffect(project.id) { onShowProject(project) }
                        Text(strings.tourOpenAgentMessage, style = MaterialTheme.typography.bodyMedium)
                        agentChoices(project, state::close)
                    }
                }
                state.error?.let { ErrorNotice(error = it, onDismiss = state::dismissError) }
            }
        },
        confirmButton = {
            when (step) {
                TourStep.EXISTING -> FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    state.project?.let { project ->
                        SecondaryButton(onClick = {
                            state.close()
                            onShowProject(project)
                        }) { Text(strings.tourOpenExisting) }
                    }
                    PrimaryButton(onClick = { scope.launch { state.restart() } }) { Text(strings.tourRestart) }
                }
                TourStep.WELCOME -> PrimaryButton(onClick = { scope.launch { state.continueFromWelcome() } }) { Text(strings.tourStart) }
                TourStep.TOOL -> {
                    val ready = hooks.toolStatus?.state.let { it == CommandLineToolState.AVAILABLE || it == CommandLineToolState.INSTALLED_NOT_ON_PATH }
                    PrimaryButton(onClick = { scope.launch { state.continueFromTool() } }) {
                        Text(if (ready) strings.tourContinue else strings.tourSkip)
                    }
                }
                TourStep.CREATING -> if (state.error != null) {
                    PrimaryButton(onClick = { scope.launch { state.retry() } }) { Text(strings.tourRetry) }
                }
                TourStep.OPEN_AGENT -> Unit
            }
        },
        dismissButton = {
            when (step) {
                TourStep.CREATING -> if (state.error != null) NeutralTextButton(onClick = state::close) { Text(strings.cancel) }
                TourStep.OPEN_AGENT -> NeutralTextButton(onClick = state::close) { Text(strings.tourLater) }
                else -> NeutralTextButton(onClick = state::close) { Text(strings.cancel) }
            }
        },
    )
}

@Composable
fun TourAgentChoices(board: WorktreeBoard, project: Project, onLaunched: () -> Unit, onCopied: () -> Unit) {
    val strings = LocalStrings.current
    val holder = board.holder(project.id)
    val main = holder?.worktrees?.firstOrNull { it.isMain }
    if (holder == null || main == null) {
        Text(strings.tourLoadingWorktrees, style = MaterialTheme.typography.bodySmall)
        return
    }
    LaunchedEffect(project.id, main.path) { board.revealInAllWorktrees(WorktreeRef(project.id, main.path)) }
    val openActions = holder.openActions(main, strings).filter { it.kind == OpenKind.TERMINAL || it.kind == OpenKind.CLAUDE_DESKTOP }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            openActions.filter { it.kind == OpenKind.TERMINAL }.forEach { action ->
                PrimaryButton(onClick = {
                    action.onClick()
                    onLaunched()
                }) { Text(action.label) }
            }
            SecondaryButton(onClick = {
                holder.openInHerdr(main)
                onLaunched()
            }) { Text(holder.launchLabel(strings.openInHerdr, main, strings)) }
            openActions.filter { it.kind == OpenKind.CLAUDE_DESKTOP }.forEach { action ->
                SecondaryButton(onClick = {
                    action.onClick()
                    onLaunched()
                }) { Text(action.label) }
            }
            SecondaryButton(onClick = {
                holder.copyLaunchCommand(main, onCopied)
                onLaunched()
            }) { Text(holder.launchLabel(strings.copyLaunchCommand, main, strings)) }
        }
        if (openActions.any { it.kind == OpenKind.CLAUDE_DESKTOP }) {
            Text(strings.tourClaudeDesktopHint, style = MaterialTheme.typography.bodySmall)
        }
        Text(strings.tourAgentsNeeded, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun TourNotice(state: TourState, hasProjects: Boolean, modifier: Modifier = Modifier) {
    if (!state.showsNotice(hasProjects)) return
    val strings = LocalStrings.current
    Notice(
        kind = NoticeKind.INFO,
        message = strings.tourNoticeMessage,
        onDismiss = state::dismissNotice,
        modifier = modifier,
        details = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                PrimaryButton(onClick = state::start) { Text(strings.tourTakeTour) }
                NeutralTextButton(onClick = state::dismissNotice) { Text(strings.claudeHooksNoticeDismiss) }
            }
        },
    )
}

@Composable
fun TourBanner(
    state: TourState,
    project: Project,
    onEnded: () -> Unit,
    agentChoices: @Composable (project: Project, onLaunched: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    var choosing by remember(project.id) { mutableStateOf(false) }
    Notice(
        kind = NoticeKind.INFO,
        message = strings.tourBannerMessage,
        onDismiss = null,
        modifier = modifier,
        details = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (choosing) {
                    agentChoices(project) { choosing = false }
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        PrimaryButton(onClick = { choosing = true }) { Text(strings.tourOpenAgent) }
                        NeutralTextButton(onClick = state::requestEnd) { Text(strings.tourEnd) }
                    }
                }
                if (state.step == null) state.error?.let { ErrorNotice(error = it, onDismiss = state::dismissError) }
            }
        },
    )
    if (state.isEndRequested) {
        AlertDialog(
            onDismissRequest = state::cancelEnd,
            title = { Text(strings.tourEndTitle) },
            text = { Text(strings.tourEndMessage(state.folder)) },
            confirmButton = {
                DangerButton(onClick = { scope.launch { state.confirmEnd().onSuccess { onEnded() } } }) { Text(strings.tourEnd) }
            },
            dismissButton = { NeutralTextButton(onClick = state::cancelEnd) { Text(strings.cancel) } },
        )
    }
}
