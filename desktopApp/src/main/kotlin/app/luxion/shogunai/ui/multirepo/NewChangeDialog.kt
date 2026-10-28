package app.luxion.shogunai.ui.multirepo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.luxion.shogunai.domain.io.ApprovalDecision
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.HerdrOutcome
import app.luxion.shogunai.domain.usecase.GroupRepositoryOutcome
import app.luxion.shogunai.domain.usecase.GroupRepositoryResult
import app.luxion.shogunai.ui.components.ErrorNotice
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.PrimaryButton
import app.luxion.shogunai.ui.components.SegmentedSelector
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.handPointer
import app.luxion.shogunai.ui.components.onEnterKey
import app.luxion.shogunai.ui.components.trackTextInputFocus
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.i18n.describe
import app.luxion.shogunai.ui.theme.ShogunTheme
import app.luxion.shogunai.ui.worktree.DialogRepositoryApproval
import app.luxion.shogunai.ui.worktree.RepositoryApprovalStep

private val DialogWidth = 480.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChangeDialog(
    change: NewChange,
    approval: DialogRepositoryApproval,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val busy = change.isCreating
    val done = change.results != null
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = !busy),
    ) {
        Surface(
            modifier = Modifier.widthIn(max = DialogWidth),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(strings.newChange, style = MaterialTheme.typography.headlineSmall)
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                val isInvalid = change.taskId.isNotBlank() && !change.isTaskIdValid
                OutlinedTextField(
                    value = change.taskId,
                    onValueChange = change::updateTaskId,
                    label = { Text(strings.taskIdLabel) },
                    textStyle = ShogunTheme.typography.data,
                    singleLine = true,
                    enabled = !busy && !done,
                    isError = isInvalid,
                    supportingText = { Text(if (isInvalid) strings.invalidTaskId else strings.taskIdHint) },
                    modifier = Modifier.fillMaxWidth()
                        .focusRequester(focusRequester)
                        .trackTextInputFocus()
                        .onEnterKey(enabled = change.canConfirm, action = onConfirm),
                )
                SegmentedSelector(
                    options = BranchType.entries,
                    selected = change.branchType,
                    onSelect = { change.branchType = it },
                    label = { it.prefix },
                    enabled = !busy && !done,
                )
                Text(strings.newChangeRepositories, style = MaterialTheme.typography.labelLarge)
                change.aliases.forEach { alias ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = alias in change.selected,
                            onCheckedChange = { change.toggle(alias) },
                            enabled = !busy && !done,
                            modifier = Modifier.handPointer(!busy && !done),
                        )
                        Text(alias, style = ShogunTheme.typography.data)
                        change.results?.firstOrNull { it.alias == alias }?.let { result ->
                            Text(
                                result.label(strings),
                                style = ShogunTheme.typography.dataSmall,
                                color = if (result.outcome == GroupRepositoryOutcome.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = Spacing.sm),
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = change.fetchFirst,
                        onCheckedChange = { change.fetchFirst = it },
                        enabled = !busy && !done,
                        modifier = Modifier.handPointer(!busy && !done),
                    )
                    Text(strings.fetchFirstLabel)
                }
                val approvalRequest = approval.pending
                if (approvalRequest != null) RepositoryApprovalStep(approvalRequest, approval)
                change.error?.let { ErrorNotice(error = it, onDismiss = null) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (busy) InlineProgress()
                    if (approvalRequest != null) {
                        NeutralTextButton(onClick = { approval.resolve(ApprovalDecision.SKIP) }) { Text(strings.skipCommands) }
                        PrimaryButton(onClick = { approval.resolve(ApprovalDecision.APPROVE) }) { Text(strings.runAndRemember) }
                        return@Row
                    }
                    NeutralTextButton(onClick = onDismiss, enabled = !busy) { Text(if (done) strings.close else strings.cancel) }
                    if (!done) PrimaryButton(onClick = onConfirm, enabled = change.canConfirm) { Text(strings.createChange) }
                }
            }
        }
    }
}

private fun GroupRepositoryResult.label(strings: Strings): String = when (outcome) {
    GroupRepositoryOutcome.CREATED -> strings.groupRepositoryCreated
    GroupRepositoryOutcome.EXISTING -> strings.groupRepositoryExisting
    GroupRepositoryOutcome.FAILED -> strings.groupRepositoryFailed(error?.let { strings.describe(it).summary }.orEmpty())
}

fun Strings.multirepoNoticeMessage(notice: MultirepoNotice): String = when (notice) {
    is MultirepoNotice.AddedToGroup -> repositoryAddedToGroup(notice.alias, notice.title)
    is MultirepoNotice.ChangeCreated -> changeCreated(notice.title, notice.aliases)
    is MultirepoNotice.AgentOpened -> when (notice.outcome) {
        HerdrOutcome.OPENED -> multirepoAgentOpened
        HerdrOutcome.STARTED_AGENT -> multirepoAgentStarted
        HerdrOutcome.FOCUSED -> multirepoAgentFocused
    }
    is MultirepoNotice.Failed -> describe(notice.error).summary
}
