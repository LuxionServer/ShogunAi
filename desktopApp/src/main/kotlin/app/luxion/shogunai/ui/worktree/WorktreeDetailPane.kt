package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeOrigin
import app.luxion.shogunai.domain.usecase.isInactive
import app.luxion.shogunai.ui.components.DangerTextButton
import app.luxion.shogunai.ui.components.IconAction
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.Notice
import app.luxion.shogunai.ui.components.NoticeKind
import app.luxion.shogunai.ui.components.PrimaryButton
import app.luxion.shogunai.ui.components.ProgressSize
import app.luxion.shogunai.ui.components.SecondaryButton
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.StatusChip
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.worktreeError
import app.luxion.shogunai.ui.theme.ShogunTheme
import app.luxion.shogunai.ui.theme.Status
import app.luxion.shogunai.ui.theme.GeistMono
import java.time.Instant

private val ButtonIconSize = 18.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorktreeDetailPane(
    worktree: Worktree,
    origin: WorktreeOrigin?,
    viewModel: WorktreeViewModel,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val clipboard = LocalClipboardManager.current
    val path = worktree.path
    val statusState = viewModel.statuses[path]
    val loadedStatus = (statusState as? WorktreeStatusState.Loaded)?.status
    val agents = viewModel.agents?.get(path).orEmpty()
    val ports = portEntries(viewModel.ports[path].orEmpty())
    val size = viewModel.sizes[path]
    val isCopied = viewModel.copiedWorktreePath == path
    val inactive = !worktree.isPrunable && isInactive(worktree, loadedStatus, agents, Instant.now())
    val showDevices = !worktree.isPrunable && (viewModel.isEmulatorRepository || viewModel.isSimulatorRepository)

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    worktree.branch ?: strings.detachedHead,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = GeistMono),
                    modifier = Modifier.weight(1f),
                )
                if (onClose != null) {
                    IconAction(Icons.Default.Close, strings.closeDetail, onClick = onClose)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (worktree.isMain) StatusChip(Status.NEUTRAL, strings.mainWorktree)
                origin?.let { StatusChip(Status.NEUTRAL, it.label(strings)) }
                if (worktree.isLocked) StatusChip(WorktreeIndicator.LOCKED.status(), strings.locked)
                if (worktree.isPrunable) StatusChip(WorktreeIndicator.FOLDER_MISSING.status(), strings.folderMissing)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(path, style = ShogunTheme.typography.dataSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconAction(
                    icon = Icons.Default.ContentCopy,
                    label = strings.copyPath,
                    onClick = { clipboard.setText(AnnotatedString(path)) },
                )
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PrimaryButton(onClick = { viewModel.copyLaunchCommand(worktree) }) {
                Icon(
                    imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.padding(end = Spacing.sm).size(ButtonIconSize),
                )
                Text(if (isCopied) strings.copied else strings.copyLaunchCommand)
            }
            if (!worktree.isPrunable) {
                SecondaryButton(
                    onClick = { viewModel.openInHerdr(worktree) },
                    enabled = path !in viewModel.openingInHerdr,
                ) { Text(strings.openInHerdr) }
            }
            if (!worktree.isMain) {
                DangerTextButton(onClick = { viewModel.requestRemoval(worktree) }) { Text(strings.deleteWorktree) }
            }
        }

        viewModel.actionErrors[path]?.let { error ->
            Notice(
                kind = NoticeKind.ERROR,
                message = strings.worktreeError(error),
                onDismiss = { viewModel.dismissActionError(path) },
            )
        }

        if (!worktree.isPrunable) {
            DetailSection(strings.detailStatusSection) {
                when (statusState) {
                    null, WorktreeStatusState.Loading -> InlineProgress(size = ProgressSize.Small)
                    WorktreeStatusState.Unavailable -> MutedText(strings.statusUnavailable)
                    is WorktreeStatusState.Loaded -> {
                        val status = statusState.status
                        val mainBranchName = viewModel.mainBranch?.name
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            if (status.isMerged && mainBranchName != null) {
                                StatusChip(WorktreeIndicator.MERGED.status(), strings.mergedInto(mainBranchName))
                            }
                            if (status.uncommittedChanges > 0) {
                                StatusChip(WorktreeIndicator.UNCOMMITTED_CHANGES.status(), strings.uncommittedChanges(status.uncommittedChanges))
                            }
                            if (inactive) StatusChip(WorktreeIndicator.INACTIVE.status(), strings.inactive)
                        }
                        statusSegments(status, mainBranchName, Instant.now(), strings).forEach { MutedData(it) }
                    }
                }
            }
            if (agents.isNotEmpty()) {
                DetailSection(strings.detailAgentsSection) {
                    agents.forEach { agent ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            StatusChip(WorktreeIndicator.AGENT_RUNNING.status(), agent.name)
                            MutedText(strings.agentRunning)
                        }
                    }
                }
            }
            if (ports.isNotEmpty()) {
                DetailSection(strings.detailEnvironmentSection) {
                    ports.forEach { MutedData(it) }
                }
            }
            if (showDevices) {
                DetailSection(strings.detailDevicesSection) {
                    if (viewModel.isEmulatorRepository) {
                        EmulatorLine(
                            emulator = viewModel.emulators[path],
                            isStarting = path in viewModel.startingEmulators,
                            onStart = { viewModel.startEmulator(worktree) },
                            onStop = { viewModel.stopEmulator(worktree) },
                        )
                    }
                    if (viewModel.isSimulatorRepository) {
                        SimulatorLine(
                            simulator = viewModel.simulators[path],
                            isStarting = path in viewModel.startingSimulators,
                            onStart = { viewModel.startSimulator(worktree) },
                            onStop = { viewModel.stopSimulator(worktree) },
                        )
                    }
                }
            }
            size?.let { DetailSection(strings.detailDiskSection) { MutedData(formatSize(it)) } }
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun MutedText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MutedData(text: String) {
    Text(text, style = ShogunTheme.typography.dataSmall, color = MaterialTheme.colorScheme.onSurface)
}
