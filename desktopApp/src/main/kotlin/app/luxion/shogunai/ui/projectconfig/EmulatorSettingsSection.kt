package app.luxion.shogunai.ui.projectconfig

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.ui.components.DropdownSelector
import app.luxion.shogunai.ui.components.SectionCard
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.worktreeError

private data class AvdOption(val name: String?)

@Composable
fun EmulatorSettingsSection(viewModel: ProjectConfigViewModel) {
    val state = viewModel.emulatorSection
    if (state == EmulatorSectionState.Hidden) return
    val strings = LocalStrings.current
    SectionCard(title = strings.emulatorSection) {
        Text(
            strings.emulatorSectionDescription,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (state) {
            EmulatorSectionState.Loading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            is EmulatorSectionState.SdkNotFound -> Text(
                strings.androidSdkNotFound(state.checked),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            is EmulatorSectionState.Failed -> Text(
                strings.worktreeError(state.error),
                color = MaterialTheme.colorScheme.error,
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
            Switch(checked = viewModel.emulatorNoWindow, onCheckedChange = { viewModel.emulatorNoWindow = it })
            Text(strings.emulatorNoWindowLabel, modifier = Modifier.padding(start = Spacing.sm))
        }
    }
}
