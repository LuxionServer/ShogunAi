package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.ui.components.DropdownSelector
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.PrimaryButton
import app.luxion.shogunai.ui.components.SecondaryButton
import app.luxion.shogunai.ui.components.SegmentedSelector
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.onEnterKey
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.components.ErrorNotice
import app.luxion.shogunai.ui.theme.ShogunTheme

private val DialogWidth = 480.dp

private fun CreateMode.label(strings: Strings): String = when (this) {
    CreateMode.NEW_BRANCH -> strings.newBranchMode
    CreateMode.EXISTING_BRANCH -> strings.existingBranchMode
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateWorktreeDialog(board: WorktreeBoard) {
    val strings = LocalStrings.current
    val holder = board.createHolder ?: return
    val isCreating = holder.isCreating
    BasicAlertDialog(
        onDismissRequest = { board.dismissCreateDialog() },
        properties = DialogProperties(dismissOnBackPress = !isCreating, dismissOnClickOutside = !isCreating),
    ) {
        Surface(
            modifier = Modifier.widthIn(max = DialogWidth),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(strings.newWorktree, style = MaterialTheme.typography.headlineSmall)
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
                if (holder.createdWithErrorPath != null) {
                    Text(strings.worktreeCreatedWithError, style = MaterialTheme.typography.bodyMedium)
                }
                holder.createError?.let { error ->
                    ErrorNotice(error = error, onDismiss = { holder.dismissCreateError() })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isCreating) InlineProgress()
                    NeutralTextButton(onClick = { board.dismissCreateDialog() }, enabled = !isCreating) {
                        Text(if (holder.createdWithErrorPath != null) strings.close else strings.cancel)
                    }
                    if (holder.createdWithErrorPath != null) {
                        SecondaryButton(onClick = holder::copyCreatedLaunchCommand) { Text(strings.copyLaunchCommand) }
                    } else {
                        PrimaryButton(onClick = { board.confirmCreate() }, enabled = board.canCreate) {
                            Text(strings.createWorktree)
                        }
                    }
                }
            }
        }
    }
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
    OutlinedTextField(
        value = board.taskId,
        onValueChange = { board.updateTaskId(it) },
        label = { Text(strings.taskIdLabel) },
        textStyle = ShogunTheme.typography.data,
        singleLine = true,
        enabled = !holder.isCreating,
        isError = isInvalid,
        supportingText = { Text(if (isInvalid) strings.invalidTaskId else strings.taskIdHint) },
        modifier = Modifier.fillMaxWidth()
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
}

@Composable
private fun ExistingBranchField(board: WorktreeBoard, holder: ProjectWorktrees) {
    val strings = LocalStrings.current
    val branchGroupExpansion = remember { mutableStateMapOf<String, Boolean>() }
    when {
        holder.isLoadingBranches -> Row(verticalAlignment = Alignment.CenterVertically) {
            InlineProgress(modifier = Modifier.padding(end = Spacing.sm))
            Text(strings.loadingBranches)
        }
        holder.localBranches.isEmpty() -> Text(strings.noBranchesAvailable)
        else -> DropdownSelector(
            options = holder.localBranches,
            selected = holder.localBranches.find { it.name == board.selectedBranch },
            onSelect = { board.selectedBranch = it.name },
            enabled = { !it.isCheckedOut },
            label = { if (it.isCheckedOut) strings.branchAlreadyHasWorktree(it.name) else it.name },
            placeholder = strings.selectBranchPlaceholder,
            groupBy = { it.name.substringBefore('/', strings.otherBranchesGroup) },
            expandedGroups = branchGroupExpansion,
            fieldEnabled = !holder.isCreating,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
