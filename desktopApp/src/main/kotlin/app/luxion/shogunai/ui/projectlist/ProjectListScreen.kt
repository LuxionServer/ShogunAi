package app.luxion.shogunai.ui.projectlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.ui.components.DangerButton
import app.luxion.shogunai.ui.components.IconAction
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.PrimaryButton
import app.luxion.shogunai.ui.components.SecondaryButton
import app.luxion.shogunai.ui.components.SegmentedSelector
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.interactive
import app.luxion.shogunai.ui.components.onNewItemShortcut
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.theme.ShogunTheme

private fun ProjectSortOrder.label(): String = when (this) {
    ProjectSortOrder.NAME_ASC -> "A-Z"
    ProjectSortOrder.NAME_DESC -> "Z-A"
}

@Composable
fun ProjectListScreen(
    viewModel: ProjectListViewModel,
    onSelectProject: (Project) -> Unit,
    onNewProject: () -> Unit,
    onEditProject: (Project) -> Unit,
) {
    val strings = LocalStrings.current
    var projectPendingDeletion by remember { mutableStateOf<Project?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.md)
            .verticalScroll(rememberScrollState())
            .onNewItemShortcut(action = onNewProject),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(strings.projectsTitle, style = MaterialTheme.typography.headlineSmall)
            Row {
                SecondaryButton(onClick = { viewModel.refresh() }, modifier = Modifier.padding(end = Spacing.sm)) {
                    Text(strings.reload)
                }
                PrimaryButton(onClick = onNewProject) {
                    Text(strings.newProject)
                }
            }
        }

        if (viewModel.projects.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = viewModel.searchQuery,
                    onValueChange = { viewModel.searchQuery = it },
                    label = { Text(strings.search) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                SegmentedSelector(
                    options = ProjectSortOrder.entries,
                    selected = viewModel.sortOrder,
                    onSelect = { viewModel.sortOrder = it },
                    label = { it.label() },
                )
            }
        }

        when {
            viewModel.projects.isEmpty() -> {
                Box(modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl), contentAlignment = Alignment.Center) {
                    Text(strings.noProjectsYet)
                }
            }
            viewModel.visibleProjects.isEmpty() -> {
                Box(modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl), contentAlignment = Alignment.Center) {
                    Text(strings.noProjectsMatch(viewModel.searchQuery))
                }
            }
            else -> {
                Column(modifier = Modifier.padding(top = Spacing.md)) {
                    viewModel.visibleProjects.forEach { project ->
                        ListItem(
                            headlineContent = { Text(project.name) },
                            supportingContent = { Text(project.config.baseRepositoryPath, style = ShogunTheme.typography.dataSmall) },
                            trailingContent = {
                                Row {
                                    IconAction(Icons.Default.Edit, strings.editProject, onClick = { onEditProject(project) })
                                    IconAction(
                                        icon = Icons.Default.Delete,
                                        label = strings.deleteProject,
                                        onClick = { projectPendingDeletion = project },
                                        tint = ShogunTheme.status.danger.color,
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().interactive { onSelectProject(project) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    projectPendingDeletion?.let { project ->
        AlertDialog(
            onDismissRequest = { projectPendingDeletion = null },
            title = { Text(strings.deleteProjectTitle) },
            text = { Text(strings.deleteProjectMessage(project.name)) },
            confirmButton = {
                DangerButton(onClick = {
                    viewModel.deleteProject(project)
                    projectPendingDeletion = null
                }) {
                    Text(strings.delete)
                }
            },
            dismissButton = {
                NeutralTextButton(onClick = { projectPendingDeletion = null }) {
                    Text(strings.cancel)
                }
            },
        )
    }
}
