package app.luxion.shogunai.ui.multirepo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.luxion.shogunai.ui.FilePicker
import app.luxion.shogunai.ui.components.ErrorNotice
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.Notice
import app.luxion.shogunai.ui.components.NoticeKind
import app.luxion.shogunai.ui.components.PrimaryButton
import app.luxion.shogunai.ui.components.SecondaryButton
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.handPointer
import app.luxion.shogunai.ui.components.trackTextInputFocus
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.theme.ShogunTheme

private val DialogWidth = 640.dp
private val DialogMaxHeight = 640.dp
private val AliasFieldWidth = 140.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMultirepoDialog(
    state: AddMultirepoState,
    onDismiss: () -> Unit,
    onAdded: (String) -> Unit,
    pickFolder: (String, String) -> String? = FilePicker::pickDirectory,
) {
    val strings = LocalStrings.current
    BasicAlertDialog(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !state.isSaving, dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.widthIn(max = DialogWidth),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(strings.addMultirepo, style = MaterialTheme.typography.headlineSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        state.folder ?: strings.multirepoFolderPlaceholder,
                        style = ShogunTheme.typography.data,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(onClick = { pickFolder(state.folder.orEmpty(), strings.addMultirepo)?.let(state::choose) }, enabled = !state.isSaving) {
                        Text(strings.chooseFolder)
                    }
                }
                Column(
                    modifier = Modifier.heightIn(max = DialogMaxHeight).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    AddMultirepoContent(state, pickFolder)
                }
                state.error?.let { ErrorNotice(error = it, onDismiss = null) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.isSaving) InlineProgress()
                    NeutralTextButton(onClick = onDismiss, enabled = !state.isSaving) { Text(strings.cancel) }
                    PrimaryButton(onClick = { state.confirm(onAdded) }, enabled = state.canConfirm) { Text(strings.addMultirepo) }
                }
            }
        }
    }
}

@Composable
private fun AddMultirepoContent(state: AddMultirepoState, pickFolder: (String, String) -> String?) {
    val strings = LocalStrings.current
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    if (state.folder == null) {
        Text(strings.addMultirepoExplanation, style = MaterialTheme.typography.bodyMedium, color = secondary)
        return
    }
    state.enclosingRepository?.let { repository ->
        Notice(kind = NoticeKind.ERROR, message = strings.multirepoInsideRepository(repository), onDismiss = null)
        return
    }
    if (state.isInspecting) {
        InlineProgress()
        return
    }
    state.existing?.let { existing ->
        existing.problem?.let {
            ErrorNotice(error = it, onDismiss = null)
            return
        }
        Notice(
            kind = NoticeKind.INFO,
            message = if (existing.known) strings.knownMultirepo(state.name) else strings.existingMultirepo(state.name),
            onDismiss = null,
        )
    }
    Text(strings.multirepoRepositoriesTitle, style = MaterialTheme.typography.titleMedium)
    if (state.candidates.isEmpty()) Text(strings.noRepositoriesFound, style = MaterialTheme.typography.bodyMedium, color = secondary)
    val problems = state.aliasProblems
    state.candidates.forEach { candidate ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (state.isExisting) {
                Text(
                    candidate.alias,
                    style = ShogunTheme.typography.data,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(AliasFieldWidth),
                )
            } else {
                Checkbox(
                    checked = candidate.included,
                    onCheckedChange = { state.setIncluded(candidate.path, it) },
                    enabled = !state.isSaving,
                    modifier = Modifier.handPointer(!state.isSaving).testTag("include-${candidate.name}"),
                )
                OutlinedTextField(
                    value = candidate.alias,
                    onValueChange = { state.setAlias(candidate.path, it) },
                    textStyle = ShogunTheme.typography.data,
                    singleLine = true,
                    enabled = candidate.included && !state.isSaving,
                    isError = candidate.path in problems,
                    label = { Text(strings.multirepoAliasLabel) },
                    modifier = Modifier.width(AliasFieldWidth).trackTextInputFocus(),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    listOfNotNull(candidate.name, strings.alreadyAProject.takeIf { candidate.project != null }).joinToString(" · "),
                    style = ShogunTheme.typography.data,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!state.isExisting || (candidate.project == null && state.existing?.known == false)) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = candidate.useProposedFolder,
                        onCheckedChange = { state.setUseProposedFolder(candidate.path, it) },
                        enabled = candidate.included && !state.isSaving,
                        modifier = Modifier.handPointer(candidate.included && !state.isSaving).testTag("proposed-${candidate.name}"),
                    )
                    Text(
                        strings.useWorktreesFolder(state.proposedFolder(candidate)),
                        style = ShogunTheme.typography.dataSmall,
                        color = secondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                problems[candidate.path]?.let { problem ->
                    Text(
                        if (problem == AliasProblem.DUPLICATED) strings.multirepoAliasDuplicated else strings.multirepoAliasInvalid,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
    if (!state.isExisting) {
        NeutralTextButton(onClick = { pickFolder(state.folder.orEmpty(), strings.addRepositoryFromElsewhere)?.let(state::addFromElsewhere) }) {
            Text(strings.addRepositoryFromElsewhere)
        }
    }
    state.notRepository?.let { Text(strings.notARepository(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    Text(strings.detectedGroupsTitle, style = MaterialTheme.typography.titleMedium)
    if (state.groups.isEmpty()) {
        Text(strings.noGroupsDetected, style = MaterialTheme.typography.bodyMedium, color = secondary)
    } else {
        state.groups.forEach { group ->
            Text("${group.branch} · ${group.aliases.joinToString(", ")}", style = ShogunTheme.typography.data)
        }
    }
}
