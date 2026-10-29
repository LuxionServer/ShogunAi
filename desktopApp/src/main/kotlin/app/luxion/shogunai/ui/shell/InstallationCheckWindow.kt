package app.luxion.shogunai.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import app.luxion.shogunai.diagnostics.copyDiagnosticReport
import app.luxion.shogunai.domain.model.CheckState
import app.luxion.shogunai.domain.model.InstallationCheck
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.PrimaryButton
import app.luxion.shogunai.ui.components.SecondaryButton
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.StatusChip
import app.luxion.shogunai.ui.components.copyText
import app.luxion.shogunai.ui.components.rememberAppIconPainter
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.theme.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val InstallationCheckWindowSize = DpSize(640.dp, 560.dp)
private val StateColumnWidth = 96.dp

@Composable
fun InstallationCheckWindow(
    appearance: Appearance,
    state: InstallationCheckState,
    buildReport: suspend () -> String,
    onClose: () -> Unit,
) {
    DialogWindow(
        onCloseRequest = onClose,
        title = appearance.strings.installationCheckTitle,
        icon = rememberAppIconPainter(),
        state = rememberDialogState(size = InstallationCheckWindowSize),
    ) {
        AppearanceScope(appearance) {
            val clipboard = LocalClipboard.current
            val scope = rememberCoroutineScope()
            var copied by remember { mutableStateOf(false) }
            LaunchedEffect(state) { state.run() }
            InstallationCheckContent(
                result = state.result,
                copied = copied,
                onCheckAgain = { scope.launch { state.run() } },
                onCopyReport = {
                    scope.launch {
                        copyDiagnosticReport(
                            build = { withContext(Dispatchers.IO) { buildReport() } },
                            copy = { clipboard.copyText(it) },
                            confirm = { copied = true },
                        )
                    }
                },
            )
        }
    }
}

@Composable
fun InstallationCheckContent(
    result: InstallationCheckResult,
    copied: Boolean,
    onCheckAgain: () -> Unit,
    onCopyReport: () -> Unit,
) {
    val strings = LocalStrings.current
    Column(modifier = Modifier.fillMaxSize().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            when (result) {
                InstallationCheckResult.Running -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    InlineProgress()
                    Text(strings.installationCheckRunning, style = MaterialTheme.typography.bodyMedium)
                }
                is InstallationCheckResult.Failed ->
                    Text(strings.installationCheckFailed(result.error.message ?: result.error::class.simpleName.orEmpty()), color = MaterialTheme.colorScheme.error)
                is InstallationCheckResult.Done -> {
                    Text(strings.installationOverview(result.report), style = MaterialTheme.typography.titleMedium)
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            result.report.checks.forEach { InstallationCheckRow(it) }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(onClick = onCheckAgain, enabled = result != InstallationCheckResult.Running) { Text(strings.installationCheckAgain) }
            SecondaryButton(onClick = onCopyReport) { Text(strings.menuCopyDiagnosticReport) }
            if (copied) Text(strings.copied, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InstallationCheckRow(check: InstallationCheck) {
    val strings = LocalStrings.current
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(modifier = Modifier.widthIn(min = StateColumnWidth, max = StateColumnWidth)) {
            StatusChip(check.state.status(), strings.installationState(check.state))
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(strings.installationSummary(check), style = MaterialTheme.typography.bodyMedium)
            strings.installationFix(check)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun CheckState.status(): Status = when (this) {
    CheckState.PASSED -> Status.SUCCESS
    CheckState.WARNING -> Status.WARNING
    CheckState.FAILED -> Status.DANGER
    CheckState.NOT_FOUND -> Status.NEUTRAL
}
