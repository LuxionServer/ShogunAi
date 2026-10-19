package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import app.luxion.shogunai.ui.components.copyText
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow
import app.luxion.shogunai.domain.model.Divergence
import app.luxion.shogunai.domain.model.EMULATOR_PORT_KEY
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeEmulator
import app.luxion.shogunai.domain.model.WorktreeSimulator
import app.luxion.shogunai.domain.model.WorktreeStatus
import app.luxion.shogunai.domain.model.portKeyLabel
import app.luxion.shogunai.domain.usecase.classifyOrigin
import app.luxion.shogunai.ui.components.DangerTextButton
import app.luxion.shogunai.ui.components.IconAction
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.PrimaryButton
import app.luxion.shogunai.ui.components.ProgressSize
import app.luxion.shogunai.ui.components.SecondaryButton
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.StatusChip
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.components.ErrorNotice
import app.luxion.shogunai.ui.theme.ShogunTheme
import app.luxion.shogunai.ui.theme.Status
import java.time.Instant

@Composable
fun WorktreeDetailPane(
    worktree: Worktree,
    projectConfig: ProjectConfig,
    viewModel: ProjectWorktrees,
    modifier: Modifier = Modifier,
    projectName: String? = null,
    projectId: String? = null,
) {
    val strings = LocalStrings.current
    val status = viewModel.statuses[worktree.path]
    val origin = classifyOrigin(worktree, projectConfig, viewModel.herdrWorktreesRoot)
    val valueColor = MaterialTheme.colorScheme.onSurfaceVariant
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            projectName?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = valueColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    worktree.branch ?: strings.detachedHead,
                    style = ShogunTheme.typography.data,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                origin?.let { StatusChip(Status.NEUTRAL, originLabel(it, strings)) }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                SelectionContainer(Modifier.weight(1f)) {
                    Text(worktree.path, style = ShogunTheme.typography.dataSmall, color = valueColor)
                }
                IconAction(
                    icon = Icons.Default.ContentCopy,
                    label = strings.copyPath,
                    onClick = { scope.launch { clipboard.copyText(worktree.path) } },
                )
            }
        }
        PrimaryButton(onClick = { viewModel.copyLaunchCommand(worktree) }, modifier = Modifier.fillMaxWidth()) {
            Text(strings.copyLaunchCommand, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            if (!worktree.isPrunable) {
                val opening = worktree.path in viewModel.openingInHerdr
                SecondaryButton(
                    onClick = { viewModel.openInHerdr(worktree) },
                    enabled = !opening,
                ) {
                    Text(strings.openInHerdr, style = MaterialTheme.typography.bodySmall)
                    if (opening) InlineProgress(size = ProgressSize.Small)
                }
            }
            if (!worktree.isMain) {
                DangerTextButton(onClick = { viewModel.requestRemoval(worktree) }) {
                    Text(strings.deleteWorktree, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        viewModel.actionErrors[worktree.path]?.let { failure ->
            ErrorNotice(
                error = failure,
                onDismiss = { viewModel.dismissActionError(worktree.path) },
                projectId = projectId,
                onRetry = viewModel.actionRetry(worktree.path),
            )
        }
        StatusSection(status, viewModel.mainBranch?.name)
        AgentsSection(viewModel.agents.orEmpty()[worktree.path].orEmpty())
        EnvironmentSection(viewModel.ports[worktree.path].orEmpty())
        DevicesSection(
            emulator = viewModel.emulators[worktree.path],
            showEmulator = viewModel.isEmulatorRepository && !worktree.isPrunable,
            isStartingEmulator = worktree.path in viewModel.startingEmulators,
            simulator = viewModel.simulators[worktree.path],
            showSimulator = viewModel.isSimulatorRepository && !worktree.isPrunable,
            isStartingSimulator = worktree.path in viewModel.startingSimulators,
            onStartSimulator = { viewModel.startSimulator(worktree) },
            onStopSimulator = { viewModel.stopSimulator(worktree) },
            onStartEmulator = { viewModel.startEmulator(worktree) },
            onStopEmulator = { viewModel.stopEmulator(worktree) },
        )
        DiskSection(viewModel.sizes[worktree.path])
    }
}

@Composable
private fun ColumnScope.StatusSection(status: WorktreeStatusState?, mainBranchName: String?) {
    val strings = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionTitle(strings.statusTitle)
        when (status) {
            null -> Unit
            WorktreeStatusState.Loading -> InlineProgress(size = ProgressSize.Small)
            WorktreeStatusState.Unavailable -> MutedText(strings.statusUnavailable)
            is WorktreeStatusState.Loaded -> {
                val value = status.status
                if (value.uncommittedChanges > 0) {
                    DetailValue(strings.uncommittedChanges(value.uncommittedChanges))
                }
                if (value.isMerged && mainBranchName != null) {
                    StatusChip(Status.SUCCESS, strings.mergedInto(mainBranchName))
                }
                statusSegments(value, mainBranchName, Instant.now(), strings).forEach { DetailValue(it) }
            }
        }
    }
}

@Composable
private fun ColumnScope.AgentsSection(agents: List<RunningAgent>) {
    if (agents.isEmpty()) return
    val strings = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionTitle(strings.agentsTitle)
        agents.forEach { DetailValue(it.name) }
    }
}

@Composable
private fun ColumnScope.EnvironmentSection(ports: Map<String, Int>) {
    val strings = LocalStrings.current
    if (ports.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionTitle(strings.environmentTitle)
        DetailValue(portsLabel(ports))
    }
}

@Composable
private fun ColumnScope.DevicesSection(
    emulator: WorktreeEmulator?,
    showEmulator: Boolean,
    isStartingEmulator: Boolean,
    simulator: WorktreeSimulator?,
    showSimulator: Boolean,
    isStartingSimulator: Boolean,
    onStartSimulator: () -> Unit,
    onStopSimulator: () -> Unit,
    onStartEmulator: () -> Unit,
    onStopEmulator: () -> Unit,
) {
    val strings = LocalStrings.current
    if (!showEmulator && !showSimulator) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionTitle(strings.devicesTitle)
        if (showEmulator) {
            EmulatorValue(emulator, isStartingEmulator, strings, onStartEmulator, onStopEmulator)
        }
        if (showSimulator) {
            SimulatorValue(simulator, isStartingSimulator, strings, onStartSimulator, onStopSimulator)
        }
    }
}

@Composable
private fun ColumnScope.DiskSection(size: Long?) {
    val strings = LocalStrings.current
    if (size == null) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionTitle(strings.diskTitle)
        DetailValue(formatSize(size))
    }
}

@Composable
private fun ColumnScope.SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ColumnScope.DetailValue(text: String) {
    Text(text, style = ShogunTheme.typography.dataSmall)
}

@Composable
private fun ColumnScope.MutedText(text: String) {
    Text(text, style = ShogunTheme.typography.dataSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ColumnScope.EmulatorValue(
    emulator: WorktreeEmulator?,
    isStarting: Boolean,
    strings: Strings,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val state = if (isStarting && emulator?.state != EmulatorState.RUNNING) EmulatorState.BOOTING else emulator?.state ?: EmulatorState.STOPPED
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(strings.emulatorLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        emulator?.serial?.let { Text(it, style = ShogunTheme.typography.dataSmall) }
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

@Composable
private fun ColumnScope.SimulatorValue(
    simulator: WorktreeSimulator?,
    isStarting: Boolean,
    strings: Strings,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val state = if (isStarting && simulator?.state != SimulatorState.BOOTED) SimulatorState.BOOTING else simulator?.state ?: SimulatorState.SHUTDOWN
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(strings.simulatorLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        simulator?.name?.let { Text(it, style = ShogunTheme.typography.dataSmall) }
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

fun statusSegments(status: WorktreeStatus, mainBranchName: String?, now: Instant, strings: Strings): List<String> =
    listOfNotNull(
        status.upstream?.arrows()?.let { "$it ${strings.upstream}" },
        status.mainBranch?.takeUnless { status.isMerged }?.arrows()?.let { arrows ->
            listOfNotNull(arrows, mainBranchName).joinToString(" ")
        },
        status.lastCommitAt?.let { relativeTime(it, now, strings) },
    )

fun portsLabel(ports: Map<String, Int>): String =
    ports.entries.filterNot { it.key == EMULATOR_PORT_KEY }.sortedBy { it.value }
        .joinToString(" · ") { (key, port) -> "${portKeyLabel(key)} $port" }
