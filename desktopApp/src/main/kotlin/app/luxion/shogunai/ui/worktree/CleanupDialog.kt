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
import app.luxion.shogunai.domain.usecase.CleanupFailure
import app.luxion.shogunai.domain.usecase.CleanupReport
import app.luxion.shogunai.domain.usecase.herdrDetail
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
                if (candidates.isNotEmpty()) Text(strings.cleanUpMessage(candidates.size))
                if (candidates.any { it.path in viewModel.openInHerdrPaths }) {
                    Text(
                        strings.herdrCleanupNotice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
                candidates.forEach { worktree ->
                    Text(
                        listOfNotNull(
                            worktree.path.substringAfterLast('/'),
                            worktree.branch,
                            strings.openInHerdrMark.takeIf { worktree.path in viewModel.openInHerdrPaths },
                        ).joinToString(" · "),
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
                if (viewModel.isSimulatorRepository) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = viewModel.cleanupDeleteSimulators,
                            onCheckedChange = { viewModel.cleanupDeleteSimulators = it },
                            enabled = !viewModel.isCleaningUp,
                        )
                        Text(strings.alsoDeleteSimulators)
                    }
                    if (viewModel.orphanedSimulators.isNotEmpty()) {
                        Text(
                            strings.orphanedSimulators(viewModel.orphanedSimulators.map { it.name }),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (viewModel.isXcodeRepository) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = viewModel.cleanupDeleteDerivedData,
                            onCheckedChange = { viewModel.cleanupDeleteDerivedData = it },
                            enabled = !viewModel.isCleaningUp,
                        )
                        Text(strings.alsoDeleteDerivedData)
                    }
                    if (viewModel.derivedDataOrphans.isNotEmpty()) {
                        Text(
                            strings.orphanedDerivedData(
                                viewModel.derivedDataOrphans.size,
                                formatSize(viewModel.derivedDataOrphans.sumOf { it.sizeBytes }),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
    WarningLines(
        report.failures.map { strings.cleanupFailed(it.path, strings.worktreeError(it.error)) } +
            report.keptBranches.map { strings.branchKept(it) },
        onDismiss,
    )
}

@Composable
fun HerdrCloseFailures(failures: List<CleanupFailure>, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    WarningLines(failures.map { strings.herdrCloseFailed(it.path, it.error.herdrDetail()) }, onDismiss)
}

@Composable
private fun WarningLines(lines: List<String>, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
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
