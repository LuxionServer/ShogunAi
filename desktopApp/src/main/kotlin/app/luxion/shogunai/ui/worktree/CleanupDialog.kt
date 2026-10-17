package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.luxion.shogunai.domain.usecase.CleanupReport
import app.luxion.shogunai.ui.components.DangerButton
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.Notice
import app.luxion.shogunai.ui.components.NoticeKind
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.handPointer
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.worktreeError
import app.luxion.shogunai.ui.theme.ShogunTheme

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
                candidates.forEach { worktree ->
                    Text(
                        listOfNotNull(worktree.path.substringAfterLast('/'), worktree.branch).joinToString(" · "),
                        style = ShogunTheme.typography.dataSmall,
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
                        modifier = Modifier.handPointer(!viewModel.isCleaningUp),
                    )
                    Text(strings.alsoDeleteLocalBranches)
                }
                if (viewModel.isGradleRepository) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = viewModel.cleanupStopGradleDaemons,
                            onCheckedChange = { viewModel.cleanupStopGradleDaemons = it },
                            enabled = !viewModel.isCleaningUp,
                            modifier = Modifier.handPointer(!viewModel.isCleaningUp),
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
                            modifier = Modifier.handPointer(!viewModel.isCleaningUp),
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
                            modifier = Modifier.handPointer(!viewModel.isCleaningUp),
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
                            modifier = Modifier.handPointer(!viewModel.isCleaningUp),
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
InlineProgress(modifier = Modifier.padding(end = Spacing.sm))
                }
                DangerButton(onClick = { viewModel.confirmCleanup() }, enabled = !viewModel.isCleaningUp) {
                    Text(strings.cleanUp)
                }
            }
        },
        dismissButton = {
            NeutralTextButton(onClick = { viewModel.dismissCleanup() }, enabled = !viewModel.isCleaningUp) {
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
    Notice(
        kind = NoticeKind.WARNING,
        message = lines.joinToString("\n"),
        onDismiss = onDismiss,
        modifier = Modifier.padding(top = Spacing.sm),
    )
}
