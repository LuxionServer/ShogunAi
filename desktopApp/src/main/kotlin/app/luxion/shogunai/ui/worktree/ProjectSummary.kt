package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import app.luxion.shogunai.ui.components.SectionTitle
import app.luxion.shogunai.ui.components.SkeletonRows
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.theme.ShogunTheme

@Composable
fun ProjectSummary(
    worktreeCount: Int,
    totalSize: Long,
    cleanupCandidateCount: Int,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
) {
    val strings = LocalStrings.current
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SectionTitle(strings.worktreesTitle)
        if (isLoading) {
            SkeletonRows(count = 1, description = strings.loadingWorktrees)
            return@Column
        }
        Text(
            strings.worktreesSummary(worktreeCount, formatSize(totalSize)),
            style = ShogunTheme.typography.dataSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (cleanupCandidateCount > 0) {
            Text(
                strings.cleanupCandidatesSummary(cleanupCandidateCount),
                style = ShogunTheme.typography.dataSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
