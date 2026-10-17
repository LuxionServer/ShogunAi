package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.luxion.shogunai.domain.model.AttributedDerivedData
import app.luxion.shogunai.domain.model.DerivedDataDeletion
import app.luxion.shogunai.domain.model.DerivedDataListing
import app.luxion.shogunai.domain.model.DerivedDataOwner
import app.luxion.shogunai.ui.components.DangerTextButton
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.Notice
import app.luxion.shogunai.ui.components.NoticeKind
import app.luxion.shogunai.ui.components.SectionCard
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.theme.ShogunTheme
import java.time.Instant

@Composable
fun XcodeDerivedDataSection(viewModel: ProjectWorktrees, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    SectionCard(title = strings.xcodeDerivedDataTitle, modifier = modifier) {
        when (val state = viewModel.derivedData) {
            DerivedDataState.Loading -> InlineProgress()
            DerivedDataState.Unavailable -> SecondaryText(strings.xcodeDerivedDataUnavailable)
            is DerivedDataState.Loaded -> LoadedDerivedData(listing = state.listing, viewModel = viewModel)
        }
        viewModel.derivedDataDeletionReport?.let { report ->
            DerivedDataDeletionMessage(report, onDismiss = { viewModel.dismissDerivedDataDeletionReport() })
        }
    }
}

@Composable
private fun LoadedDerivedData(listing: DerivedDataListing, viewModel: ProjectWorktrees) {
    val strings = LocalStrings.current
    val worktreeFolders = listing.ofWorktrees()
    val orphans = listing.orphans
    val unavailableVolumeFolders = listing.folders.filter { it.owner == DerivedDataOwner.UnavailableVolume }
    if (worktreeFolders.isEmpty()) {
        SecondaryText(strings.noDerivedDataFolders)
    }
    worktreeFolders.forEach { attributed ->
        HorizontalDivider()
        DerivedDataRow(attributed)
    }
    if (worktreeFolders.isNotEmpty()) {
        DataText(
            strings.derivedDataSummary(worktreeFolders.size, formatSize(worktreeFolders.sumOf { it.folder.sizeBytes })),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DataText(strings.derivedDataOrphans(orphans.size, formatSize(orphans.sumOf { it.sizeBytes })))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (viewModel.isDeletingOrphans) {
                InlineProgress(modifier = Modifier.padding(end = Spacing.sm))
            }
            DangerTextButton(
                onClick = { viewModel.deleteOrphanedDerivedData() },
                enabled = !viewModel.isDeletingOrphans && orphans.isNotEmpty(),
            ) { Text(strings.deleteOrphans) }
        }
    }
    if (unavailableVolumeFolders.isNotEmpty()) {
        SecondaryText(strings.derivedDataOnUnavailableVolumes(unavailableVolumeFolders.size))
    }
    listing.compilationCacheBytes?.let { cacheBytes ->
        DataText(strings.derivedDataCompilationCache(formatSize(cacheBytes)))
    }
}

@Composable
private fun DerivedDataRow(attributed: AttributedDerivedData) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
        Text(worktreeName(attributed), style = ShogunTheme.typography.data, maxLines = 1, overflow = TextOverflow.Ellipsis)
        DataText(derivedDataDetails(attributed, LocalStrings.current, Instant.now()))
    }
}

@Composable
private fun DerivedDataDeletionMessage(report: DerivedDataDeletion, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    Notice(
        kind = NoticeKind.SUCCESS,
        message = strings.derivedDataDeleted(report.deleted.size, formatSize(report.bytesFreed)),
        onDismiss = onDismiss,
    )
}

@Composable
private fun SecondaryText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DataText(text: String) {
    Text(text, style = ShogunTheme.typography.dataSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun worktreeName(attributed: AttributedDerivedData): String =
    (attributed.owner as DerivedDataOwner.Worktree).path.substringAfterLast('/')

private fun derivedDataDetails(attributed: AttributedDerivedData, strings: Strings, now: Instant): String =
    listOfNotNull(
        formatSize(attributed.folder.sizeBytes),
        attributed.folder.lastAccessed?.let { relativeTime(it, now, strings) },
    ).joinToString(" · ")
