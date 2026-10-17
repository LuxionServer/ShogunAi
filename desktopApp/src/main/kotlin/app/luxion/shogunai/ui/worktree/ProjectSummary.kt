package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import app.luxion.shogunai.ui.components.DangerTextButton
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.theme.ShogunTheme

@Composable
fun ProjectSummary(viewModel: WorktreeViewModel, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(strings.worktreesTitle, style = MaterialTheme.typography.titleMedium)
        Text(
            strings.worktreesSummary(viewModel.orderedWorktrees.size, formatSize(viewModel.totalSize)),
            style = ShogunTheme.typography.dataSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (viewModel.cleanupCandidates.isNotEmpty()) {
            Text(
                strings.cleanupCandidatesSummary(viewModel.cleanupCandidates.size),
                style = ShogunTheme.typography.dataSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (viewModel.canCleanUp) {
            DangerTextButton(
                onClick = viewModel::openCleanup,
                enabled = !viewModel.isCleaningUp,
            ) {
                Text(strings.cleanUp, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
