package app.luxion.shogunai.ui.machine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.MavenDaemonStopReport
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.Notice
import app.luxion.shogunai.ui.components.NoticeKind
import app.luxion.shogunai.ui.components.SecondaryButton
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.worktree.formatSize
import app.luxion.shogunai.ui.worktree.relativeTime
import java.time.Instant

@Composable
fun MavenDaemonsView(machine: MachineResources, onOpenWorktree: (String, String) -> Unit) {
    val strings = LocalStrings.current
    MachineViewFrame(strings.mavenDaemonsTitle) {
        MachineErrorNotice(machine.error, machine::dismissError, machine.errorRetry?.let { { machine.retryError() } })
        when (val state = machine.mavenDaemons) {
            MachineLoad.Loading -> InlineProgress()
            MachineLoad.Unavailable -> MachineSecondaryText(strings.mavenDaemonsUnavailable)
            is MachineLoad.Loaded -> LoadedMavenDaemons(state.value, machine, onOpenWorktree)
        }
        machine.mavenDaemonStopReport?.let { report ->
            MavenDaemonStopMessage(report, onDismiss = machine::dismissMavenDaemonStopReport)
        }
    }
}

@Composable
private fun LoadedMavenDaemons(daemons: List<MachineMavenDaemon>, machine: MachineResources, onOpenWorktree: (String, String) -> Unit) {
    val strings = LocalStrings.current
    if (daemons.isEmpty()) {
        MachineSecondaryText(strings.noMavenDaemons)
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MachineDataText(strings.mavenDaemonsSummary(daemons.size, formatSize(daemons.sumOf { it.process.residentMemoryBytes })))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (machine.isStoppingMavenDaemons) InlineProgress(modifier = Modifier.padding(end = Spacing.sm))
            SecondaryButton(
                onClick = machine::stopIdleMavenDaemons,
                enabled = !machine.isStoppingMavenDaemons && daemons.any { it.process.state is DaemonState.Idle },
            ) { Text(strings.stopIdleDaemons) }
        }
    }
    daemons.forEach { daemon ->
        HorizontalDivider()
        MachineRowLine {
            MachineDataText("Maven · PID ${daemon.process.pid}", Modifier.weight(1f))
            MachineDataText(formatSize(daemon.process.residentMemoryBytes), Modifier.width(80.dp))
            MachineStateText(mavenDaemonStateText(daemon.process.state, strings), Modifier.width(170.dp))
            OwnerCell(daemon.owner, machine::projectName, onOpenWorktree, Modifier.width(MachineOwnerColumnWidth))
            Row(Modifier.width(MachineActionColumnWidth), horizontalArrangement = Arrangement.End) {
                SecondaryButton(
                    onClick = { machine.stopMavenDaemon(daemon.process.pid) },
                    enabled = !machine.isStoppingMavenDaemons && daemon.process.state is DaemonState.Idle,
                ) { Text(strings.stopDaemon) }
            }
        }
    }
}

@Composable
private fun MavenDaemonStopMessage(report: MavenDaemonStopReport, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    Notice(
        kind = if (report.failures.isEmpty()) NoticeKind.SUCCESS else NoticeKind.WARNING,
        message = strings.daemonsStopped(report.stopped.size, report.kept.size, report.failures.size),
        onDismiss = onDismiss,
    )
}

fun mavenDaemonStateText(state: DaemonState, strings: Strings, now: Instant = Instant.now()): String = when (state) {
    DaemonState.Busy -> strings.daemonBusy
    is DaemonState.Idle -> strings.daemonIdle(relativeTime(state.since, now, strings))
    DaemonState.Unknown, DaemonState.Untracked -> strings.daemonStateUnknown
}
