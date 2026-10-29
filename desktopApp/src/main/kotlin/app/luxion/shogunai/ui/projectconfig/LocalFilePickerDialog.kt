package app.luxion.shogunai.ui.projectconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.LocalFileCandidate
import app.luxion.shogunai.ui.components.DialogSize
import app.luxion.shogunai.ui.components.ErrorNotice
import app.luxion.shogunai.ui.components.Field
import app.luxion.shogunai.ui.components.IconAction
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.PrimaryButton
import app.luxion.shogunai.ui.components.SecondaryButton
import app.luxion.shogunai.ui.components.ShogunDialog
import app.luxion.shogunai.ui.components.SkeletonRows
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.StatusChip
import app.luxion.shogunai.ui.components.handPointer
import app.luxion.shogunai.ui.components.onEnterKey
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.theme.ShogunTheme
import app.luxion.shogunai.ui.theme.Status

private val CandidateListHeight = 360.dp
private val CandidateIconSize = 16.dp
private val FilterIconSize = 16.dp

@Composable
fun LocalFilePickerDialog(
    state: LocalFilePickerState,
    repository: String,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val strings = LocalStrings.current
    ShogunDialog(
        title = strings.localFilePickerTitle,
        onDismissRequest = onCancel,
        size = DialogSize.Large,
        scrollable = false,
        dismiss = {
            Text(
                strings.selectedLocalFiles(state.selectedCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NeutralTextButton(onClick = onCancel) { Text(strings.cancel) }
        },
        confirm = {
            PrimaryButton(onClick = onApply, enabled = state.load is LocalFileCandidatesLoad.Loaded || state.patterns.isNotEmpty()) {
                Text(strings.applyLocalFiles)
            }
        },
    ) {
        Text(
            strings.localFilePickerDescription(repository),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PatternFilter(state)
        Box(modifier = Modifier.fillMaxWidth().height(CandidateListHeight)) {
            when (val load = state.load) {
                LocalFileCandidatesLoad.Loading -> SkeletonRows(count = 4)
                is LocalFileCandidatesLoad.Failed -> ErrorNotice(error = load.error, onDismiss = null, onRetry = onRetry)
                is LocalFileCandidatesLoad.Loaded -> CandidateList(state, load.candidates.isEmpty())
            }
        }
    }
}

@Composable
private fun PatternFilter(state: LocalFilePickerState) {
    val strings = LocalStrings.current
    Field(
        value = state.filter,
        onValueChange = { state.filter = it },
        placeholder = strings.localFileFilterPlaceholder,
        textStyle = ShogunTheme.typography.data,
        error = strings.localPatternMustBeRelative.takeIf { state.isFilterPatternAbsolute },
        leading = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(FilterIconSize)) },
        inputModifier = Modifier.testTag("local-file-filter").onEnterKey(enabled = state.offeredPattern != null, action = state::addOfferedPattern),
        action = state.offeredPattern?.let { pattern ->
            { SecondaryButton(onClick = state::addOfferedPattern) { Text(strings.addLocalPattern(pattern)) } }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.patterns.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            state.patterns.forEach { pattern ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pattern, style = ShogunTheme.typography.data)
                    Text(
                        strings.localPatternLabel,
                        style = ShogunTheme.typography.dataSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.xs),
                    )
                    IconAction(icon = Icons.Default.Close, label = strings.removeLocalFile, onClick = { state.removePattern(pattern) })
                }
            }
        }
    }
}

@Composable
private fun CandidateList(state: LocalFilePickerState, noCandidates: Boolean) {
    val strings = LocalStrings.current
    val visible = state.visible
    if (visible.isEmpty()) {
        Text(
            if (noCandidates) strings.noLocalFileCandidates else strings.noLocalFileMatches,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = Spacing.md),
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(visible, key = { it.path }) { candidate -> CandidateRow(state, candidate) }
    }
}

@Composable
private fun CandidateRow(state: LocalFilePickerState, candidate: LocalFileCandidate) {
    val strings = LocalStrings.current
    val enabled = !candidate.providedByRepository
    val checked = state.isChecked(candidate)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox) { state.setChecked(candidate, it) }
            .handPointer(enabled)
            .padding(end = Spacing.sm)
            .testTag("local-file-${candidate.path}"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(Spacing.sm))
        Icon(
            if (candidate.isFolder) Icons.Outlined.Folder else Icons.Outlined.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(CandidateIconSize),
        )
        Text(
            if (candidate.isFolder) "${candidate.path}/" else candidate.path,
            style = ShogunTheme.typography.data,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            modifier = Modifier.weight(1f),
        )
        val secretKind = candidate.secretKind
        when {
            candidate.providedByRepository -> StatusChip(Status.NEUTRAL, strings.providedByRepository)
            secretKind != null -> StatusChip(Status.INFO, strings.suggestedLocalFile(strings.mobileSecretKind(secretKind)))
        }
    }
}
