package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.usecase.CleanupReport
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.worktreeError

@Composable
fun CleanupDialog(viewModel: WorktreeViewModel) {
    val strings = LocalStrings.current
    val candidates = viewModel.cleanupCandidates
    AlertDialog(
        onDismissRequest = { viewModel.dismissCleanup() },
        title = { Text(strings.cleanUpTitle) },
        text = {
            Column {
                Text(strings.cleanUpMessage(candidates.size))
                candidates.forEach { worktree ->
                    Text(
                        listOfNotNull(worktree.path.substringAfterLast('/'), worktree.branch).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.sm)) {
                    Checkbox(
                        checked = viewModel.cleanupDeleteBranches,
                        onCheckedChange = { viewModel.cleanupDeleteBranches = it },
                        enabled = !viewModel.isCleaningUp,
                    )
                    Text(strings.alsoDeleteLocalBranches)
                }
                if (viewModel.isGradleRepository) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = viewModel.cleanupStopGradleDaemons,
                            onCheckedChange = { viewModel.cleanupStopGradleDaemons = it },
                            enabled = !viewModel.isCleaningUp,
                        )
                        Text(strings.alsoStopIdleGradleDaemons)
                    }
                }
                if (viewModel.isEmulatorRepository) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = viewModel.cleanupStopEmulators,
                            onCheckedChange = { viewModel.cleanupStopEmulators = it },
                            enabled = !viewModel.isCleaningUp,
                        )
                        Text(strings.alsoStopEmulators)
                    }
                }
            }
        },
        confirmButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (viewModel.isCleaningUp) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = Spacing.sm).size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
                TextButton(onClick = { viewModel.confirmCleanup() }, enabled = !viewModel.isCleaningUp) {
                    Text(strings.cleanUp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dismissCleanup() }, enabled = !viewModel.isCleaningUp) {
                Text(strings.cancel)
            }
        },
    )
}

@Composable
fun CleanupIssues(report: CleanupReport, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    val lines = report.failures.map { strings.cleanupFailed(it.path, strings.worktreeError(it.error)) } +
        report.keptBranches.map { strings.branchKept(it) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Row(modifier = Modifier.weight(1f)) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(end = Spacing.sm),
            )
            Column {
                lines.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = strings.closeMessage)
        }
    }
}
