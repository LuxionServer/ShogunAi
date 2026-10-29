package app.luxion.shogunai.ui.worktree

import androidx.compose.runtime.key
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.luxion.shogunai.domain.model.HeadlessAppProcess
import app.luxion.shogunai.domain.model.HeadlessAppState
import app.luxion.shogunai.ui.components.InlineConfirmIconAction
import app.luxion.shogunai.ui.components.NoticeKind
import app.luxion.shogunai.ui.components.Notice
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import app.luxion.shogunai.ui.components.copyText
import androidx.compose.runtime.rememberCoroutineScope
import app.luxion.shogunai.ui.components.rememberInlineConfirm
import app.luxion.shogunai.ui.machine.DesktopTurnWatch
import app.luxion.shogunai.ui.machine.ReleaseDesktopTurnDialog
import app.luxion.shogunai.ui.machine.clockTime
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow
import app.luxion.shogunai.domain.model.EMULATOR_PORT_KEY
import app.luxion.shogunai.domain.model.EmulatorState
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.RunningAgent
import app.luxion.shogunai.domain.model.SimulatorState
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.ReleaseRecord
import app.luxion.shogunai.domain.model.toLocalClockTime
import app.luxion.shogunai.domain.model.WorktreeEmulator
import app.luxion.shogunai.domain.model.WorktreeComposeState
import app.luxion.shogunai.domain.model.WorktreeSimulator
import app.luxion.shogunai.domain.model.WorktreeStatus
import app.luxion.shogunai.domain.model.portKeyLabel
import app.luxion.shogunai.domain.model.WorktreeEnvironment
import app.luxion.shogunai.domain.usecase.EnvironmentFormat
import app.luxion.shogunai.domain.usecase.classifyOrigin
import app.luxion.shogunai.domain.usecase.formatEnvironment
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.NeutralTextButton
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
    desktopTurn: DesktopTurnWatch = DesktopTurnWatch.NONE,
    openWithRequests: Int = 0,
) {
    val strings = LocalStrings.current
    val status = viewModel.statuses[worktree.path]
    val origin = classifyOrigin(worktree, projectConfig, viewModel.herdrWorktreesRoot, viewModel.orcaOrigins)
    val valueColor = MaterialTheme.colorScheme.onSurfaceVariant
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(worktree.path) { viewModel.loadDetail(worktree) }
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
                InlineConfirmIconAction(
                    label = strings.copyPath,
                    onClick = { scope.launch { clipboard.copyText(worktree.path) } },
                    state = key(worktree.path) { rememberInlineConfirm() },
                )
            }
        }
        WorktreeActionBar(viewModel, worktree, openWithRequests = openWithRequests)
        viewModel.actionErrors[worktree.path]?.let { failure ->
            ErrorNotice(
                error = failure,
                onDismiss = { viewModel.dismissActionError(worktree.path) },
                projectId = projectId,
                onRetry = viewModel.actionRetry(worktree.path),
            )
        }
        viewModel.releaseNotices[worktree.path]?.forEach { notice ->
            Notice(
                kind = NoticeKind.INFO,
                message = strings.releasedNotice(notice.kind, notice.released, notice.forWorktree),
                onDismiss = { viewModel.dismissReleaseNotices(worktree.path) },
            )
        }
        StatusSection(status, viewModel.mainBranch?.name)
        AgentsSection(viewModel.agents.orEmpty()[worktree.path].orEmpty())
        ProvisioningSection(
            worktree = worktree,
            state = viewModel.provisioning[worktree.path],
            isProvisioning = worktree.path in viewModel.provisioningInProgress,
            onProvision = { viewModel.provision(worktree) },
        )
        EnvironmentSection(viewModel.environments[worktree.path], viewModel.ports[worktree.path].orEmpty())
        DevicesSection(
            emulator = viewModel.emulators[worktree.path],
            emulatorRelease = viewModel.emulatorReleases[worktree.path],
            simulatorRelease = viewModel.simulatorReleases[worktree.path],
            showEmulator = viewModel.isEmulatorRepository && !worktree.isPrunable,
            isStartingEmulator = worktree.path in viewModel.startingEmulators,
            isStoppingEmulator = worktree.path in viewModel.stoppingEmulators,
            simulator = viewModel.simulators[worktree.path],
            showSimulator = viewModel.isSimulatorRepository && !worktree.isPrunable,
            isStartingSimulator = worktree.path in viewModel.startingSimulators,
            isStoppingSimulator = worktree.path in viewModel.stoppingSimulators,
            onStartSimulator = { viewModel.startSimulator(worktree) },
            onStopSimulator = { viewModel.stopSimulator(worktree) },
            onStartEmulator = { viewModel.startEmulator(worktree) },
            onStopEmulator = { viewModel.stopEmulator(worktree) },
        )
        DesktopTurnSection(worktree, desktopTurn)
        if (viewModel.isHeadlessAppRepository && !worktree.isPrunable) {
            HeadlessAppSection(
                app = viewModel.headlessApps[worktree.path],
                isStopping = worktree.path in viewModel.stoppingHeadlessApps,
                onStop = { viewModel.stopHeadlessApp(worktree) },
            )
        }
        if (viewModel.isComposeRepository && !worktree.isPrunable) {
            ComposeSection(
                state = viewModel.composeStates[worktree.path],
                unavailable = viewModel.isComposeUnavailable,
                activity = viewModel.composeActivities[worktree.path],
                onStart = { viewModel.startComposeServices(worktree) },
                onBuild = { viewModel.buildComposeServices(worktree) },
                onTakeDown = { viewModel.takeDownComposeServices(worktree) },
            )
        }
        DiskSection(viewModel.sizes[worktree.path])
    }
}

@Composable
private fun ColumnScope.ComposeSection(
    state: WorktreeComposeState?,
    unavailable: Boolean,
    activity: ProjectWorktrees.ComposeActivity?,
    onStart: () -> Unit,
    onBuild: () -> Unit,
    onTakeDown: () -> Unit,
) {
    val strings = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionTitle(strings.composeSectionTitle)
        if (unavailable) {
            MutedText(strings.composeDockerUnavailable)
            return@Column
        }
        if (state == null) {
            InlineProgress(size = ProgressSize.Small)
            return@Column
        }
        val status = state.status
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(state.project, style = ShogunTheme.typography.dataSmall)
            if (status == null) {
                StatusChip(Status.NEUTRAL, strings.composeNotStarted)
            } else {
                StatusChip(composeStatus(status), status)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            if (activity != null) {
                InlineProgress(size = ProgressSize.Small)
            } else {
                NeutralTextButton(onClick = onStart) { Text(strings.startComposeServices, style = MaterialTheme.typography.bodySmall) }
                NeutralTextButton(onClick = onBuild) { Text(strings.buildComposeServices, style = MaterialTheme.typography.bodySmall) }
                if (status != null) {
                    NeutralTextButton(onClick = onTakeDown) { Text(strings.takeDownComposeServices, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

fun composeStatus(status: String): Status = when {
    status.startsWith("running") && !status.contains("exited") -> Status.SUCCESS
    status.startsWith("running") -> Status.WARNING
    else -> Status.NEUTRAL
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
                if (value.isNotStarted) {
                    StatusChip(Status.NEUTRAL, strings.notStarted)
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
        agents.forEach { DetailValue(strings.agentLabel(it.name, it.state)) }
    }
}

@Composable
private fun ColumnScope.ProvisioningSection(
    worktree: Worktree,
    state: ProvisioningState?,
    isProvisioning: Boolean,
    onProvision: () -> Unit,
) {
    if (worktree.isMain || worktree.isPrunable || state == null) return
    val strings = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionTitle(strings.provisioningTitle)
        when (state) {
            ProvisioningState.Checking -> {
                MutedText(strings.provisioningChecking)
                InlineProgress(size = ProgressSize.Small)
            }
            ProvisioningState.Unavailable -> MutedText(strings.provisioningUnavailable)
            is ProvisioningState.Checked -> if (state.drift.isEmpty()) {
                StatusChip(Status.SUCCESS, strings.provisioningUpToDate)
            } else {
                state.drift.forEach { DetailValue(strings.provisionDriftFile(it.path, it.state)) }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    SecondaryButton(onClick = onProvision, enabled = !isProvisioning) {
                        Text(strings.provisionAction, style = MaterialTheme.typography.bodySmall)
                    }
                    if (isProvisioning) InlineProgress(size = ProgressSize.Small)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.EnvironmentSection(environment: WorktreeEnvironment?, ports: Map<String, Int>) {
    val strings = LocalStrings.current
    val variables = environment?.variables.orEmpty()
    val awaiting = environment?.awaitingApproval.orEmpty()
    if (variables.isEmpty() && ports.isEmpty() && awaiting.isEmpty()) return
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionTitle(strings.environmentTitle)
        if (ports.isNotEmpty()) DetailValue(portsLabel(ports))
        if (variables.isNotEmpty()) {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    variables.forEach { (name, value) -> DetailValue("$name=$value") }
                }
            }
            NeutralTextButton(onClick = { scope.launch { clipboard.copyText(environmentAsDotenv(environment!!)) } }) {
                Text(strings.copyAsEnv, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (awaiting.isNotEmpty()) MutedText(strings.variablesAwaitingApproval(awaiting))
    }
}

fun environmentAsDotenv(environment: WorktreeEnvironment): String =
    formatEnvironment(environment, EnvironmentFormat.DOTENV)

@Composable
private fun ColumnScope.DevicesSection(
    emulator: WorktreeEmulator?,
    emulatorRelease: ReleaseRecord?,
    simulatorRelease: ReleaseRecord?,
    showEmulator: Boolean,
    isStartingEmulator: Boolean,
    isStoppingEmulator: Boolean,
    simulator: WorktreeSimulator?,
    showSimulator: Boolean,
    isStartingSimulator: Boolean,
    isStoppingSimulator: Boolean,
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
            EmulatorValue(emulator, emulatorRelease, isStartingEmulator, isStoppingEmulator, strings, onStartEmulator, onStopEmulator)
        }
        if (showSimulator) {
            SimulatorValue(simulator, simulatorRelease, isStartingSimulator, isStoppingSimulator, strings, onStartSimulator, onStopSimulator)
        }
    }
}

@Composable
private fun ColumnScope.DesktopTurnSection(worktree: Worktree, watch: DesktopTurnWatch) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val held = watch.heldBy(worktree.path)
    val waiting = watch.waitOf(worktree.path)
    if (held == null && waiting == null) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionTitle(strings.desktopTurnDetailTitle)
        if (held != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                StatusChip(Status.INFO, strings.desktopHeldLabel)
                Text(strings.desktopTurnHeld(clockTime(held.acquiredAt), clockTime(held.expiresAt)), style = ShogunTheme.typography.dataSmall)
                if (watch.isReleasing) {
                    InlineProgress(size = ProgressSize.Small)
                } else {
                    NeutralTextButton(onClick = watch::requestRelease) { Text(strings.releaseDesktopTurn, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        if (waiting != null) {
            DetailValue(strings.desktopTurnDetailWaiting(waiting.position, clockTime(waiting.ticket.since), waiting.holder?.displayName))
        }
    }
    if (held != null) {
        watch.pendingRelease?.let { pending ->
            ReleaseDesktopTurnDialog(
                holder = pending.displayName,
                onConfirm = { scope.launch { watch.confirmRelease() } },
                onDismiss = watch::cancelRelease,
            )
        }
    }
}

@Composable
private fun ColumnScope.HeadlessAppSection(app: HeadlessAppProcess?, isStopping: Boolean, onStop: () -> Unit) {
    val strings = LocalStrings.current
    val running = app?.state == HeadlessAppState.RUNNING
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionTitle(strings.headlessAppDetailTitle)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            if (running) {
                StatusChip(Status.SUCCESS, strings.headlessAppRunning(app.pid))
            } else {
                StatusChip(Status.NEUTRAL, strings.headlessAppStopped)
            }
            when {
                isStopping -> InlineProgress(size = ProgressSize.Small)
                running -> NeutralTextButton(onClick = onStop) { Text(strings.stopHeadlessApp, style = MaterialTheme.typography.bodySmall) }
            }
        }
        if (running) MutedText(strings.headlessAppLog(app.logPath))
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
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { heading() },
    )
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
    release: ReleaseRecord?,
    isStarting: Boolean,
    isStopping: Boolean,
    strings: Strings,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val state = if (isStarting && emulator?.state != EmulatorState.RUNNING) EmulatorState.BOOTING else emulator?.state ?: EmulatorState.STOPPED
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(strings.emulatorLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        emulator?.serial?.let { Text(it, style = ShogunTheme.typography.dataSmall) }
        if (emulator == null && !isStarting) {
            StatusChip(Status.NEUTRAL, strings.notAcquired)
        } else {
            StatusChip(state.status(), emulatorStateLabel(state, strings))
        }
        if (isStarting || isStopping) {
            InlineProgress(size = ProgressSize.Small)
        } else if (state == EmulatorState.STOPPED) {
            NeutralTextButton(onClick = onStart) { Text(strings.startEmulator, style = MaterialTheme.typography.bodySmall) }
        } else {
            NeutralTextButton(onClick = onStop) { Text(strings.stopEmulator, style = MaterialTheme.typography.bodySmall) }
        }
    }
    ReleaseNote(emulator == null, release, strings)
}

@Composable
private fun ColumnScope.SimulatorValue(
    simulator: WorktreeSimulator?,
    release: ReleaseRecord?,
    isStarting: Boolean,
    isStopping: Boolean,
    strings: Strings,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val state = if (isStarting && simulator?.state != SimulatorState.BOOTED) SimulatorState.BOOTING else simulator?.state ?: SimulatorState.SHUTDOWN
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(strings.simulatorLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        simulator?.name?.let { Text(it, style = ShogunTheme.typography.dataSmall) }
        if (simulator == null && !isStarting) {
            StatusChip(Status.NEUTRAL, strings.notAcquired)
        } else {
            StatusChip(state.status(), simulatorStateLabel(state, strings))
        }
        if (isStarting || isStopping) {
            InlineProgress(size = ProgressSize.Small)
        } else if (state == SimulatorState.SHUTDOWN) {
            NeutralTextButton(onClick = onStart) { Text(strings.startSimulator, style = MaterialTheme.typography.bodySmall) }
        } else {
            NeutralTextButton(onClick = onStop) { Text(strings.stopSimulator, style = MaterialTheme.typography.bodySmall) }
        }
    }
    ReleaseNote(simulator == null, release, strings)
}

@Composable
private fun ColumnScope.ReleaseNote(notAcquired: Boolean, release: ReleaseRecord?, strings: Strings) {
    if (!notAcquired || release == null) return
    MutedText(strings.releasedFor(release.forWorktree, Instant.ofEpochMilli(release.releasedAt).toLocalClockTime()))
}

fun statusSegments(status: WorktreeStatus, mainBranchName: String?, now: Instant, strings: Strings): List<String> =
    listOfNotNull(
        status.upstream?.arrows()?.let { "$it ${strings.upstream}" },
        status.mainBranch?.takeUnless { status.isMerged }?.arrows()?.let { arrows ->
            listOfNotNull(arrows, mainBranchName).joinToString(" ")
        },
        status.lastCommitAt?.let { strings.commitAgo(relativeTime(it, now, strings)) },
        status.createdAt?.let { strings.createdAgo(relativeTime(it, now, strings)) },
    )

fun portsLabel(ports: Map<String, Int>): String =
    ports.entries.filterNot { it.key == EMULATOR_PORT_KEY }.sortedBy { it.value }
        .joinToString(" · ") { (key, port) -> "${portKeyLabel(key)} $port" }
