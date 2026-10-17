package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.ui.components.DangerButton
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.handPointer
import app.luxion.shogunai.ui.i18n.LocalStrings

@Composable
fun WorktreeRemovalDialog(worktree: Worktree, viewModel: WorktreeViewModel) {
    val strings = LocalStrings.current
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
