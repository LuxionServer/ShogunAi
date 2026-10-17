package app.luxion.shogunai.ui.projectconfig

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.SimulatorRuntime
import app.luxion.shogunai.ui.components.DropdownSelector
import app.luxion.shogunai.ui.components.SectionCard
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.worktreeError

private data class NamedOption(val name: String?)

@Composable
fun SimulatorSettingsSection(viewModel: ProjectConfigViewModel) {
    val state = viewModel.simulatorSection
    if (state == SimulatorSectionState.Hidden) return
    val strings = LocalStrings.current
    SectionCard(title = strings.simulatorSection) {
        Text(
            strings.simulatorSectionDescription,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (state) {
            SimulatorSectionState.Loading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            is SimulatorSectionState.Failed -> Text(
                strings.worktreeError(state.error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            is SimulatorSectionState.Available -> AvailableSimulatorSettings(viewModel, state)
            SimulatorSectionState.Hidden -> Unit
        }
    }
}

@Composable
private fun AvailableSimulatorSettings(viewModel: ProjectConfigViewModel, state: SimulatorSectionState.Available) {
    val strings = LocalStrings.current
    val runtimeNames = state.runtimes.map { it.name }
    DropdownSelector(
        options = listOf(NamedOption(null)) + (runtimeNames + listOfNotNull(viewModel.simulatorRuntime)).distinct().map(::NamedOption),
        selected = NamedOption(viewModel.simulatorRuntime),
        onSelect = { viewModel.simulatorRuntime = it.name },
        label = { option -> option.name?.let { if (it in runtimeNames) it else strings.notOnThisMachine(it) } ?: strings.simulatorAutomatic },
        placeholder = strings.simulatorRuntimeLabel,
        fieldLabel = strings.simulatorRuntimeLabel,
        modifier = Modifier.fillMaxWidth(),
    )
    val deviceNames = deviceTypesFor(state.runtimes, viewModel.simulatorRuntime)
    DropdownSelector(
        options = listOf(NamedOption(null)) + (deviceNames + listOfNotNull(viewModel.simulatorDevice)).distinct().map(::NamedOption),
        selected = NamedOption(viewModel.simulatorDevice),
        onSelect = { viewModel.simulatorDevice = it.name },
        label = { option -> option.name?.let { if (it in deviceNames) it else strings.notOnThisMachine(it) } ?: strings.simulatorAutomatic },
        placeholder = strings.simulatorDeviceLabel,
        fieldLabel = strings.simulatorDeviceLabel,
        modifier = Modifier.fillMaxWidth(),
    )
    if (!state.kotlinMultiplatform) return
    if (state.testsWired) {
        Text(strings.simulatorTestsWired, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    } else {
        KmpWiringPanel(viewModel)
    }
}

@Composable
private fun KmpWiringPanel(viewModel: ProjectConfigViewModel) {
    val strings = LocalStrings.current
    val clipboard = LocalClipboardManager.current
    var confirming by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var installError by remember { mutableStateOf<Throwable?>(null) }
    Text(strings.simulatorTestsNotWired, style = MaterialTheme.typography.bodySmall)
    Text(viewModel.simulatorSnippet, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
    Row {
        OutlinedButton(
            onClick = { clipboard.setText(AnnotatedString(viewModel.simulatorSnippet)); copied = true },
            modifier = Modifier.padding(end = Spacing.sm),
        ) { Text(if (copied) strings.copied else strings.copySnippet) }
        OutlinedButton(onClick = { confirming = true }) { Text(strings.installInitScript) }
    }
    installError?.let {
        Text(strings.worktreeError(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(strings.installInitScriptTitle) },
            text = { Text(strings.installInitScriptMessage(viewModel.simulatorInitScriptPath)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    installError = viewModel.installSimulatorInitScript().exceptionOrNull()
                }) { Text(strings.install) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(strings.cancel) } },
        )
    }
}

private fun deviceTypesFor(runtimes: List<SimulatorRuntime>, runtimeName: String?): List<String> {
    val chosen = runtimes.filter { runtimeName == null || it.name == runtimeName }.ifEmpty { runtimes }
    return chosen.flatMap { runtime -> runtime.supportedDeviceTypes.filter { it.productFamily == "iPhone" || it.productFamily == "iPad" } }
        .map { it.name }
        .distinct()
}
