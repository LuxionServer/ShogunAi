package app.luxion.shogunai.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.HostOs
import app.luxion.shogunai.domain.usecase.CommandLineToolState
import app.luxion.shogunai.ui.components.ErrorNotice
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.Notice
import app.luxion.shogunai.ui.components.NoticeKind
import app.luxion.shogunai.ui.components.PrimaryButton
import app.luxion.shogunai.ui.components.SecondaryButton
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import kotlinx.coroutines.launch

@Composable
fun ClaudeHooksSection(state: ClaudeHooksState, os: HostOs = HostOs.current()) {
    val strings = LocalStrings.current
    Text(
        strings.claudeCodeLabel,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = Spacing.md),
    )
    state.status?.let { Text(strings.claudeHooksStatus(it.state), style = MaterialTheme.typography.bodyMedium) }
    CommandLineToolPrerequisite(state, os, showWhenReady = true)
    state.error?.let { ErrorNotice(error = it, onDismiss = state::dismissError) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        state.installAction?.let { action -> HooksActionButton(state, action, strings, prominent = true) }
        if (state.canRemove) {
            NeutralTextButton(onClick = { state.request(HooksAction.REMOVE) }) { Text(strings.claudeHooksRemove) }
        }
    }
    if (state.installAction != null && !state.canChangeHooks) {
        Text(strings.claudeHooksNeedsTool, style = MaterialTheme.typography.bodySmall)
    }
    state.pending?.let { ClaudeHooksConfirmDialog(it, state, strings) }
}

@Composable
private fun HooksActionButton(state: ClaudeHooksState, action: HooksAction, strings: Strings, prominent: Boolean) {
    val label = if (action == HooksAction.UPDATE) strings.claudeHooksUpdate else strings.claudeHooksInstall
    if (prominent) {
        PrimaryButton(onClick = { state.request(action) }, enabled = state.canChangeHooks) { Text(label) }
    } else {
        SecondaryButton(onClick = { state.request(action) }, enabled = state.canChangeHooks) { Text(label) }
    }
}

@Composable
private fun CommandLineToolPrerequisite(state: ClaudeHooksState, os: HostOs, showWhenReady: Boolean) {
    val status = state.toolStatus ?: return
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val needsAttention = state.toolNeedsAttention
    if (!needsAttention && !showWhenReady) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.padding(top = if (showWhenReady) Spacing.sm else 0.dp)) {
        if (showWhenReady) Text(strings.commandLineToolLabel, style = MaterialTheme.typography.labelLarge)
        Text(strings.commandLineToolStatus(status), style = MaterialTheme.typography.bodyMedium)
        if (needsAttention && showWhenReady) {
            Text(strings.commandLineToolNeededByHooks, style = MaterialTheme.typography.bodySmall)
        }
        if (status.state == CommandLineToolState.INSTALLED_NOT_ON_PATH && !status.restartNeeded) {
            Text(strings.commandLineToolPathHint(status, os), style = MaterialTheme.typography.bodySmall)
        }
        if (needsAttention && !status.canInstall) {
            Text(strings.commandLineToolManualSteps(os), style = MaterialTheme.typography.bodySmall)
        }
        state.toolInstallation?.let { Text(strings.commandLineToolInstalled(it), style = MaterialTheme.typography.bodySmall) }
        state.toolError?.let {
            ErrorNotice(error = it, onDismiss = state::dismissToolError)
            if (!status.canInstall || needsAttention) Text(strings.commandLineToolManualSteps(os), style = MaterialTheme.typography.bodySmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (needsAttention && status.canInstall && status.state != CommandLineToolState.INSTALLED_NOT_ON_PATH) {
                val label = if (status.state == CommandLineToolState.BROKEN) strings.commandLineToolRepair else strings.commandLineToolInstall
                PrimaryButton(onClick = { scope.launch { state.installTool() } }) { Text(label) }
            }
            if (status.installedByApp) {
                NeutralTextButton(onClick = { scope.launch { state.removeTool() } }) { Text(strings.commandLineToolRemove) }
            }
        }
    }
}

@Composable
private fun ClaudeHooksConfirmDialog(change: PendingHooksChange, state: ClaudeHooksState, strings: Strings) {
    AlertDialog(
        onDismissRequest = state::cancel,
        title = { Text(strings.claudeHooksDialogTitle(change.action)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(strings.claudeHooksChangesIn(change.path), style = MaterialTheme.typography.bodyMedium)
                change.preview.added.forEach { Text(strings.claudeHooksAdded(it), style = MaterialTheme.typography.bodySmall) }
                change.preview.replaced.forEach { Text(strings.claudeHooksReplaced(it), style = MaterialTheme.typography.bodySmall) }
                change.preview.removed.forEach { Text(strings.claudeHooksRemoved(it), style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { PrimaryButton(onClick = state::confirm) { Text(strings.claudeHooksConfirm) } },
        dismissButton = { NeutralTextButton(onClick = state::cancel) { Text(strings.claudeHooksCancel) } },
    )
}

@Composable
fun ClaudeHooksNotice(
    state: ClaudeHooksState,
    claudeWithoutState: Boolean,
    modifier: Modifier = Modifier,
    os: HostOs = HostOs.current(),
) {
    if (!claudeWithoutState || !state.suggestsInstalling) return
    val strings = LocalStrings.current
    val action = state.installAction ?: HooksAction.INSTALL
    Notice(
        kind = NoticeKind.INFO,
        message = if (state.canChangeHooks) strings.claudeHooksNoticeMessage else strings.claudeHooksNoticeToolMessage,
        onDismiss = state::dismissNotice,
        modifier = modifier,
        details = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                CommandLineToolPrerequisite(state, os, showWhenReady = false)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    HooksActionButton(state, action, strings, prominent = true)
                    NeutralTextButton(onClick = state::dismissNotice) { Text(strings.claudeHooksNoticeDismiss) }
                }
            }
        },
    )
    state.pending?.let { ClaudeHooksConfirmDialog(it, state, strings) }
}
