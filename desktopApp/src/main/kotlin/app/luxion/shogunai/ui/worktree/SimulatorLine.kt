package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.model.WorktreeSimulator
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.ProgressSize
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.StatusChip
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
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(strings.simulatorLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        StatusChip(state.status(), simulatorStateLabel(state, strings))
        if (isStarting) {
            InlineProgress(size = ProgressSize.Small)
        } else if (state == SimulatorState.SHUTDOWN) {
            NeutralTextButton(onClick = onStart) { Text(strings.startSimulator, style = MaterialTheme.typography.bodySmall) }
        } else {
            NeutralTextButton(onClick = onStop) { Text(strings.stopSimulator, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

fun simulatorStateLabel(state: SimulatorState, strings: Strings): String = when (state) {
    SimulatorState.SHUTDOWN -> strings.simulatorShutdown
    SimulatorState.BOOTING -> strings.simulatorBooting
    SimulatorState.BOOTED -> strings.simulatorBooted
}
