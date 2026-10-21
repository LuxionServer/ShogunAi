package app.luxion.shogunai.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.luxion.shogunai.ui.components.ErrorNotice
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.Notice
import app.luxion.shogunai.ui.components.NoticeKind
import app.luxion.shogunai.ui.components.PrimaryButton
import app.luxion.shogunai.ui.components.SecondaryButton
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings

@Composable
fun ClaudeHooksSection(state: ClaudeHooksState) {
    val strings = LocalStrings.current
    Text(
        strings.claudeCodeLabel,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = Spacing.md),
    )
    state.status?.let { Text(strings.claudeHooksStatus(it.state), style = MaterialTheme.typography.bodyMedium) }
    if (!state.shogunOnPath) {
        Text(strings.claudeHooksPathWarning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    state.error?.let { ErrorNotice(error = it, onDismiss = state::dismissError) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        state.installAction?.let { action ->
            SecondaryButton(onClick = { state.request(action) }) {
                Text(if (action == HooksAction.UPDATE) strings.claudeHooksUpdate else strings.claudeHooksInstall)
            }
        }
        if (state.canRemove) {
            NeutralTextButton(onClick = { state.request(HooksAction.REMOVE) }) { Text(strings.claudeHooksRemove) }
        }
    }
    state.pending?.let { ClaudeHooksConfirmDialog(it, state, strings) }
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
fun ClaudeHooksNotice(state: ClaudeHooksState, claudeWithoutState: Boolean, modifier: Modifier = Modifier) {
    if (!claudeWithoutState || !state.suggestsInstalling) return
    val strings = LocalStrings.current
    val action = state.installAction ?: HooksAction.INSTALL
    Notice(
        kind = NoticeKind.INFO,
        message = strings.claudeHooksNoticeMessage,
        onDismiss = state::dismissNotice,
        modifier = modifier,
        details = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SecondaryButton(onClick = { state.request(action) }) {
                    Text(if (action == HooksAction.UPDATE) strings.claudeHooksUpdate else strings.claudeHooksInstall)
                }
                NeutralTextButton(onClick = state::dismissNotice) { Text(strings.claudeHooksNoticeDismiss) }
            }
        },
    )
    state.pending?.let { ClaudeHooksConfirmDialog(it, state, strings) }
}
