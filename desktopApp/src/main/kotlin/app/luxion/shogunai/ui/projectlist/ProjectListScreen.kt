package app.luxion.shogunai.ui.projectlist

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.ui.components.SegmentedSelector
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.components.onNewItemShortcut

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
        modifier = Modifier.fillMaxSize().padding(16.dp)
            .verticalScroll(rememberScrollState())
            .onNewItemShortcut(action = onNewProject),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row {
                OutlinedButton(onClick = { viewModel.refresh() }, modifier = Modifier.padding(end = 8.dp)) {
                    Text(strings.reload)
                }
                Button(onClick = onNewProject) {
                    Text(strings.newProject)
                }
            }
        }

        if (viewModel.projects.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = viewModel.searchQuery,
                    onValueChange = { viewModel.searchQuery = it },
                    label = { Text(strings.search) },
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
                Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    Text(strings.noProjectsYet)
                }
            }
            viewModel.visibleProjects.isEmpty() -> {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    Text(strings.noProjectsMatch(viewModel.searchQuery))
                }
            }
            else -> {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    viewModel.visibleProjects.forEach { project ->
                        ListItem(
                            headlineContent = { Text(project.name) },
                            supportingContent = { Text(project.config.baseRepositoryPath) },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { onEditProject(project) }) {
                                        Icon(Icons.Default.Edit, contentDescription = strings.editProject)
                                    }
                                    IconButton(onClick = { projectPendingDeletion = project }) {
                                        Icon(Icons.Default.Delete, contentDescription = strings.deleteProject)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().clickable { onSelectProject(project) },
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
                TextButton(onClick = {
                    viewModel.deleteProject(project)
                    projectPendingDeletion = null
                }) {
                    Text(strings.delete)
                }
            },
            dismissButton = {
                TextButton(onClick = { projectPendingDeletion = null }) {
                    Text(strings.cancel)
                }
            },
        )
    }
}
