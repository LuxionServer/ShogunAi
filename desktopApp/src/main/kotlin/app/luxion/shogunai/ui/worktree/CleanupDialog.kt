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
import app.luxion.shogunai.domain.usecase.CleanupFailure
import app.luxion.shogunai.domain.usecase.CleanupReport
import app.luxion.shogunai.domain.usecase.herdrDetail
import app.luxion.shogunai.ui.components.DangerButton
import app.luxion.shogunai.ui.components.DangerTextButton
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.Notice
import app.luxion.shogunai.ui.components.NoticeKind
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.handPointer
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.describe
import app.luxion.shogunai.ui.theme.ShogunTheme

@Composable
fun CleanupDialog(board: WorktreeBoard) {
    val strings = LocalStrings.current
    val groups = board.cleanupProjects
    val holders = groups.map { it.holder }
    val candidateCount = groups.sumOf { it.worktrees.size }
    val showHeadings = groups.size > 1 || !board.isFiltered
    val enabled = !board.isCleaningUp
    val orphanedSimulators = holders.filter { it.isSimulatorRepository }.flatMap { it.orphanedSimulators }
    val derivedDataOrphans = holders.filter { it.isXcodeRepository }.flatMap { it.derivedDataOrphans }
    AlertDialog(
        onDismissRequest = { board.dismissCleanup() },
        title = { Text(strings.cleanUpTitle) },
        text = {
            Column {
                if (candidateCount > 0) Text(strings.cleanUpMessage(candidateCount))
                if (groups.any { group -> group.worktrees.any { it.path in group.holder.openInHerdrPaths } }) {
                    Text(
                        strings.herdrCleanupNotice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
                groups.filter { it.worktrees.isNotEmpty() }.forEach { group ->
                    if (showHeadings) {
                        Text(
                            group.project.name,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                    }
                    group.worktrees.forEach { worktree ->
                        Text(
                            listOfNotNull(
                                worktree.path.substringAfterLast('/'),
                                worktree.branch,
                                strings.openInHerdrMark.takeIf { worktree.path in group.holder.openInHerdrPaths },
                            ).joinToString(" · "),
                            style = ShogunTheme.typography.dataSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
                groups.forEach { group ->
                    group.holder.cleanupKeptForRunningAgent.forEach { (worktree, running) ->
                        Text(
                            strings.keptForRunningAgent(worktree.path.substringAfterLast('/'), running.map { it.name }),
                            style = ShogunTheme.typography.dataSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
                CleanupOption(strings.alsoDeleteLocalBranches, board.cleanupDeleteBranches, enabled, Modifier.padding(top = Spacing.sm)) {
                    board.cleanupDeleteBranches = it
                }
                if (holders.any { it.isGradleRepository }) {
                    CleanupOption(strings.alsoStopIdleGradleDaemons, board.cleanupStopGradleDaemons, enabled) {
                        board.cleanupStopGradleDaemons = it
                    }
                }
                if (holders.any { it.isEmulatorRepository }) {
                    CleanupOption(strings.alsoStopEmulators, board.cleanupStopEmulators, enabled) {
                        board.cleanupStopEmulators = it
                    }
                }
                if (holders.any { it.isSimulatorRepository }) {
                    CleanupOption(strings.alsoDeleteSimulators, board.cleanupDeleteSimulators, enabled) {
                        board.cleanupDeleteSimulators = it
                    }
                    if (orphanedSimulators.isNotEmpty()) {
                        Text(
                            strings.orphanedSimulators(orphanedSimulators.map { it.name }),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (holders.any { it.isXcodeRepository }) {
                    CleanupOption(strings.alsoDeleteDerivedData, board.cleanupDeleteDerivedData, enabled) {
                        board.cleanupDeleteDerivedData = it
                    }
                    if (derivedDataOrphans.isNotEmpty()) {
                        Text(
                            strings.orphanedDerivedData(
                                derivedDataOrphans.size,
                                formatSize(derivedDataOrphans.sumOf { it.sizeBytes }),
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
                if (board.isCleaningUp) {
                    InlineProgress(modifier = Modifier.padding(end = Spacing.sm))
                }
                DangerButton(onClick = { board.confirmCleanup() }, enabled = enabled) {
                    Text(strings.cleanUp)
                }
            }
        },
        dismissButton = {
            NeutralTextButton(onClick = { board.dismissCleanup() }, enabled = enabled) {
                Text(strings.cancel)
            }
        },
    )
}

@Composable
private fun CleanupOption(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.handPointer(enabled),
        )
        Text(label)
    }
}

@Composable
fun CleanupIssues(report: CleanupReport, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    val lines = report.failures.map { strings.cleanupFailed(it.path, strings.describe(it.error).summary) } +
        report.keptBranches.map { strings.branchKept(it) }
    Notice(
        kind = NoticeKind.WARNING,
        message = lines.joinToString("\n"),
        onDismiss = onDismiss,
        modifier = Modifier.padding(top = Spacing.sm),
    )
}

@Composable
fun HerdrCloseFailures(failures: List<CleanupFailure>, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    Notice(
        kind = NoticeKind.WARNING,
        message = failures.joinToString("\n") { strings.herdrCloseFailed(it.path, it.error.herdrDetail()) },
        onDismiss = onDismiss,
        modifier = Modifier.padding(top = Spacing.sm),
    )
}

@Composable
fun KeptBranchNotice(notice: ProjectWorktrees.KeptBranchNotice, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    Notice(
        kind = NoticeKind.WARNING,
        message = notice.failureReason?.let { strings.worktreeRemovedBranchKeptFailed(notice.branch, it) }
            ?: strings.worktreeRemovedBranchKept(notice.branch),
        onDismiss = onDismiss,
        modifier = Modifier.padding(top = Spacing.sm),
        details = {
            DangerTextButton(onClick = onDelete) { Text(strings.deleteBranchAnyway) }
        },
    )
}
