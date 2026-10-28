package app.luxion.shogunai.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.UpdateAction
import app.luxion.shogunai.domain.model.UpdateCheck
import app.luxion.shogunai.domain.model.VersionNotes
import app.luxion.shogunai.ui.components.ErrorNotice
import app.luxion.shogunai.ui.components.MarkdownText
import app.luxion.shogunai.ui.components.Notice
import app.luxion.shogunai.ui.components.NoticeKind
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.PrimaryButton
import app.luxion.shogunai.ui.components.SecondaryButton
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.handPointer
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.theme.ShogunTheme

private val ReleaseNotesMaxHeight = 420.dp

@Composable
fun UpdateNotice(state: UpdateState, modifier: Modifier = Modifier) {
    val available = state.notice ?: return
    val strings = LocalStrings.current
    Notice(
        kind = NoticeKind.INFO,
        message = strings.updateAvailableMessage(available.release.version.toString(), available.running.toString()),
        onDismiss = state::dismissNotice,
        modifier = modifier,
        details = {
            UpdateDetails(available, state, onSkip = state::skipVersion)
        },
        onIconClick = { state.showNotes(available) },
        iconDescription = strings.releaseNotesIconDescription,
    )
}

@Composable
private fun UpdateDetails(available: UpdateCheck.Available, state: UpdateState, onSkip: (() -> Unit)?) {
    val strings = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        UpdateReminder(available, strings)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            UpdateActionButton(available.action, state, strings)
            NeutralTextButton(onClick = { state.showNotes(available) }) { Text(strings.updateWhatsNew) }
            onSkip?.let { NeutralTextButton(onClick = it) { Text(strings.updateSkipVersion) } }
        }
    }
}

@Composable
private fun UpdateReminder(available: UpdateCheck.Available, strings: Strings) {
    when (val action = available.action) {
        is UpdateAction.RunCommand -> Text(
            strings.updateCommandReminder(available.channel, action.command),
            style = MaterialTheme.typography.bodyMedium,
        )
        is UpdateAction.Download -> {
            Text(strings.updateDownloadReminder, style = MaterialTheme.typography.bodyMedium)
            action.installCommand?.let {
                Text(strings.updateInstallCommandReminder(it), style = ShogunTheme.typography.dataSmall)
            }
        }
        is UpdateAction.OpenReleasePage -> Text(strings.updateReleasePageReminder, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun UpdateActionButton(action: UpdateAction, state: UpdateState, strings: Strings) {
    when (action) {
        is UpdateAction.RunCommand -> SecondaryButton(onClick = { state.copy(action.command) }) { Text(strings.updateCopy) }
        is UpdateAction.Download -> SecondaryButton(onClick = { state.open(action.asset.downloadUrl) }) { Text(strings.updateDownload) }
        is UpdateAction.OpenReleasePage -> SecondaryButton(onClick = { state.open(action.url) }) { Text(strings.updateOpenReleasePage) }
    }
}

@Composable
fun UpdateCheckDialog(state: UpdateState) {
    val result = state.manualResult ?: return
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = state::closeManualResult,
        title = { Text(strings.updateDialogTitle) },
        text = { UpdateCheckDialogContent(result, state) },
        confirmButton = {
            PrimaryButton(onClick = state::closeManualResult) { Text(strings.close) }
        },
    )
}

@Composable
private fun UpdateCheckDialogContent(result: ManualUpdateResult, state: UpdateState) {
    val strings = LocalStrings.current
    when (result) {
        ManualUpdateResult.Checking -> Text(strings.updateChecking)
        is ManualUpdateResult.Failed -> ErrorNotice(error = result.error, onDismiss = null)
        is ManualUpdateResult.Finished -> when (val check = result.check) {
            is UpdateCheck.Available -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(strings.updateAvailableDialogMessage(check.release.version.toString(), check.running.toString()))
                UpdateDetails(check, state, onSkip = null)
            }
            is UpdateCheck.UpToDate -> Text(strings.updateUpToDateMessage(check.running.toString()))
            UpdateCheck.NotDue -> Text(strings.updateChecking)
        }
    }
}

@Composable
fun ReleaseNotesDialog(state: UpdateState) {
    val available = state.notes ?: return
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = state::closeNotes,
        title = { Text(strings.releaseNotesTitle(available.release.version.toString())) },
        text = { ReleaseNotesContent(available.notes, state) },
        confirmButton = {
            PrimaryButton(onClick = state::closeNotes) { Text(strings.close) }
        },
        dismissButton = {
            SecondaryButton(onClick = { state.open(available.release.pageUrl) }) { Text(strings.releaseNotesViewOnGitHub) }
        },
    )
}

@Composable
private fun ReleaseNotesContent(versions: List<VersionNotes>, state: UpdateState) {
    val strings = LocalStrings.current
    Column(
        modifier = Modifier.heightIn(max = ReleaseNotesMaxHeight).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        versions.forEach { version ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (versions.size > 1) {
                    Text(strings.releaseNotesVersionHeading(version.version.toString()), style = MaterialTheme.typography.titleMedium)
                }
                val notes = version.notes.forLanguage(strings.language)
                if (notes == null) {
                    Text(strings.releaseNotesEmpty, style = MaterialTheme.typography.bodyMedium)
                } else {
                    MarkdownText(notes, onOpenUrl = state::open)
                }
            }
        }
    }
}

@Composable
fun UpdatesPreferences(state: UpdateState) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier.padding(top = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = state.checkAutomatically,
            onCheckedChange = state::changeCheckAutomatically,
            modifier = Modifier.handPointer(true),
        )
        Text(strings.updateCheckAutomaticallyLabel)
    }
}
