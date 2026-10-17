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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.ui.components.IconAction
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.WithTooltip
import app.luxion.shogunai.ui.components.interactive
import app.luxion.shogunai.ui.i18n.LocalStrings

val ExpandedSidebarWidth: Dp = 240.dp
val RailSidebarWidth: Dp = 64.dp
private val SidebarRowHeight = 32.dp
private val RailItemSize = 36.dp

@Composable
fun Sidebar(
    projects: List<Project>,
    selectedProjectId: String?,
    rail: Boolean,
    onSelectProject: (Project) -> Unit,
    onEditProject: (Project) -> Unit,
    onRemoveProject: (Project) -> Unit,
    onAddProject: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
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
                icon = Icons.Default.ViewSidebar,
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
            if (!rail) SectionLabel(strings.projectsTitle)
            projects.forEach { project ->
                ProjectContextMenu(
                    onEdit = { onEditProject(project) },
                    onRemove = { onRemoveProject(project) },
                ) {
                    if (rail) {
                        RailProjectItem(project, selected = project.id == selectedProjectId) { onSelectProject(project) }
                    } else {
                        ProjectRow(project, selected = project.id == selectedProjectId) { onSelectProject(project) }
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
private fun ProjectRow(project: Project, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm)
            .height(SidebarRowHeight)
            .interactive(onClick = onClick)
            .background(if (selected) colors.secondaryContainer else colors.surfaceContainerLow)
            .padding(horizontal = Spacing.sm),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            project.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) colors.onSecondaryContainer else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
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
private fun AddProjectRow(onClick: () -> Unit) {
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
        Icon(Icons.Default.Add, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(LocalStrings.current.addProject, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }
}
