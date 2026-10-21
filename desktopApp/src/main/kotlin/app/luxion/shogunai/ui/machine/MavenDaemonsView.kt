package app.luxion.shogunai.ui.machine

import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.worktree.formatSize

@Composable
fun MavenDaemonsView(machine: MachineResources, onOpenWorktree: (String, String) -> Unit) {
    val strings = LocalStrings.current
    MachineViewFrame(strings.mavenDaemonsTitle) {
        when (val state = machine.mavenDaemons) {
            MachineLoad.Loading -> InlineProgress()
            MachineLoad.Unavailable -> MachineSecondaryText(strings.mavenDaemonsUnavailable)
            is MachineLoad.Loaded -> {
                val daemons = state.value
                if (daemons.isEmpty()) {
                    MachineSecondaryText(strings.noMavenDaemons)
                } else {
                    MachineDataText(strings.mavenDaemonsSummary(daemons.size, formatSize(daemons.sumOf { it.process.residentMemoryBytes })))
                    daemons.forEach { daemon ->
                        HorizontalDivider()
                        MachineRowLine {
                            MachineDataText("Maven · PID ${daemon.process.pid}", Modifier.weight(1f))
                            MachineDataText(formatSize(daemon.process.residentMemoryBytes), Modifier.width(80.dp))
                            OwnerCell(daemon.owner, machine::projectName, onOpenWorktree, Modifier.width(MachineOwnerColumnWidth))
                        }
                    }
                }
            }
        }
        MachineSecondaryText(strings.mavenDaemonsNotStopped)
    }
}
