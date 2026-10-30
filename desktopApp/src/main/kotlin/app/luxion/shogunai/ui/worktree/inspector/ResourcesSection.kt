package app.luxion.shogunai.ui.worktree.inspector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.HeadlessAppProcess
import app.luxion.shogunai.domain.model.HeadlessAppState
import app.luxion.shogunai.domain.model.ReleaseRecord
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeComposeState
import app.luxion.shogunai.domain.model.WorktreeEmulator
import app.luxion.shogunai.domain.model.WorktreeSimulator
import app.luxion.shogunai.domain.model.toLocalClockTime
import app.luxion.shogunai.ui.components.GhostButton
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.InspectorSection
import app.luxion.shogunai.ui.components.ProgressSize
import app.luxion.shogunai.ui.components.Property
import app.luxion.shogunai.ui.components.PropertyList
import app.luxion.shogunai.ui.components.PropertyRow
import app.luxion.shogunai.ui.components.SectionState
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.StatusChip
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.machine.DesktopTurnWatch
import app.luxion.shogunai.ui.machine.ReleaseDesktopTurnDialog
import app.luxion.shogunai.ui.machine.clockTime
import app.luxion.shogunai.ui.theme.ShogunTheme
import app.luxion.shogunai.ui.theme.Status
import app.luxion.shogunai.ui.worktree.ProjectWorktrees
import app.luxion.shogunai.ui.worktree.emulatorStateLabel
import app.luxion.shogunai.ui.worktree.formatSize
import app.luxion.shogunai.ui.worktree.simulatorStateLabel
import app.luxion.shogunai.ui.worktree.status
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun ResourcesSection(worktree: Worktree, viewModel: ProjectWorktrees, desktopTurn: DesktopTurnWatch) {
    val strings = LocalStrings.current
    val path = worktree.path
    val present = !worktree.isPrunable
    val showEmulator = viewModel.isEmulatorRepository && present
    val showSimulator = viewModel.isSimulatorRepository && present
    val showHeadless = viewModel.isHeadlessAppRepository && present
    val showCompose = viewModel.isComposeRepository && present
    val held = desktopTurn.heldBy(path)
    val waiting = desktopTurn.waitOf(path)
    val size = viewModel.sizes[path]
    val applies = showEmulator || showSimulator || showHeadless || showCompose || held != null || waiting != null
    InspectorSection(
        id = InspectorSectionId.RESOURCES,
        title = strings.resourcesTitle,
        state = resourcesSectionState(applies, size, worktree),
        summary = size?.let(::formatSize),
        summaryIsData = true,
    ) {
        if (showEmulator) {
            EmulatorRow(
                emulator = viewModel.emulators[path],
                release = viewModel.emulatorReleases[path],
                isStarting = path in viewModel.startingEmulators,
                isStopping = path in viewModel.stoppingEmulators,
                onStart = { viewModel.startEmulator(worktree) },
                onStop = { viewModel.stopEmulator(worktree) },
            )
        }
        if (showSimulator) {
            SimulatorRow(
                simulator = viewModel.simulators[path],
                release = viewModel.simulatorReleases[path],
                isStarting = path in viewModel.startingSimulators,
                isStopping = path in viewModel.stoppingSimulators,
                onStart = { viewModel.startSimulator(worktree) },
                onStop = { viewModel.stopSimulator(worktree) },
            )
        }
        if (showCompose) {
            ComposeRow(
                state = viewModel.composeStates[path],
                unavailable = viewModel.isComposeUnavailable,
                busy = viewModel.composeActivities[path] != null,
                onStart = { viewModel.startComposeServices(worktree) },
                onBuild = { viewModel.buildComposeServices(worktree) },
                onTakeDown = { viewModel.takeDownComposeServices(worktree) },
            )
        }
        if (showHeadless) {
            HeadlessAppRow(
                app = viewModel.headlessApps[path],
                isStopping = path in viewModel.stoppingHeadlessApps,
                onStop = { viewModel.stopHeadlessApp(worktree) },
            )
        }
        DesktopTurnRow(path, desktopTurn)
        size?.let { PropertyList(listOf(Property(strings.diskTitle, formatSize(it)))) }
    }
}

fun resourcesSectionState(applies: Boolean, size: Long?, worktree: Worktree): SectionState = when {
    applies -> SectionState.Content
    size != null -> SectionState.Content
    worktree.isPrunable -> SectionState.NotApplicable
    else -> SectionState.Loading
}

@Composable
private fun EmulatorRow(
    emulator: WorktreeEmulator?,
    release: ReleaseRecord?,
    isStarting: Boolean,
    isStopping: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val strings = LocalStrings.current
    val state = if (isStarting && emulator?.state != EmulatorState.RUNNING) EmulatorState.BOOTING else emulator?.state ?: EmulatorState.STOPPED
    ResourceRow(strings.emulatorLabel) {
        emulator?.serial?.let { DataValue(it) }
        if (emulator == null && !isStarting) {
            StatusChip(Status.NEUTRAL, strings.notAcquired)
        } else {
            StatusChip(state.status(), emulatorStateLabel(state, strings))
        }
        when {
            isStarting || isStopping -> InlineProgress(size = ProgressSize.Small)
            state == EmulatorState.STOPPED -> GhostButton(onClick = onStart) { Text(strings.startEmulator) }
            else -> GhostButton(onClick = onStop) { Text(strings.stopEmulator) }
        }
    }
    ReleaseNote(emulator == null, release, strings)
}

@Composable
private fun SimulatorRow(
    simulator: WorktreeSimulator?,
    release: ReleaseRecord?,
    isStarting: Boolean,
    isStopping: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val strings = LocalStrings.current
    val state = if (isStarting && simulator?.state != SimulatorState.BOOTED) SimulatorState.BOOTING else simulator?.state ?: SimulatorState.SHUTDOWN
    ResourceRow(strings.simulatorLabel) {
        simulator?.name?.let { DataValue(it) }
        if (simulator == null && !isStarting) {
            StatusChip(Status.NEUTRAL, strings.notAcquired)
        } else {
            StatusChip(state.status(), simulatorStateLabel(state, strings))
        }
        when {
            isStarting || isStopping -> InlineProgress(size = ProgressSize.Small)
            state == SimulatorState.SHUTDOWN -> GhostButton(onClick = onStart) { Text(strings.startSimulator) }
            else -> GhostButton(onClick = onStop) { Text(strings.stopSimulator) }
        }
    }
    ReleaseNote(simulator == null, release, strings)
}

@Composable
private fun ComposeRow(
    state: WorktreeComposeState?,
    unavailable: Boolean,
    busy: Boolean,
    onStart: () -> Unit,
    onBuild: () -> Unit,
    onTakeDown: () -> Unit,
) {
    val strings = LocalStrings.current
    ResourceRow(strings.composeSectionTitle) {
        when {
            unavailable -> MutedValue(strings.composeDockerUnavailable)
            state == null -> InlineProgress(size = ProgressSize.Small)
            else -> {
                val status = state.status
                DataValue(state.project)
                if (status == null) StatusChip(Status.NEUTRAL, strings.composeNotStarted) else StatusChip(composeStatus(status), status)
            }
        }
    }
    if (!unavailable && state != null) {
        ResourceRow("") {
            if (busy) {
                InlineProgress(size = ProgressSize.Small)
            } else {
                GhostButton(onClick = onStart) { Text(strings.startComposeServices) }
                GhostButton(onClick = onBuild) { Text(strings.buildComposeServices) }
                if (state.status != null) GhostButton(onClick = onTakeDown) { Text(strings.takeDownComposeServices) }
            }
        }
    }
}

@Composable
private fun HeadlessAppRow(app: HeadlessAppProcess?, isStopping: Boolean, onStop: () -> Unit) {
    val strings = LocalStrings.current
    val running = app?.state == HeadlessAppState.RUNNING
    ResourceRow(strings.headlessAppDetailTitle) {
        if (running) StatusChip(Status.SUCCESS, strings.headlessAppRunning(app.pid)) else StatusChip(Status.NEUTRAL, strings.headlessAppStopped)
        when {
            isStopping -> InlineProgress(size = ProgressSize.Small)
            running -> GhostButton(onClick = onStop) { Text(strings.stopHeadlessApp) }
        }
    }
    if (running) ResourceRow("") { MutedValue(strings.headlessAppLog(app.logPath)) }
}

@Composable
private fun DesktopTurnRow(path: String, watch: DesktopTurnWatch) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val held = watch.heldBy(path)
    val waiting = watch.waitOf(path)
    if (held != null) {
        ResourceRow(strings.desktopTurnDetailTitle) {
            StatusChip(Status.INFO, strings.desktopHeldLabel)
            DataValue(strings.desktopTurnHeld(clockTime(held.acquiredAt), clockTime(held.expiresAt)))
            if (watch.isReleasing) {
                InlineProgress(size = ProgressSize.Small)
            } else {
                GhostButton(onClick = watch::requestRelease) { Text(strings.releaseDesktopTurn) }
            }
        }
        watch.pendingRelease?.let { pending ->
            ReleaseDesktopTurnDialog(
                holder = pending.displayName,
                onConfirm = { scope.launch { watch.confirmRelease() } },
                onDismiss = watch::cancelRelease,
            )
        }
    }
    if (waiting != null) {
        ResourceRow(strings.desktopTurnDetailTitle) {
            MutedValue(strings.desktopTurnDetailWaiting(waiting.position, clockTime(waiting.ticket.since), waiting.holder?.displayName))
        }
    }
}

@Composable
private fun ReleaseNote(notAcquired: Boolean, release: ReleaseRecord?, strings: Strings) {
    if (!notAcquired || release == null) return
    ResourceRow("") { MutedValue(strings.releasedFor(release.forWorktree, Instant.ofEpochMilli(release.releasedAt).toLocalClockTime())) }
}

@Composable
private fun ResourceRow(key: String, content: @Composable RowScope.() -> Unit) {
    PropertyRow(key) {
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

@Composable
private fun DataValue(text: String) {
    Text(text, style = ShogunTheme.typography.dataSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun MutedValue(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

fun composeStatus(status: String): Status = when {
    status.startsWith("running") && !status.contains("exited") -> Status.SUCCESS
    status.startsWith("running") -> Status.WARNING
    else -> Status.NEUTRAL
}
