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
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.model.WorktreeSimulator
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings

@Composable
fun SimulatorLine(simulator: WorktreeSimulator?, isStarting: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    val strings = LocalStrings.current
    val state = if (isStarting && simulator?.state != SimulatorState.BOOTED) {
        SimulatorState.BOOTING
    } else {
        simulator?.state ?: SimulatorState.SHUTDOWN
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            simulatorLabel(state, strings),
            style = MaterialTheme.typography.bodySmall,
            color = if (state == SimulatorState.BOOTED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isStarting) {
            CircularProgressIndicator(modifier = Modifier.padding(start = Spacing.sm).size(14.dp), strokeWidth = 2.dp)
        } else if (state == SimulatorState.SHUTDOWN) {
            TextButton(onClick = onStart) { Text(strings.startSimulator, style = MaterialTheme.typography.bodySmall) }
        } else {
            TextButton(onClick = onStop) { Text(strings.stopSimulator, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

fun simulatorLabel(state: SimulatorState, strings: Strings): String =
    listOf(
        strings.simulatorLabel,
        when (state) {
            SimulatorState.SHUTDOWN -> strings.simulatorShutdown
            SimulatorState.BOOTING -> strings.simulatorBooting
            SimulatorState.BOOTED -> strings.simulatorBooted
        },
    ).joinToString(" · ")
