package app.luxion.shogunai.ui.machine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.DaemonKind
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.DaemonStopReport
import app.luxion.shogunai.ui.components.DisabledReason
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.Notice
import app.luxion.shogunai.ui.components.NoticeKind
import app.luxion.shogunai.ui.components.GhostButton
import app.luxion.shogunai.ui.components.NeutralTextButton
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
        buildCacheNotice(machine.gradleProjects.filterNot { it.gradleBuildCacheEnabled }.map { it.project.name }, strings)?.let { notice ->
            BuildCacheNotice(notice)
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
        val gradle = processes.count { it.kind == DaemonKind.GRADLE }
        MachineDataText(strings.gradleDaemonsSummary(gradle, processes.size - gradle, formatSize(processes.sumOf { it.residentMemoryBytes })))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (machine.isStoppingDaemons) InlineProgress(modifier = Modifier.padding(end = Spacing.sm))
            val stopIdleBlock = stopIdleBlock(daemons, machine.isStoppingDaemons)
            val stopIdleReason = stopIdleBlock?.let { stopIdleReason(daemons, it, strings) }
            if (stopIdleBlock == DaemonStopBlock.NONE_IDLE && stopIdleReason != null) {
                MachineStateText(stopIdleReason, Modifier.padding(end = Spacing.sm).testTag("stop-idle-reason"))
            }
            DisabledReason(stopIdleReason) {
                SecondaryButton(
                    onClick = machine::stopIdleGradleDaemons,
                    enabled = stopIdleBlock == null,
                ) { Text(strings.stopIdleDaemons) }
            }
        }
    }
    val showsState = showsDaemonState(daemons)
    daemons.forEach { daemon ->
        HorizontalDivider()
        MachineRow(
            *listOfNotNull(
                machineCell(MachineMainColumn) { MachineDataText(daemonTitle(daemon, strings)) },
                machineCell(MachineSizeColumn) { MachineDataText(formatSize(daemon.process.residentMemoryBytes)) },
                machineCell(MachineDaemonStateColumn) { MachineStateText(daemonStateText(daemon, strings)) }.takeIf { showsState },
                machineCell(MachineOwnerColumn) { DaemonOwnerCell(daemon, machine, onOpenWorktree) },
                machineActionCell {
                    val stopBlock = daemon.stopBlock(daemons, machine.isStoppingDaemons)
                    DisabledReason(stopBlock?.describe(strings)) {
                        GhostButton(
                            onClick = { machine.stopGradleDaemon(daemon.process.pid) },
                            enabled = stopBlock == null,
                        ) { Text(strings.stopDaemon) }
                    }
                },
            ).toTypedArray(),
        )
    }
}

@Composable
private fun DaemonOwnerCell(daemon: MachineDaemon, machine: MachineResources, onOpenWorktree: (String, String) -> Unit) {
    if (daemon.owner == null) {
        MachineStateText(LocalStrings.current.ownerUnknown, Modifier.testTag("daemon-owner-unknown"))
    } else {
        OwnerCell(daemon.owner, machine::projectName, onOpenWorktree)
    }
}

@Composable
private fun BuildCacheNotice(notice: BuildCacheNotice) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = Spacing.sm).size(18.dp),
        )
        Column {
            MachineSecondaryText(notice.message)
            if (notice.projects.isNotEmpty()) {
                NeutralTextButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight, contentDescription = null)
                    Text(strings.noticeDetails(notice.projects.size))
                }
                if (expanded) notice.projects.forEach { MachineDataText(it) }
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

fun showsDaemonState(daemons: List<MachineDaemon>): Boolean =
    daemons.any { it.process.state == DaemonState.Busy || it.process.state is DaemonState.Idle }

fun stopIdleReason(daemons: List<MachineDaemon>, block: DaemonStopBlock, strings: Strings): String {
    if (block != DaemonStopBlock.NONE_IDLE) return block.describe(strings)
    val gradle = daemons.filter { it.process.kind == DaemonKind.GRADLE }
    val busy = gradle.count { it.process.state == DaemonState.Busy }
    val unknown = gradle.size - busy
    return when {
        gradle.isEmpty() -> block.describe(strings)
        unknown == 0 -> strings.stopIdleAllBusy(busy)
        busy == 0 -> strings.stopIdleAllUnknown(unknown)
        else -> strings.stopIdleBusyAndUnknown(busy, unknown)
    }
}

data class BuildCacheNotice(val message: String, val projects: List<String>)

fun buildCacheNotice(projectNames: List<String>, strings: Strings): BuildCacheNotice? = when (projectNames.size) {
    0 -> null
    1 -> BuildCacheNotice("${projectNames.single()}: ${strings.gradleBuildCacheOff}", emptyList())
    else -> BuildCacheNotice(strings.gradleBuildCacheOffIn(projectNames.size), projectNames)
}

fun DaemonStopBlock.describe(strings: Strings): String = when (this) {
    DaemonStopBlock.STOPPING -> strings.stopIdleBlockedStopping
    DaemonStopBlock.BUSY -> strings.daemonStopBlockedBusy
    DaemonStopBlock.STATE_UNKNOWN -> strings.daemonStopBlockedUnknown
    DaemonStopBlock.GRADLE_BUSY -> strings.daemonStopBlockedGradleBusy
    DaemonStopBlock.NONE_IDLE -> strings.stopIdleBlockedNoneIdle
}
