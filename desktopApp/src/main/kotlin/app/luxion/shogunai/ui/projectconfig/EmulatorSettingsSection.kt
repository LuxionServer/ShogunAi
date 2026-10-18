package app.luxion.shogunai.ui.projectconfig

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.luxion.shogunai.ui.components.DropdownSelector
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.handPointer
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.worktreeError
import app.luxion.shogunai.ui.theme.ShogunTheme

private data class AvdOption(val name: String?)

@Composable
fun EmulatorSettingsSection(viewModel: ProjectConfigViewModel, modifier: Modifier = Modifier) {
    val state = viewModel.emulatorSection
    if (state == EmulatorSectionState.Hidden) return
    val strings = LocalStrings.current
    SettingsSection(title = strings.emulatorSection, modifier = modifier) {
        Text(strings.machineOnlyLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            strings.emulatorSectionDescription,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (state) {
            EmulatorSectionState.Loading -> InlineProgress()
            is EmulatorSectionState.SdkNotFound -> Text(
                strings.androidSdkNotFound(state.checked),
                color = ShogunTheme.status.danger.color,
                style = MaterialTheme.typography.bodySmall,
            )
            is EmulatorSectionState.Failed -> Text(
                strings.worktreeError(state.error),
                color = ShogunTheme.status.danger.color,
                style = MaterialTheme.typography.bodySmall,
            )
            is EmulatorSectionState.Available -> {
                val options = listOf(AvdOption(null)) + (state.avds + listOfNotNull(viewModel.emulatorAvd))
                    .distinct()
                    .map { AvdOption(it) }
                DropdownSelector(
                    options = options,
                    selected = AvdOption(viewModel.emulatorAvd),
                    onSelect = { viewModel.emulatorAvd = it.name },
                    label = { option ->
                        when {
                            option.name == null -> strings.emulatorAutomaticAvd
                            option.name !in state.avds -> strings.emulatorMissingAvd(option.name)
                            else -> option.name
                        }
                    },
                    placeholder = strings.emulatorAvdLabel,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            EmulatorSectionState.Hidden -> Unit
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = viewModel.emulatorNoWindow, onCheckedChange = { viewModel.emulatorNoWindow = it }, modifier = Modifier.handPointer())
            Text(strings.emulatorNoWindowLabel, modifier = Modifier.padding(start = Spacing.sm))
        }
    }
}
