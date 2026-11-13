package app.luxion.shogunai.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import app.luxion.shogunai.domain.model.HerdrPluginState
import app.luxion.shogunai.domain.model.HerdrPluginStep
import app.luxion.shogunai.ui.components.ErrorNotice
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.PrimaryButton
import app.luxion.shogunai.ui.components.ShogunDialog
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.LocalStrings
import kotlinx.coroutines.launch

@Composable
fun HerdrPluginPart(settings: HerdrPluginSettings) {
    val status = settings.status ?: return
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.padding(top = Spacing.sm)) {
        Text(strings.herdrPluginLabel, style = MaterialTheme.typography.labelLarge)
        Text(strings.herdrPluginStatus(status.state), style = MaterialTheme.typography.bodyMedium)
        status.error?.let { Text(strings.herdrPluginWarning(it), style = MaterialTheme.typography.bodySmall) }
        if (status.state == HerdrPluginState.BROKEN) {
            Text(strings.herdrPluginBrokenHint, style = MaterialTheme.typography.bodySmall)
        }
        status.plugin?.warnings.orEmpty().forEach { Text(strings.herdrPluginWarning(it), style = MaterialTheme.typography.bodySmall) }
        settings.error?.let { ErrorNotice(error = it, onDismiss = settings::dismissError) }
        settings.action?.let { action ->
            val label = if (action == HerdrPluginState.BROKEN) strings.herdrPluginReinstall else strings.herdrPluginInstall
            PrimaryButton(onClick = settings::request, enabled = !settings.running) { Text(label) }
        }
    }
    settings.pending?.let { steps -> HerdrPluginConfirmDialog(status.state, steps, settings, onConfirm = { scope.launch { settings.confirm() } }) }
}

@Composable
private fun HerdrPluginConfirmDialog(
    state: HerdrPluginState,
    steps: List<HerdrPluginStep>,
    settings: HerdrPluginSettings,
    onConfirm: () -> Unit,
) {
    val strings = LocalStrings.current
    ShogunDialog(
        title = strings.herdrPluginDialogTitle(state),
        onDismissRequest = settings::cancel,
        confirm = { PrimaryButton(onClick = onConfirm) { Text(strings.herdrPluginRun) } },
        dismiss = { NeutralTextButton(onClick = settings::cancel) { Text(strings.claudeHooksCancel) } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(strings.herdrPluginDialogIntro, style = MaterialTheme.typography.bodyMedium)
            steps.forEach { step ->
                Text((listOf("herdr") + step.arguments).joinToString(" "), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
