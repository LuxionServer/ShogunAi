package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.AttributedDerivedData
import app.luxion.shogunai.domain.model.DerivedDataDeletion
import app.luxion.shogunai.domain.model.DerivedDataListing
import app.luxion.shogunai.domain.model.DerivedDataOwner
import app.luxion.shogunai.ui.components.SectionCard
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import java.time.Instant

@Composable
fun XcodeDerivedDataSection(viewModel: WorktreeViewModel, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    SectionCard(title = strings.xcodeDerivedDataTitle, modifier = modifier) {
        when (val state = viewModel.derivedData) {
            DerivedDataState.Loading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            DerivedDataState.Unavailable -> SecondaryText(strings.xcodeDerivedDataUnavailable)
            is DerivedDataState.Loaded -> LoadedDerivedData(listing = state.listing, viewModel = viewModel)
        }
        viewModel.derivedDataDeletionReport?.let { report ->
            DerivedDataDeletionMessage(report, onDismiss = { viewModel.dismissDerivedDataDeletionReport() })
        }
    }
}

@Composable
private fun LoadedDerivedData(listing: DerivedDataListing, viewModel: WorktreeViewModel) {
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
        SecondaryText(
            strings.derivedDataSummary(worktreeFolders.size, formatSize(worktreeFolders.sumOf { it.folder.sizeBytes })),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SecondaryText(strings.derivedDataOrphans(orphans.size, formatSize(orphans.sumOf { it.sizeBytes })))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (viewModel.isDeletingOrphans) {
                CircularProgressIndicator(modifier = Modifier.padding(end = Spacing.sm).size(20.dp), strokeWidth = 2.dp)
            }
            OutlinedButton(
                onClick = { viewModel.deleteOrphanedDerivedData() },
                enabled = !viewModel.isDeletingOrphans && orphans.isNotEmpty(),
            ) { Text(strings.deleteOrphans) }
        }
    }
    if (unavailableVolumeFolders.isNotEmpty()) {
        SecondaryText(strings.derivedDataOnUnavailableVolumes(unavailableVolumeFolders.size))
    }
    listing.compilationCacheBytes?.let { cacheBytes ->
        SecondaryText(strings.derivedDataCompilationCache(formatSize(cacheBytes)))
    }
}

@Composable
private fun DerivedDataRow(attributed: AttributedDerivedData) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
        Text(worktreeName(attributed), maxLines = 1, overflow = TextOverflow.Ellipsis)
        SecondaryText(derivedDataDetails(attributed, LocalStrings.current, Instant.now()))
    }
}

@Composable
private fun DerivedDataDeletionMessage(report: DerivedDataDeletion, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            strings.derivedDataDeleted(report.deleted.size, formatSize(report.bytesFreed)),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = strings.closeMessage)
        }
    }
}

@Composable
private fun SecondaryText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun worktreeName(attributed: AttributedDerivedData): String =
    (attributed.owner as DerivedDataOwner.Worktree).path.substringAfterLast('/')

private fun derivedDataDetails(attributed: AttributedDerivedData, strings: Strings, now: Instant): String =
    listOfNotNull(
        formatSize(attributed.folder.sizeBytes),
        attributed.folder.lastAccessed?.let { relativeTime(it, now, strings) },
    ).joinToString(" · ")
