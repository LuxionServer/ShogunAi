package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.usecase.classifyOrigin
import app.luxion.shogunai.ui.components.DangerTextButton
import app.luxion.shogunai.ui.components.DropdownSelector
import app.luxion.shogunai.ui.components.ErrorNotice
import app.luxion.shogunai.ui.components.IconAction
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.Notice
import app.luxion.shogunai.ui.components.NoticeKind
import app.luxion.shogunai.ui.components.PrimaryButton
import app.luxion.shogunai.ui.components.ProgressSize
import app.luxion.shogunai.ui.components.SecondaryButton
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.machine.DesktopTurnWatch
import app.luxion.shogunai.ui.worktree.inspector.AgentSection
import app.luxion.shogunai.ui.worktree.inspector.EnvironmentSection
import app.luxion.shogunai.ui.worktree.inspector.InspectorHeader
import app.luxion.shogunai.ui.worktree.inspector.ProvisioningSection
import app.luxion.shogunai.ui.worktree.inspector.ResourcesSection
import app.luxion.shogunai.ui.worktree.inspector.StatusSection

@Composable
fun WorktreeDetailPane(
    worktree: Worktree,
    projectConfig: ProjectConfig,
    viewModel: ProjectWorktrees,
    modifier: Modifier = Modifier,
    projectName: String? = null,
    projectId: String? = null,
    desktopTurn: DesktopTurnWatch = DesktopTurnWatch.NONE,
) {
    val strings = LocalStrings.current
    val status = viewModel.statuses[worktree.path]
    val origin = classifyOrigin(worktree, projectConfig, viewModel.herdrWorktreesRoot, viewModel.orcaOrigins)
    LaunchedEffect(worktree.path) { viewModel.loadDetail(worktree) }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        InspectorHeader(worktree, origin, projectName)
        if (viewModel.hasAgentChoice) {
            DropdownSelector(
                options = viewModel.agentOptions,
                selected = viewModel.agentFor(worktree),
                onSelect = { viewModel.setAgent(worktree, it) },
                label = { it.displayName },
                placeholder = strings.chooseAgent,
                modifier = Modifier.fillMaxWidth(),
                fieldLabel = strings.worktreeAgentLabel,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(onClick = { viewModel.copyLaunchCommand(worktree) }, modifier = Modifier.weight(1f)) {
                Text(
                    viewModel.launchLabel(strings.copyLaunchCommand, worktree, strings),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (viewModel.hasAgentChoice) {
                AgentMenuButton(viewModel.agentOptions) { viewModel.copyLaunchCommand(worktree, picked = it) }
            }
        }
        val openActions = viewModel.openActions(worktree, strings)
        if (openActions.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                openActions.forEach { action ->
                    if (action.kind == OpenKind.FOLDER) {
                        IconAction(icon = Icons.Default.FolderOpen, label = action.label, onClick = action.onClick)
                    } else {
                        SecondaryButton(onClick = action.onClick) {
                            Text(action.label, style = MaterialTheme.typography.bodySmall)
                        }
                        if (action.kind == OpenKind.TERMINAL && viewModel.hasAgentChoice) {
                            AgentMenuButton(viewModel.agentOptions) { viewModel.openInTerminal(worktree, it) }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            if (!worktree.isPrunable) {
                val opening = worktree.path in viewModel.openingInHerdr
                SecondaryButton(
                    onClick = { viewModel.openInHerdr(worktree) },
                    enabled = !opening,
                ) {
                    Text(viewModel.launchLabel(strings.openInHerdr, worktree, strings), style = MaterialTheme.typography.bodySmall)
                    if (opening) InlineProgress(size = ProgressSize.Small)
                }
                if (viewModel.hasAgentChoice) {
                    AgentMenuButton(viewModel.agentOptions) { viewModel.openInHerdr(worktree, it) }
                }
                val openingOrca = worktree.path in viewModel.openingInOrca
                SecondaryButton(
                    onClick = { viewModel.openInOrca(worktree) },
                    enabled = !openingOrca,
                ) {
                    Text(viewModel.launchLabel(strings.openInOrca, worktree, strings), style = MaterialTheme.typography.bodySmall)
                    if (openingOrca) InlineProgress(size = ProgressSize.Small)
                }
                if (viewModel.hasAgentChoice) {
                    AgentMenuButton(viewModel.agentOptions) { viewModel.openInOrca(worktree, it) }
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
        viewModel.releaseNotices[worktree.path]?.forEach { notice ->
            Notice(
                kind = NoticeKind.INFO,
                message = strings.releasedNotice(notice.kind, notice.released, notice.forWorktree),
                onDismiss = { viewModel.dismissReleaseNotices(worktree.path) },
            )
        }
        StatusSection(status, viewModel.mainBranch?.name)
        AgentSection(worktree, viewModel.agents, viewModel.agentsReadAt)
        EnvironmentSection(viewModel.environments[worktree.path], viewModel.ports[worktree.path].orEmpty())
        ResourcesSection(worktree, viewModel, desktopTurn)
        ProvisioningSection(
            worktree = worktree,
            state = viewModel.provisioning[worktree.path],
            isProvisioning = worktree.path in viewModel.provisioningInProgress,
            onProvision = { viewModel.provision(worktree) },
        )
    }
}
