package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.DaemonKind
import app.luxion.shogunai.domain.model.DaemonOwner
import app.luxion.shogunai.domain.model.DaemonState
import app.luxion.shogunai.domain.model.DaemonStopReport
import app.luxion.shogunai.domain.model.GradleDaemon
import app.luxion.shogunai.domain.usecase.totalResidentMemory
import app.luxion.shogunai.ui.components.SectionCard
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import java.time.Instant

@Composable
fun GradleDaemonsSection(viewModel: WorktreeViewModel, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    SectionCard(title = strings.gradleDaemonsTitle, modifier = modifier) {
        when (val state = viewModel.gradleDaemons) {
            GradleDaemonsState.Loading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            GradleDaemonsState.Unavailable -> SecondaryText(strings.gradleDaemonsUnavailable)
            is GradleDaemonsState.Loaded -> LoadedDaemons(state.daemons, viewModel)
        }
        viewModel.gradleSettings?.let { settings ->
            SecondaryText(
                strings.gradleWorktreeSettings(
                    settings.daemonMaxHeap?.let(::formatMemorySetting),
                    settings.idleTimeoutMinutes,
                    settings.kotlinDaemonMaxHeap?.let(::formatMemorySetting),
                ),
            )
        }
        viewModel.daemonStopReport?.let { report ->
            DaemonStopMessage(report, onDismiss = { viewModel.dismissDaemonStopReport() })
        }
        if (!viewModel.isGradleBuildCacheEnabled) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = Spacing.sm).size(18.dp),
                )
                SecondaryText(strings.gradleBuildCacheOff)
            }
        }
    }
}

@Composable
private fun LoadedDaemons(daemons: List<GradleDaemon>, viewModel: WorktreeViewModel) {
    val strings = LocalStrings.current
    if (daemons.isEmpty()) {
        SecondaryText(strings.noGradleDaemons)
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SecondaryText(strings.gradleDaemonsSummary(daemons.size, formatSize(totalResidentMemory(daemons))))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (viewModel.isStoppingDaemons) {
                CircularProgressIndicator(modifier = Modifier.padding(end = Spacing.sm).size(20.dp), strokeWidth = 2.dp)
            }
            OutlinedButton(
                onClick = { viewModel.stopIdleGradleDaemons() },
                enabled = !viewModel.isStoppingDaemons && daemons.any { it.isStoppable(daemons) },
            ) { Text(strings.stopIdleDaemons) }
        }
    }
    daemons.forEach { daemon ->
        HorizontalDivider()
        DaemonRow(
            daemon = daemon,
            canStop = !viewModel.isStoppingDaemons && daemon.isStoppable(daemons),
            onStop = { viewModel.stopGradleDaemon(daemon.pid) },
        )
    }
}

@Composable
private fun DaemonRow(daemon: GradleDaemon, canStop: Boolean, onStop: () -> Unit) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = Spacing.sm)) {
            Text(daemonTitle(daemon, strings), maxLines = 1, overflow = TextOverflow.Ellipsis)
            SecondaryText(daemonDetails(daemon, strings, Instant.now()))
        }
        OutlinedButton(onClick = onStop, enabled = canStop) { Text(strings.stopDaemon) }
    }
}

@Composable
private fun DaemonStopMessage(report: DaemonStopReport, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            strings.daemonsStopped(report.stopped.size, report.kept.size, report.failures.size),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = strings.closeMessage)
        }
    }
}

@Composable
private fun SecondaryText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

fun formatMemorySetting(size: String): String {
    val unit = when (size.last().lowercaseChar()) {
        'g' -> "GB"
        'm' -> "MB"
        else -> "KB"
    }
    return "${size.dropLast(1)} $unit"
}

private fun daemonTitle(daemon: GradleDaemon, strings: Strings): String = when (daemon.process.kind) {
    DaemonKind.GRADLE -> strings.gradleDaemonLabel(daemon.process.gradleVersion)
    DaemonKind.KOTLIN -> strings.kotlinDaemonLabel
} + " · PID ${daemon.pid}"

fun daemonDetails(daemon: GradleDaemon, strings: Strings, now: Instant): String = listOfNotNull(
    formatSize(daemon.process.residentMemoryBytes),
    when (val state = daemon.process.state) {
        DaemonState.Busy -> strings.daemonBusy
        is DaemonState.Idle -> strings.daemonIdle(relativeTime(state.since, now, strings))
        DaemonState.Unknown -> strings.daemonStateUnknown
        DaemonState.Untracked -> null
    },
    when (val owner = daemon.owner) {
        is DaemonOwner.Worktree -> owner.path.substringAfterLast('/')
        DaemonOwner.RemovedWorktree -> strings.daemonOwnerRemoved
        DaemonOwner.OtherProject -> strings.daemonOwnerOther
        null -> null
    },
).joinToString(" · ")

fun GradleDaemon.isStoppable(all: List<GradleDaemon>): Boolean = when (process.kind) {
    DaemonKind.GRADLE -> process.state is DaemonState.Idle
    DaemonKind.KOTLIN -> all.none { it.process.kind == DaemonKind.GRADLE && it.process.state !is DaemonState.Idle }
}
