package app.luxion.shogunai.ui.shell

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.SidebarOrder
import app.luxion.shogunai.ui.components.AgentIndicator
import app.luxion.shogunai.ui.components.AgentIndicatorModel
import app.luxion.shogunai.ui.components.AgentIndicatorSize
import app.luxion.shogunai.ui.components.AgentPhase
import app.luxion.shogunai.ui.components.IconAction
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.WithTooltip
import app.luxion.shogunai.ui.components.interactive
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.multirepo.MultirepoMembership
import app.luxion.shogunai.ui.theme.ShogunTheme
import app.luxion.shogunai.ui.theme.ShogunLayout

val ExpandedSidebarWidth: Dp = ShogunLayout.sidebarMaxWidth
val RailSidebarWidth: Dp = 64.dp
private val SidebarRowHeight = 32.dp
private val RailItemSize = 36.dp
private val SidebarIconSize = 16.dp

@Composable
fun Sidebar(
    projects: List<Project>,
    selectedProjectId: String?,
    isActiveSelected: Boolean,
    isAllSelected: Boolean,
    waitingCount: Int,
    totalWorktreeCount: Int,
    activityOf: (Project) -> ProjectActivity,
    rail: Boolean,
    onSelectActive: () -> Unit,
    onSelectAll: () -> Unit,
    onSelectProject: (Project) -> Unit,
    onEditProject: (Project) -> Unit,
    onRemoveProject: (Project) -> Unit,
    onAddProject: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    multirepos: List<SidebarMultirepo> = emptyList(),
    selectedMultirepo: String? = null,
    onSelectMultirepo: (String) -> Unit = {},
    onToggleMultirepo: (String) -> Unit = {},
    onEditMultirepo: (String) -> Unit = {},
    onAddMultirepo: () -> Unit = {},
    order: SidebarOrder = SidebarOrder(),
    onMoveEntry: (SidebarLevel, String, Int) -> Unit = { _, _, _ -> },
    onSortLevel: (SidebarLevel) -> Unit = {},
    footer: @Composable () -> Unit = {},
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
                ViewRow(strings.activeWorktrees, Icons.Default.Bolt, waitingCount.takeIf { it > 0 }, selected = isActiveSelected, onClick = onSelectActive)
                ViewRow(strings.allWorktrees, Icons.AutoMirrored.Filled.ViewList, totalWorktreeCount, selected = isAllSelected, onClick = onSelectAll)
                SectionLabel(strings.projectsTitle)
            }
            val itemWidth = if (rail) Modifier else Modifier.fillMaxWidth()
            val spacing = with(LocalDensity.current) { Spacing.xxs.toPx() }
            val topLevelDrag = remember { SidebarDragState() }
            val folderDrags = remember { mutableMapOf<String, SidebarDragState>() }
            val entries = sidebarEntries(projects, multirepos, order)
            topLevelDrag.keys = entries.map { it.key }
            topLevelDrag.spacing = spacing
            val reorderItems: (SidebarLevel, String, Int, Int) -> List<ContextMenuItem> = { level, key, index, size ->
                listOfNotNull(
                    ContextMenuItem(strings.moveUp) { onMoveEntry(level, key, index - 1) }.takeIf { index > 0 },
                    ContextMenuItem(strings.moveDown) { onMoveEntry(level, key, index + 1) }.takeIf { index < size - 1 },
                    ContextMenuItem(strings.sortAlphabetically) { onSortLevel(level) },
                )
            }
            val projectItem: @Composable (Project, String, SidebarLevel, String, Int, Int, SidebarDragState) -> Unit = { project, label, level, key, index, size, drag ->
                Box(itemWidth.reorderable(drag, key) { moved, target -> onMoveEntry(level, moved, target) }) {
                    ProjectContextMenu(
                        onEdit = { onEditProject(project) },
                        onRemove = { onRemoveProject(project) },
                        reorderItems = reorderItems(level, key, index, size),
                    ) {
                        if (rail) {
                            RailProjectItem(label, selected = project.id == selectedProjectId) { onSelectProject(project) }
                        } else {
                            ProjectRow(label, activityOf(project), selected = project.id == selectedProjectId, nested = level is SidebarLevel.Folder) { onSelectProject(project) }
                        }
                    }
                }
            }
            entries.forEachIndexed { index, entry ->
                when (entry) {
                    is SidebarEntry.ProjectEntry -> projectItem(entry.project, entry.project.name, SidebarLevel.TopLevel, entry.key, index, entries.size, topLevelDrag)
                    is SidebarEntry.FolderEntry -> {
                        val folder = entry.multirepo
                        val folderLevel = SidebarLevel.Folder(folder.folder)
                        val folderDrag = folderDrags.getOrPut(folder.folder) { SidebarDragState() }
                        folderDrag.keys = folder.projects.map { it.id }
                        folderDrag.spacing = spacing
                        Column(
                            modifier = itemWidth.reorderable(topLevelDrag, entry.key) { moved, target -> onMoveEntry(SidebarLevel.TopLevel, moved, target) },
                            horizontalAlignment = if (rail) Alignment.CenterHorizontally else Alignment.Start,
                            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                        ) {
                            ContextMenuArea(
                                items = {
                                    listOf(ContextMenuItem(strings.multirepoSettingsTitle) { onEditMultirepo(folder.folder) }) +
                                        reorderItems(SidebarLevel.TopLevel, entry.key, index, entries.size)
                                },
                            ) {
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
                            if (!folder.collapsed) {
                                folder.projects.forEachIndexed { position, project ->
                                    projectItem(project, folder.labelOf(project), folderLevel, project.id, position, folder.projects.size, folderDrag)
                                }
                            }
                        }
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
        }
        footer()
    }
}

@Composable
fun SidebarFooterLine(
    message: String,
    details: String,
    icon: ImageVector,
    rail: Boolean,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String,
) {
    val colors = MaterialTheme.colorScheme
    if (rail) {
        IconAction(icon = icon, label = details, onClick = onClick, tint = colors.primary)
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.sm, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WithTooltip(label = details, modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SidebarRowHeight)
                    .interactive(onClick = onClick)
                    .padding(horizontal = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(SidebarIconSize))
                Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconAction(icon = Icons.Default.Close, label = dismissLabel, onClick = onDismiss, tint = colors.onSurfaceVariant)
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
private fun ViewRow(label: String, icon: ImageVector, count: Int?, selected: Boolean, onClick: () -> Unit) {
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
            modifier = Modifier.size(SidebarIconSize),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) colors.onSecondaryContainer else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        count?.let {
            Text(
                it.toString(),
                style = ShogunTheme.typography.dataSmall,
                color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
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
private fun ProjectContextMenu(
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    reorderItems: List<ContextMenuItem>,
    content: @Composable () -> Unit,
) {
    val strings = LocalStrings.current
    ContextMenuArea(
        items = {
            listOf(
                ContextMenuItem(strings.editProject, onEdit),
                ContextMenuItem(strings.removeProject, onRemove),
            ) + reorderItems
        },
        content = content,
    )
}

@Composable
private fun ProjectRow(label: String, activity: ProjectActivity, selected: Boolean, nested: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (nested) Spacing.sm + Spacing.lg else Spacing.sm, end = Spacing.sm)
            .height(SidebarRowHeight)
            .interactive(onClick = onClick)
            .background(if (selected) colors.secondaryContainer else colors.surfaceContainerLow)
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Spacer(Modifier.width(SidebarIconSize))
        Text(
            label,
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
        val running = strings.agentsRunning(activity.agentNames)
        AgentIndicator(
            AgentIndicatorModel(phase = AgentPhase.RUNNING, label = running, moreCount = 0, tooltip = running),
            compact = true,
            size = AgentIndicatorSize.SMALL,
        )
    }
    if (activity.waitingCount > 0) {
        Text(
            activity.waitingCount.toString(),
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
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        DisclosureToggle(collapsed = multirepo.collapsed, onToggle = onToggle)
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

@Composable
private fun DisclosureToggle(collapsed: Boolean, onToggle: () -> Unit) {
    val strings = LocalStrings.current
    val label = if (collapsed) strings.expand else strings.collapse
    WithTooltip(label = label) {
        Icon(
            if (collapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(SidebarIconSize)
                .interactive(shape = CircleShape, onClick = onToggle)
                .semantics { role = Role.Button }
                .testTag("multirepo-toggle"),
        )
    }
}

private val SidebarMarkSize = 16.dp

data class ProjectActivity(val waitingCount: Int, val agentNames: List<String>) {
    operator fun plus(other: ProjectActivity) =
        ProjectActivity(waitingCount + other.waitingCount, (agentNames + other.agentNames).distinct())

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
    val labels: Map<String, String> = emptyMap(),
) {
    fun labelOf(project: Project): String = labels[project.id] ?: project.name
}

fun multirepoChildLabels(membership: MultirepoMembership): Map<String, String> {
    val workspaceAlias = membership.multirepo.workspaceRepository?.alias ?: return emptyMap()
    val projectId = membership.projectIdOf(workspaceAlias) ?: return emptyMap()
    return mapOf(projectId to workspaceAlias)
}

sealed interface SidebarEntry {
    val name: String
    val key: String

    data class ProjectEntry(val project: Project) : SidebarEntry {
        override val name: String get() = project.name
        override val key: String get() = projectSidebarKey(project.id)
    }

    data class FolderEntry(val multirepo: SidebarMultirepo) : SidebarEntry {
        override val name: String get() = multirepo.name
        override val key: String get() = multirepoSidebarKey(multirepo.folder)
    }
}

fun sidebarEntries(projects: List<Project>, multirepos: List<SidebarMultirepo>, order: SidebarOrder = SidebarOrder()): List<SidebarEntry> {
    val nested = multirepos.flatMap { it.projects }.mapTo(mutableSetOf()) { it.id }
    val folders = multirepos.map { folder ->
        val saved = order.savedFor(SidebarLevel.Folder(folder.folder))
        folder.copy(projects = folder.projects.orderedBy(saved, Project::id, folder::labelOf))
    }
    val entries = projects.filter { it.id !in nested }.map(SidebarEntry::ProjectEntry) + folders.map(SidebarEntry::FolderEntry)
    return entries.orderedBy(order.topLevel, SidebarEntry::key, SidebarEntry::name)
}

@Composable
private fun RailProjectItem(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    WithTooltip(label = label) {
        Box(
            modifier = Modifier
                .size(RailItemSize)
                .then(if (selected) Modifier else Modifier.border(1.dp, colors.outlineVariant, MaterialTheme.shapes.small))
                .interactive(shape = MaterialTheme.shapes.small, onClick = onClick)
                .background(if (selected) colors.secondaryContainer else colors.surfaceContainerLow),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label.take(1).uppercase(),
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
        Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(SidebarIconSize))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }
}
