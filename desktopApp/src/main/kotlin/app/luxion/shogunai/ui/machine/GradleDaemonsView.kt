package app.luxion.shogunai.ui.machine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.DaemonKind
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.DaemonStopReport
import app.luxion.shogunai.ui.components.DisabledReason
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
fun GradleDaemonsView(machine: MachineResources, onOpenWorktree: (String, String) -> Unit) {
    val strings = LocalStrings.current
    MachineViewFrame(strings.gradleDaemonsTitle) {
        MachineErrorNotice(machine.error, machine::dismissError, machine.errorRetry?.let { { machine.retryError() } })
        PoolUsageSection(machine, MachineResource.GRADLE_DAEMONS)
        when (val state = machine.gradleDaemons) {
            MachineLoad.Loading -> InlineProgress()
            MachineLoad.Unavailable -> MachineSecondaryText(strings.gradleDaemonsUnavailable)
            is MachineLoad.Loaded -> LoadedDaemons(state.value, machine, onOpenWorktree)
        }
        machine.daemonStopReport?.let { report ->
            DaemonStopMessage(report, onDismiss = machine::dismissDaemonStopReport)
        }
        machine.gradleProjects.forEach { project ->
            project.gradleSettings?.settingsText(strings)?.let { settings ->
                MachineDataText("${project.project.name} · $settings")
            }
        }
        machine.gradleProjects.filterNot { it.gradleBuildCacheEnabled }.forEach { project ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = Spacing.sm).size(18.dp),
                )
                MachineSecondaryText("${project.project.name}: ${strings.gradleBuildCacheOff}")
            }
        }
    }
}

@Composable
private fun LoadedDaemons(daemons: List<MachineDaemon>, machine: MachineResources, onOpenWorktree: (String, String) -> Unit) {
    val strings = LocalStrings.current
    if (daemons.isEmpty()) {
        MachineSecondaryText(strings.noGradleDaemons)
        return
    }
    val processes = daemons.map { it.process }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MachineDataText(strings.gradleDaemonsSummary(daemons.size, formatSize(processes.sumOf { it.residentMemoryBytes })))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (machine.isStoppingDaemons) InlineProgress(modifier = Modifier.padding(end = Spacing.sm))
            val stopIdleBlock = stopIdleBlock(daemons, machine.isStoppingDaemons)
            DisabledReason(stopIdleBlock?.describe(strings)) {
                SecondaryButton(
                    onClick = machine::stopIdleGradleDaemons,
                    enabled = stopIdleBlock == null,
                ) { Text(strings.stopIdleDaemons) }
            }
        }
    }
    daemons.forEach { daemon ->
        HorizontalDivider()
        MachineRowLine {
            MachineDataText(daemonTitle(daemon, strings), Modifier.weight(1f))
            MachineDataText(formatSize(daemon.process.residentMemoryBytes), Modifier.width(80.dp))
            MachineStateText(daemonStateText(daemon, strings), Modifier.width(170.dp))
            OwnerCell(daemon.owner, machine::projectName, onOpenWorktree, Modifier.width(MachineOwnerColumnWidth))
            Row(Modifier.width(MachineActionColumnWidth), horizontalArrangement = Arrangement.End) {
                val stopBlock = daemon.stopBlock(daemons, machine.isStoppingDaemons)
                DisabledReason(stopBlock?.describe(strings)) {
                    SecondaryButton(
                        onClick = { machine.stopGradleDaemon(daemon.process.pid) },
                        enabled = stopBlock == null,
                    ) { Text(strings.stopDaemon) }
                }
            }
        }
    }
}

@Composable
private fun DaemonStopMessage(report: DaemonStopReport, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    Notice(
        kind = if (report.failures.isEmpty()) NoticeKind.SUCCESS else NoticeKind.WARNING,
        message = strings.daemonsStopped(report.stopped.size, report.kept.size, report.failures.size),
        onDismiss = onDismiss,
    )
}

private fun daemonTitle(daemon: MachineDaemon, strings: Strings): String = when (daemon.process.kind) {
    DaemonKind.GRADLE -> strings.gradleDaemonLabel(daemon.process.gradleVersion)
    DaemonKind.KOTLIN -> strings.kotlinDaemonLabel
} + " · PID ${daemon.process.pid}"

fun daemonStateText(daemon: MachineDaemon, strings: Strings, now: Instant = Instant.now()): String =
    when (val state = daemon.process.state) {
        DaemonState.Busy -> strings.daemonBusy
        is DaemonState.Idle -> strings.daemonIdle(relativeTime(state.since, now, strings))
        DaemonState.Unknown -> strings.daemonStateUnknown
        DaemonState.Untracked -> ""
    }

enum class DaemonStopBlock { STOPPING, BUSY, STATE_UNKNOWN, GRADLE_BUSY, NONE_IDLE }

fun MachineDaemon.stopBlock(all: List<MachineDaemon>, stopping: Boolean = false): DaemonStopBlock? = when {
    stopping -> DaemonStopBlock.STOPPING
    process.kind == DaemonKind.GRADLE -> when (process.state) {
        is DaemonState.Idle -> null
        DaemonState.Busy -> DaemonStopBlock.BUSY
        DaemonState.Unknown, DaemonState.Untracked -> DaemonStopBlock.STATE_UNKNOWN
    }
    all.any { it.process.kind == DaemonKind.GRADLE && it.process.state !is DaemonState.Idle } -> DaemonStopBlock.GRADLE_BUSY
    else -> null
}

fun MachineDaemon.isStoppable(all: List<MachineDaemon>): Boolean = stopBlock(all) == null

fun stopIdleBlock(daemons: List<MachineDaemon>, stopping: Boolean): DaemonStopBlock? = when {
    stopping -> DaemonStopBlock.STOPPING
    daemons.none { it.isStoppable(daemons) } -> DaemonStopBlock.NONE_IDLE
    else -> null
}

fun DaemonStopBlock.describe(strings: Strings): String = when (this) {
    DaemonStopBlock.STOPPING -> strings.stopIdleBlockedStopping
    DaemonStopBlock.BUSY -> strings.daemonStopBlockedBusy
    DaemonStopBlock.STATE_UNKNOWN -> strings.daemonStopBlockedUnknown
    DaemonStopBlock.GRADLE_BUSY -> strings.daemonStopBlockedGradleBusy
    DaemonStopBlock.NONE_IDLE -> strings.stopIdleBlockedNoneIdle
}
