package app.luxion.shogunai.ui.machine

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.ComposeDownReport
import app.luxion.shogunai.domain.model.ComposeLeftoversReport
import app.luxion.shogunai.domain.model.ComposeOwner
import app.luxion.shogunai.domain.model.ComposeResourceKind
import app.luxion.shogunai.ui.components.ShogunDialog
import app.luxion.shogunai.ui.components.DangerButton
import app.luxion.shogunai.ui.components.ErrorState
import app.luxion.shogunai.ui.components.StateAction
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.Notice
import app.luxion.shogunai.ui.components.NoticeKind
import app.luxion.shogunai.ui.components.SecondaryButton
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.i18n.LocalStrings

@Composable
fun ComposeProjectsView(machine: MachineResources, onOpenWorktree: (String, String) -> Unit) {
    val strings = LocalStrings.current
    MachineViewFrame(strings.composeProjectsTitle) {
        when (val state = machine.composeProjectList) {
            MachineLoad.Loading -> InlineProgress()
            MachineLoad.Unavailable -> ErrorState(
                message = strings.composeDockerUnavailableMessage,
                action = StateAction(strings.tryAgain, machine::retryComposeProjects),
                technicalDetails = machine.composeUnavailableReason,
            )
            is MachineLoad.Loaded -> {
                val projects = state.value
                if (projects.isEmpty()) {
                    MachineSecondaryText(strings.noComposeProjects)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MachineDataText(strings.composeProjectsSummary(projects.size), Modifier.weight(1f))
                        if (machine.isTakingDownCompose) InlineProgress(modifier = Modifier.padding(end = Spacing.sm))
                        SecondaryButton(
                            onClick = machine::takeDownRemovedComposeProjects,
                            enabled = !machine.isTakingDownCompose && machine.removedComposeProjectNames.isNotEmpty(),
                        ) { Text(strings.takeDownRemovedComposeProjects) }
                    }
                    projects.forEach { project ->
                        HorizontalDivider()
                        MachineRowLine {
                            MachineDataText(project.entry.project.name, Modifier.weight(1f))
                            MachineStateText(project.entry.project.status, Modifier.width(150.dp))
                            ComposeOwnerCell(machine, project, onOpenWorktree)
                        }
                    }
                    MachineSecondaryText(strings.composeVolumesKept)
                }
                ComposeLeftovers(machine)
            }
        }
        machine.composeDownReport?.let { report -> ComposeDownMessage(report, machine::dismissComposeDownReport) }
        machine.composeLeftoversReport?.let { report -> ComposeLeftoversMessage(report, machine::dismissComposeLeftoversReport) }
    }
}

@Composable
private fun ComposeLeftovers(machine: MachineResources) {
    val strings = LocalStrings.current
    val leftovers = machine.composeLeftovers
    if (leftovers.isEmpty()) return
    var confirmingVolumes by remember { mutableStateOf(false) }
    val volumes = leftovers.count { it.resource.kind == ComposeResourceKind.VOLUME }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.md)) {
        MachineDataText(strings.composeLeftoversTitle, Modifier.weight(1f))
        if (machine.isRemovingComposeLeftovers) InlineProgress(modifier = Modifier.padding(end = Spacing.sm))
        SecondaryButton(
            onClick = { machine.removeComposeLeftovers(removeVolumes = false) },
            enabled = !machine.isRemovingComposeLeftovers && leftovers.any { it.resource.kind != ComposeResourceKind.VOLUME },
        ) { Text(strings.removeComposeLeftovers) }
        if (volumes > 0) {
            SecondaryButton(
                onClick = { confirmingVolumes = true },
                enabled = !machine.isRemovingComposeLeftovers,
                modifier = Modifier.padding(start = Spacing.sm),
            ) { Text(strings.removeComposeLeftoversWithVolumes) }
        }
    }
    leftovers.forEach { leftover ->
        HorizontalDivider()
        MachineRowLine {
            MachineDataText(leftover.resource.name, Modifier.weight(1f))
            MachineDataText(strings.composeResourceKind(leftover.resource.kind), Modifier.width(150.dp))
            MachineDataText(leftover.resource.project, Modifier.width(MachineOwnerColumnWidth))
        }
    }
    if (confirmingVolumes) {
        ShogunDialog(
            title = strings.confirmRemoveComposeVolumesTitle,
            onDismissRequest = { confirmingVolumes = false },
            confirm = {
                DangerButton(onClick = {
                    confirmingVolumes = false
                    machine.removeComposeLeftovers(removeVolumes = true)
                }) { Text(strings.removeVolumes) }
            },
            dismiss = { NeutralTextButton(onClick = { confirmingVolumes = false }) { Text(strings.cancel) } },
        ) {
            Text(strings.confirmRemoveComposeVolumes(volumes))
        }
    }
}

@Composable
private fun ComposeLeftoversMessage(report: ComposeLeftoversReport, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    Notice(
        kind = if (report.failures.isEmpty()) NoticeKind.SUCCESS else NoticeKind.WARNING,
        message = (listOf(strings.composeResourcesRemoved(report.removed.size)) +
            report.failures.map { strings.composeResourceRemoveFailed(it.resource.name, it.detail) }).joinToString("\n"),
        onDismiss = onDismiss,
    )
}

@Composable
private fun ComposeOwnerCell(machine: MachineResources, project: MachineComposeProject, onOpenWorktree: (String, String) -> Unit) {
    val strings = LocalStrings.current
    when (val owner = project.entry.owner) {
        is ComposeOwner.Worktree -> OwnerCell(
            app.luxion.shogunai.domain.usecase.ProjectOwner.Owned(project.projectId, owner.path),
            machine::projectName,
            onOpenWorktree,
            Modifier.width(MachineOwnerColumnWidth),
        )
        ComposeOwner.RemovedWorktree -> MachineDataText(strings.composeOwnerRemoved, Modifier.width(MachineOwnerColumnWidth))
    }
}

@Composable
private fun ComposeDownMessage(report: ComposeDownReport, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    Notice(
        kind = if (report.failures.isEmpty()) NoticeKind.SUCCESS else NoticeKind.WARNING,
        message = (listOf(strings.composeProjectsTakenDown(report.takenDown.size)) +
            report.failures.map { strings.composeProjectDownFailed(it.name, it.detail) }).joinToString("\n"),
        onDismiss = onDismiss,
    )
}
