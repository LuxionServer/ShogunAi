package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.WorktreeEmulator
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings

@Composable
fun EmulatorLine(emulator: WorktreeEmulator?, isStarting: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    val strings = LocalStrings.current
    val state = if (isStarting && emulator?.state != EmulatorState.RUNNING) EmulatorState.BOOTING else emulator?.state ?: EmulatorState.STOPPED
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            emulatorLabel(emulator, state, strings),
            style = MaterialTheme.typography.bodySmall,
            color = if (state == EmulatorState.RUNNING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isStarting) {
            CircularProgressIndicator(modifier = Modifier.padding(start = Spacing.sm).size(14.dp), strokeWidth = 2.dp)
        } else if (state == EmulatorState.STOPPED) {
            TextButton(onClick = onStart) { Text(strings.startEmulator, style = MaterialTheme.typography.bodySmall) }
        } else {
            TextButton(onClick = onStop) { Text(strings.stopEmulator, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

fun emulatorLabel(emulator: WorktreeEmulator?, state: EmulatorState, strings: Strings): String =
    listOfNotNull(
        strings.emulatorLabel,
        emulator?.serial,
        when (state) {
            EmulatorState.STOPPED -> strings.emulatorStopped
            EmulatorState.BOOTING -> strings.emulatorBooting
            EmulatorState.RUNNING -> strings.emulatorRunning
        },
    ).joinToString(" · ")
