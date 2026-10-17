package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.WorktreeEmulator
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.ProgressSize
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.StatusChip
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.theme.ShogunTheme

@Composable
fun EmulatorLine(emulator: WorktreeEmulator?, isStarting: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    val strings = LocalStrings.current
    val state = if (isStarting && emulator?.state != EmulatorState.RUNNING) EmulatorState.BOOTING else emulator?.state ?: EmulatorState.STOPPED
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(strings.emulatorLabel, style = MaterialTheme.typography.bodySmall, color = mutedColor)
        emulator?.serial?.let { Text(it, style = ShogunTheme.typography.dataSmall, color = mutedColor) }
        StatusChip(state.status(), emulatorStateLabel(state, strings))
        if (isStarting) {
            InlineProgress(size = ProgressSize.Small)
        } else if (state == EmulatorState.STOPPED) {
            NeutralTextButton(onClick = onStart) { Text(strings.startEmulator, style = MaterialTheme.typography.bodySmall) }
        } else {
            NeutralTextButton(onClick = onStop) { Text(strings.stopEmulator, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

fun emulatorStateLabel(state: EmulatorState, strings: Strings): String = when (state) {
    EmulatorState.STOPPED -> strings.emulatorStopped
    EmulatorState.BOOTING -> strings.emulatorBooting
    EmulatorState.RUNNING -> strings.emulatorRunning
}
