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
import app.luxion.shogunai.ui.components.Notice
import app.luxion.shogunai.ui.components.NoticeKind
import app.luxion.shogunai.ui.components.PrimaryButton
import app.luxion.shogunai.ui.components.SegmentedSelector
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.onEnterKey
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.i18n.worktreeError
import app.luxion.shogunai.ui.theme.ShogunTheme

private val DialogWidth = 480.dp

private fun CreateMode.label(strings: Strings): String = when (this) {
    CreateMode.NEW_BRANCH -> strings.newBranchMode
    CreateMode.EXISTING_BRANCH -> strings.existingBranchMode
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateWorktreeDialog(viewModel: WorktreeViewModel) {
    val strings = LocalStrings.current
    val isCreating = viewModel.isCreating
    LaunchedEffect(viewModel.createMode) {
        if (viewModel.createMode == CreateMode.EXISTING_BRANCH) viewModel.loadLocalBranches()
    }
    BasicAlertDialog(
        onDismissRequest = { viewModel.dismissCreateDialog() },
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
                SegmentedSelector(
                    options = CreateMode.entries,
                    selected = viewModel.createMode,
                    onSelect = { viewModel.createMode = it },
                    label = { it.label(strings) },
                    enabled = !isCreating,
                )
                when (viewModel.createMode) {
                    CreateMode.NEW_BRANCH -> NewBranchFields(viewModel)
                    CreateMode.EXISTING_BRANCH -> ExistingBranchField(viewModel)
                }
                viewModel.createError?.let { error ->
                    Notice(
                        kind = NoticeKind.ERROR,
                        message = strings.worktreeError(error),
                        onDismiss = { viewModel.dismissCreateError() },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isCreating) InlineProgress()
                    NeutralTextButton(onClick = { viewModel.dismissCreateDialog() }, enabled = !isCreating) {
                        Text(strings.cancel)
                    }
                    PrimaryButton(onClick = { viewModel.confirmCreate() }, enabled = viewModel.canCreate) {
                        Text(strings.createWorktree)
                    }
                }
            }
        }
    }
}

@Composable
private fun NewBranchFields(viewModel: WorktreeViewModel) {
    val strings = LocalStrings.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val isInvalid = viewModel.taskId.isNotBlank() && !viewModel.isTaskIdValid(viewModel.taskId)
    OutlinedTextField(
        value = viewModel.taskId,
        onValueChange = { viewModel.updateTaskId(it) },
        label = { Text(strings.taskIdLabel) },
        textStyle = ShogunTheme.typography.data,
        singleLine = true,
        enabled = !viewModel.isCreating,
        isError = isInvalid,
        supportingText = { Text(if (isInvalid) strings.invalidTaskId else strings.taskIdHint) },
        modifier = Modifier.fillMaxWidth()
            .focusRequester(focusRequester)
            .onEnterKey(enabled = viewModel.canCreate) { viewModel.confirmCreate() },
    )
    SegmentedSelector(
        options = BranchType.entries,
        selected = viewModel.branchType,
        onSelect = { viewModel.branchType = it },
        label = { it.prefix },
        enabled = !viewModel.isCreating,
    )
}

@Composable
private fun ExistingBranchField(viewModel: WorktreeViewModel) {
    val strings = LocalStrings.current
    val branchGroupExpansion = remember { mutableStateMapOf<String, Boolean>() }
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
            fieldEnabled = !viewModel.isCreating,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
