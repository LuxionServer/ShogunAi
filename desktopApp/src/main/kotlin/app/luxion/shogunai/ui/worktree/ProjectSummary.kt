package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.luxion.shogunai.ui.components.SecondaryButton
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.theme.ShogunTheme

@Composable
fun ProjectSummary(viewModel: WorktreeViewModel, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    val candidates = viewModel.cleanupCandidates.size
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(strings.worktreesTitle, style = MaterialTheme.typography.titleMedium)
        Text(
            strings.worktreesSummary(viewModel.worktrees.size, formatSize(viewModel.totalSize)),
            style = ShogunTheme.typography.data,
        )
        if (candidates > 0) {
            Text(
                strings.cleanableCount(candidates),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SecondaryButton(
            onClick = { viewModel.openCleanup() },
            enabled = viewModel.canCleanUp && !viewModel.isCleaningUp,
        ) { Text(strings.cleanUp) }
    }
}
