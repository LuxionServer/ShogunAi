package app.luxion.shogunai.ui.multirepo

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import app.luxion.shogunai.domain.io.ApprovalDecision
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.HerdrOutcome
import app.luxion.shogunai.domain.usecase.GroupRepositoryOutcome
import app.luxion.shogunai.domain.usecase.GroupRepositoryResult
import app.luxion.shogunai.ui.components.DialogSize
import app.luxion.shogunai.ui.components.ErrorNotice
import app.luxion.shogunai.ui.components.Field
import app.luxion.shogunai.ui.components.FieldLabel
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.PrimaryButton
import app.luxion.shogunai.ui.components.SegmentedSelector
import app.luxion.shogunai.ui.components.ShogunDialog
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.handPointer
import app.luxion.shogunai.ui.components.onEnterKey
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.i18n.describe
import app.luxion.shogunai.ui.theme.ShogunTheme
import app.luxion.shogunai.ui.worktree.DialogRepositoryApproval
import app.luxion.shogunai.ui.worktree.RepositoryApprovalStep

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
    val approvalRequest = approval.pending
    ShogunDialog(
        title = strings.newChange,
        onDismissRequest = onDismiss,
        dismissible = !busy,
        size = DialogSize.Medium,
        dismiss = {
            if (busy) InlineProgress()
            if (approvalRequest != null) {
                NeutralTextButton(onClick = { approval.resolve(ApprovalDecision.SKIP) }) { Text(strings.skipCommands) }
            } else {
                NeutralTextButton(onClick = onDismiss, enabled = !busy) { Text(if (done) strings.close else strings.cancel) }
            }
        },
        confirm = {
            if (approvalRequest != null) {
                PrimaryButton(onClick = { approval.resolve(ApprovalDecision.APPROVE) }) { Text(strings.runAndRemember) }
            } else if (!done) {
                PrimaryButton(onClick = onConfirm, enabled = change.canConfirm) { Text(strings.createChange) }
            }
        },
    ) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        val isInvalid = change.taskId.isNotBlank() && !change.isTaskIdValid
        Field(
            value = change.taskId,
            onValueChange = change::updateTaskId,
            label = strings.taskIdLabel,
            help = strings.taskIdHint,
            error = strings.invalidTaskId.takeIf { isInvalid },
            textStyle = ShogunTheme.typography.data,
            enabled = !busy && !done,
            modifier = Modifier.fillMaxWidth(),
            inputModifier = Modifier
                .focusRequester(focusRequester)
                .onEnterKey(enabled = change.canConfirm, action = onConfirm),
        )
        SegmentedSelector(
            options = BranchType.entries,
            selected = change.branchType,
            onSelect = { change.branchType = it },
            label = { it.prefix },
            enabled = !busy && !done,
        )
        FieldLabel(strings.newChangeRepositories)
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
        if (approvalRequest != null) RepositoryApprovalStep(approvalRequest, approval)
        change.error?.let { ErrorNotice(error = it, onDismiss = null) }
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
