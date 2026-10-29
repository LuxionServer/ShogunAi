package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import app.luxion.shogunai.domain.io.ApprovalDecision
import app.luxion.shogunai.domain.model.RepositoryApprovalSubject
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.ShellQuoting
import app.luxion.shogunai.domain.model.withoutLocalTwins
import app.luxion.shogunai.ui.components.DialogSize
import app.luxion.shogunai.ui.components.DropdownSelector
import app.luxion.shogunai.ui.components.Field
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.PrimaryButton
import app.luxion.shogunai.ui.components.SecondaryButton
import app.luxion.shogunai.ui.components.SectionTitle
import app.luxion.shogunai.ui.components.SegmentedSelector
import app.luxion.shogunai.ui.components.ShogunDialog
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.handPointer
import app.luxion.shogunai.ui.components.onEnterKey
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.components.ErrorNotice
import app.luxion.shogunai.ui.theme.ShogunTheme

private fun CreateMode.label(strings: Strings): String = when (this) {
    CreateMode.NEW_BRANCH -> strings.newBranchMode
    CreateMode.EXISTING_BRANCH -> strings.existingBranchMode
}

@Composable
fun CreateWorktreeDialog(board: WorktreeBoard) {
    val strings = LocalStrings.current
    val holder = board.createHolder ?: return
    val isCreating = holder.isCreating
    val approvalRequest = board.approval.pending
    ShogunDialog(
        title = strings.newWorktree,
        onDismissRequest = { board.dismissCreateDialog() },
        dismissible = !isCreating,
        size = DialogSize.Medium,
        dismiss = {
            if (isCreating) InlineProgress()
            if (approvalRequest != null) {
                NeutralTextButton(onClick = { board.approval.resolve(ApprovalDecision.SKIP) }) { Text(strings.skipCommands) }
            } else {
                NeutralTextButton(onClick = { board.dismissCreateDialog() }, enabled = !isCreating) {
                    Text(if (holder.createdWithErrorPath != null) strings.close else strings.cancel)
                }
            }
        },
        confirm = {
            when {
                approvalRequest != null ->
                    PrimaryButton(onClick = { board.approval.resolve(ApprovalDecision.APPROVE) }) { Text(strings.runAndRemember) }
                holder.createdWithErrorPath != null ->
                    SecondaryButton(onClick = holder::copyCreatedLaunchCommand) { Text(strings.copyLaunchCommand) }
                else -> PrimaryButton(onClick = { board.confirmCreate() }, enabled = board.canCreate) {
                    Text(strings.createWorktree)
                }
            }
        },
    ) {
        if (board.createAsksForProject) ProjectField(board, isCreating)
        SegmentedSelector(
            options = CreateMode.entries,
            selected = board.createMode,
            onSelect = { board.updateCreateMode(it) },
            label = { it.label(strings) },
            enabled = !isCreating,
        )
        when (board.createMode) {
            CreateMode.NEW_BRANCH -> NewBranchFields(board, holder)
            CreateMode.EXISTING_BRANCH -> ExistingBranchField(board, holder)
        }
        if (board.canFetchFirst) {
            FetchFirstOption(board, isCreating)
        }
        holder.fetchError?.let { error ->
            ErrorNotice(error = error, onDismiss = null, onRetry = { board.confirmCreate() })
            NeutralTextButton(onClick = { board.confirmCreate(skipFetch = true) }) {
                Text(strings.createWithoutFetching)
            }
        }
        if (approvalRequest != null) RepositoryApprovalStep(approvalRequest, board.approval)
        if (holder.createdWithErrorPath != null) {
            Text(strings.worktreeCreatedWithError, style = MaterialTheme.typography.bodyMedium)
        }
        holder.createError?.let { error ->
            ErrorNotice(error = error, onDismiss = { holder.dismissCreateError() })
        }
    }
}

@Composable
internal fun RepositoryApprovalStep(request: DialogRepositoryApproval.Request, approval: DialogRepositoryApproval) {
    val strings = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionTitle(strings.postCreateApprovalTitle)
        Text(
            strings.postCreateApprovalExplanation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Column(modifier = Modifier.fillMaxWidth().padding(Spacing.sm)) {
                ApprovalSubjectLines(request.subject, strings)
            }
        }
    }
}

@Composable
fun ApprovalSubjectLines(subject: RepositoryApprovalSubject, strings: Strings) {
    subject.commands.forEach { command ->
        Text(ShellQuoting.posixCommand(command), style = ShogunTheme.typography.data)
    }
    if (subject.variables.isNotEmpty()) {
        Text(strings.approvalVariablesTitle, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = Spacing.xs))
        subject.variables.forEach { (name, template) ->
            Text("$name = $template", style = ShogunTheme.typography.data)
        }
    }
    subject.envFile?.let { envFile ->
        Text(strings.approvalEnvFile(envFile), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Spacing.xs))
    }
}

@Composable
private fun FetchFirstOption(board: WorktreeBoard, isCreating: Boolean) {
    val strings = LocalStrings.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = board.fetchFirst,
            onCheckedChange = { board.fetchFirst = it },
            enabled = !isCreating,
            modifier = Modifier.handPointer(!isCreating),
        )
        Text(strings.fetchFirstLabel)
    }
}

@Composable
private fun StartFromField(board: WorktreeBoard, holder: ProjectWorktrees) {
    val strings = LocalStrings.current
    val groupExpansion = remember { mutableStateMapOf<String, Boolean>() }
    DropdownSelector(
        options = board.startOptions,
        selected = board.effectiveStartFrom,
        onSelect = { board.startFrom = it },
        label = { option ->
            when {
                option == StartOption.Current -> strings.startFromCurrentBranch(board.currentBranchName)
                option.ref == holder.defaultStartRef -> strings.startFromMainBranch(option.ref.orEmpty())
                else -> option.ref.orEmpty()
            }
        },
        placeholder = strings.startFromLabel,
        fieldLabel = strings.startFromLabel,
        groupBy = { option ->
            option.ref?.takeIf { it != holder.defaultStartRef }?.substringBefore('/', strings.otherBranchesGroup)
        },
        expandedGroups = groupExpansion,
        fieldEnabled = !holder.isCreating,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ProjectField(board: WorktreeBoard, isCreating: Boolean) {
    val strings = LocalStrings.current
    DropdownSelector(
        options = board.projects,
        selected = board.projects.find { it.id == board.createProjectId },
        onSelect = { board.selectCreateProject(it.id) },
        label = { it.name },
        placeholder = strings.projectLabel,
        fieldLabel = strings.projectLabel,
        fieldEnabled = !isCreating,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NewBranchFields(board: WorktreeBoard, holder: ProjectWorktrees) {
    val strings = LocalStrings.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val isInvalid = board.taskId.isNotBlank() && !board.isTaskIdValid(board.taskId)
    Field(
        value = board.taskId,
        onValueChange = { board.updateTaskId(it) },
        label = strings.taskIdLabel,
        help = strings.taskIdHint,
        error = strings.invalidTaskId.takeIf { isInvalid },
        textStyle = ShogunTheme.typography.data,
        enabled = !holder.isCreating,
        modifier = Modifier.fillMaxWidth(),
        inputModifier = Modifier
            .focusRequester(focusRequester)
            .onEnterKey(enabled = board.canCreate) { board.confirmCreate() },
    )
    SegmentedSelector(
        options = BranchType.entries,
        selected = board.branchType,
        onSelect = { board.branchType = it },
        label = { it.prefix },
        enabled = !holder.isCreating,
    )
    StartFromField(board, holder)
}

@Composable
private fun ExistingBranchField(board: WorktreeBoard, holder: ProjectWorktrees) {
    val strings = LocalStrings.current
    val branchGroupExpansion = remember { mutableStateMapOf<String, Boolean>() }
    val existing = holder.branches.withoutLocalTwins()
    when {
        holder.isLoadingBranches -> Row(verticalAlignment = Alignment.CenterVertically) {
            InlineProgress(modifier = Modifier.padding(end = Spacing.sm))
            Text(strings.loadingBranches)
        }
        holder.branchesError != null -> ErrorNotice(
            error = holder.branchesError!!,
            onDismiss = null,
            onRetry = holder::loadBranches,
        )
        existing.isEmpty() -> Text(strings.noBranchesAvailable)
        else -> DropdownSelector(
            options = existing,
            selected = existing.find { it.name == board.selectedBranch },
            onSelect = { board.selectedBranch = it.name },
            enabled = { !it.isCheckedOut },
            label = {
                when {
                    it.isCheckedOut -> strings.branchAlreadyHasWorktree(it.name)
                    it.isRemote -> strings.remoteBranchLabel(it.name)
                    else -> it.name
                }
            },
            placeholder = strings.selectBranchPlaceholder,
            groupBy = { it.name.substringBefore('/', strings.otherBranchesGroup) },
            expandedGroups = branchGroupExpansion,
            fieldEnabled = !holder.isCreating,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
