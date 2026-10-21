package app.luxion.shogunai.ui.machine

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.ComposeDownReport
import app.luxion.shogunai.domain.model.ComposeOwner
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
            MachineLoad.Unavailable -> MachineSecondaryText(strings.composeProjectsUnavailable(machine.composeUnavailableReason))
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
                            MachineDataText(project.entry.project.status, Modifier.width(150.dp))
                            ComposeOwnerCell(machine, project, onOpenWorktree)
                        }
                    }
                    MachineSecondaryText(strings.composeVolumesKept)
                }
            }
        }
        machine.composeDownReport?.let { report -> ComposeDownMessage(report, machine::dismissComposeDownReport) }
    }
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
