package app.luxion.shogunai.ui.shell

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.filled.ViewSidebar
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.ui.components.IconAction
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.WithTooltip
import app.luxion.shogunai.ui.components.interactive
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.machine.MachineResource
import app.luxion.shogunai.ui.machine.MachineSummary
import app.luxion.shogunai.ui.machine.icon
import app.luxion.shogunai.ui.machine.text
import app.luxion.shogunai.ui.machine.title
import app.luxion.shogunai.ui.theme.Status
import app.luxion.shogunai.ui.theme.ShogunTheme

val ExpandedSidebarWidth: Dp = 240.dp
val RailSidebarWidth: Dp = 64.dp
private val SidebarRowHeight = 32.dp
private val RailItemSize = 36.dp
private val AgentDotSize = 8.dp

@Composable
fun Sidebar(
    projects: List<Project>,
    selectedProjectId: String?,
    isActiveSelected: Boolean,
    isAllSelected: Boolean,
    activeCount: Int,
    totalWorktreeCount: Int,
    activityOf: (Project) -> ProjectActivity,
    machineResources: List<MachineResource>,
    machineSummaries: Map<MachineResource, MachineSummary>,
    selectedMachine: MachineResource?,
    rail: Boolean,
    onSelectActive: () -> Unit,
    onSelectAll: () -> Unit,
    onSelectProject: (Project) -> Unit,
    onEditProject: (Project) -> Unit,
    onRemoveProject: (Project) -> Unit,
    onAddProject: () -> Unit,
    onSelectMachine: (MachineResource) -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    multirepos: List<SidebarMultirepo> = emptyList(),
    selectedMultirepo: String? = null,
    onSelectMultirepo: (String) -> Unit = {},
    onToggleMultirepo: (String) -> Unit = {},
    onEditMultirepo: (String) -> Unit = {},
    onAddMultirepo: () -> Unit = {},
) {
    val strings = LocalStrings.current
    Column(
        modifier = modifier
            .width(if (rail) RailSidebarWidth else ExpandedSidebarWidth)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(vertical = Spacing.sm),
        horizontalAlignment = if (rail) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
            horizontalArrangement = if (rail) Arrangement.Center else Arrangement.End,
        ) {
            IconAction(
                icon = Icons.AutoMirrored.Filled.ViewSidebar,
                label = if (rail) strings.expandSidebar else strings.collapseSidebar,
                onClick = onToggle,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = if (rail) Alignment.CenterHorizontally else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            if (rail) {
                RailViewItem(strings.activeWorktrees, Icons.Default.Bolt, selected = isActiveSelected, onClick = onSelectActive)
                RailViewItem(strings.allWorktrees, Icons.AutoMirrored.Filled.ViewList, selected = isAllSelected, onClick = onSelectAll)
            } else {
                SectionLabel(strings.viewsTitle)
                ViewRow(strings.activeWorktrees, Icons.Default.Bolt, activeCount, selected = isActiveSelected, onClick = onSelectActive)
                ViewRow(strings.allWorktrees, Icons.AutoMirrored.Filled.ViewList, totalWorktreeCount, selected = isAllSelected, onClick = onSelectAll)
                SectionLabel(strings.projectsTitle)
            }
            val projectItem: @Composable (Project, Boolean) -> Unit = { project, nested ->
                ProjectContextMenu(
                    onEdit = { onEditProject(project) },
                    onRemove = { onRemoveProject(project) },
                ) {
                    if (rail) {
                        RailProjectItem(project, selected = project.id == selectedProjectId) { onSelectProject(project) }
                    } else {
                        ProjectRow(project, activityOf(project), selected = project.id == selectedProjectId, nested = nested) { onSelectProject(project) }
                    }
                }
            }
            sidebarEntries(projects, multirepos).forEach { entry ->
                when (entry) {
                    is SidebarEntry.ProjectEntry -> projectItem(entry.project, false)
                    is SidebarEntry.FolderEntry -> {
                        val folder = entry.multirepo
                        ContextMenuArea(items = { listOf(ContextMenuItem(strings.multirepoSettingsTitle) { onEditMultirepo(folder.folder) }) }) {
                            if (rail) {
                                RailViewItem(folder.name, Icons.Default.Folder, selected = folder.folder == selectedMultirepo) { onSelectMultirepo(folder.folder) }
                            } else {
                                MultirepoRow(
                                    folder,
                                    selected = folder.folder == selectedMultirepo,
                                    onClick = { onSelectMultirepo(folder.folder) },
                                    onToggle = { onToggleMultirepo(folder.folder) },
                                )
                            }
                        }
                        if (!folder.collapsed) folder.projects.forEach { projectItem(it, true) }
                    }
                }
            }
            if (rail) {
                IconAction(
                    icon = Icons.Default.Add,
                    label = strings.addProject,
                    onClick = onAddProject,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                AddProjectRow(onAddProject)
                AddRow(strings.addMultirepo, Icons.Default.CreateNewFolder, onAddMultirepo)
            }
            if (machineResources.isNotEmpty() && !rail) SectionLabel(strings.machineTitle)
            machineResources.forEach { resource ->
                if (rail) {
                    RailViewItem(resource.title(strings), resource.icon, selected = resource == selectedMachine) { onSelectMachine(resource) }
                } else {
                    MachineRow(
                        label = resource.title(strings),
                        icon = resource.icon,
                        summary = machineSummaries[resource]?.text(resource, strings),
                        selected = resource == selectedMachine,
                        onClick = { onSelectMachine(resource) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = Spacing.xs),
    )
}

@Composable
private fun ViewRow(label: String, icon: ImageVector, count: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm)
            .height(SidebarRowHeight)
            .interactive(onClick = onClick)
            .background(if (selected) colors.secondaryContainer else colors.surfaceContainerLow)
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) colors.onSecondaryContainer else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            count.toString(),
            style = ShogunTheme.typography.dataSmall,
            color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun MachineRow(label: String, icon: ImageVector, summary: String?, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm)
            .height(SidebarRowHeight)
            .interactive(onClick = onClick)
            .background(if (selected) colors.secondaryContainer else colors.surfaceContainerLow)
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) colors.onSecondaryContainer else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        summary?.let {
            Text(
                it,
                style = ShogunTheme.typography.dataSmall,
                color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RailViewItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    WithTooltip(label = label) {
        Box(
            modifier = Modifier
                .size(RailItemSize)
                .interactive(shape = MaterialTheme.shapes.small, onClick = onClick)
                .background(if (selected) colors.secondaryContainer else colors.surfaceContainerLow),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ProjectContextMenu(onEdit: () -> Unit, onRemove: () -> Unit, content: @Composable () -> Unit) {
    val strings = LocalStrings.current
    ContextMenuArea(
        items = {
            listOf(
                ContextMenuItem(strings.editProject, onEdit),
                ContextMenuItem(strings.removeProject, onRemove),
            )
        },
        content = content,
    )
}

@Composable
private fun ProjectRow(project: Project, activity: ProjectActivity, selected: Boolean, nested: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (nested) Spacing.lg else Spacing.sm, end = Spacing.sm)
            .height(SidebarRowHeight)
            .interactive(onClick = onClick)
            .background(if (selected) colors.secondaryContainer else colors.surfaceContainerLow)
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            project.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) colors.onSecondaryContainer else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        ActivityMarks(activity, selected)
    }
}

@Composable
private fun ActivityMarks(activity: ProjectActivity, selected: Boolean) {
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme
    if (activity.agentNames.isNotEmpty()) {
        WithTooltip(label = strings.agentsRunning(activity.agentNames)) {
            Box(
                Modifier
                    .size(AgentDotSize)
                    .background(ShogunTheme.status.of(Status.INFO).color, CircleShape),
            )
        }
    }
    if (activity.activeCount > 0) {
        Text(
            activity.activeCount.toString(),
            style = ShogunTheme.typography.dataSmall,
            color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun MultirepoRow(multirepo: SidebarMultirepo, selected: Boolean, onClick: () -> Unit, onToggle: () -> Unit) {
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm)
            .height(SidebarRowHeight)
            .interactive(onClick = onClick)
            .background(if (selected) colors.secondaryContainer else colors.surfaceContainerLow)
            .padding(end = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        IconAction(
            icon = if (multirepo.collapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
            label = if (multirepo.collapsed) strings.expand else strings.collapse,
            onClick = onToggle,
            tint = colors.onSurfaceVariant,
        )
        Text(
            multirepo.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) colors.onSecondaryContainer else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (multirepo.needsReview) {
            Icon(
                Icons.Default.Warning,
                contentDescription = strings.multirepoNeedsReview,
                tint = colors.error,
                modifier = Modifier.size(SidebarMarkSize).testTag("multirepo-needs-review"),
            )
        }
        ActivityMarks(multirepo.activity, selected)
    }
}

private val SidebarMarkSize = 16.dp

data class ProjectActivity(val activeCount: Int, val agentNames: List<String>) {
    operator fun plus(other: ProjectActivity) =
        ProjectActivity(activeCount + other.activeCount, (agentNames + other.agentNames).distinct())

    companion object {
        val NONE = ProjectActivity(0, emptyList())
    }
}

data class SidebarMultirepo(
    val folder: String,
    val name: String,
    val projects: List<Project>,
    val activity: ProjectActivity,
    val collapsed: Boolean,
    val needsReview: Boolean = false,
)

sealed interface SidebarEntry {
    val name: String

    data class ProjectEntry(val project: Project) : SidebarEntry {
        override val name: String get() = project.name
    }

    data class FolderEntry(val multirepo: SidebarMultirepo) : SidebarEntry {
        override val name: String get() = multirepo.name
    }
}

fun sidebarEntries(projects: List<Project>, multirepos: List<SidebarMultirepo>): List<SidebarEntry> {
    val nested = multirepos.flatMap { it.projects }.mapTo(mutableSetOf()) { it.id }
    val entries = projects.filter { it.id !in nested }.map(SidebarEntry::ProjectEntry) +
        multirepos.map { it.copy(projects = it.projects.sortedBy { project -> project.name.lowercase() }) }.map(SidebarEntry::FolderEntry)
    return entries.sortedBy { it.name.lowercase() }
}

@Composable
private fun RailProjectItem(project: Project, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    WithTooltip(label = project.name) {
        Box(
            modifier = Modifier
                .size(RailItemSize)
                .then(if (selected) Modifier else Modifier.border(1.dp, colors.outlineVariant, MaterialTheme.shapes.small))
                .interactive(shape = MaterialTheme.shapes.small, onClick = onClick)
                .background(if (selected) colors.secondaryContainer else colors.surfaceContainerLow),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                project.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) colors.onSecondaryContainer else colors.onSurface,
            )
        }
    }
}

@Composable
private fun AddProjectRow(onClick: () -> Unit) = AddRow(LocalStrings.current.addProject, Icons.Default.Add, onClick)

@Composable
private fun AddRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm)
            .height(SidebarRowHeight)
            .interactive(onClick = onClick)
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }
}
