package app.luxion.shogunai.ui.projectlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@Composable
fun ProjectListScreen(
    viewModel: ProjectListViewModel,
    onSelectProject: (Project) -> Unit,
    onNewProject: () -> Unit,
    onEditProject: (Project) -> Unit,
) {
    var projectPendingDeletion by remember { mutableStateOf<Project?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Proyectos", style = MaterialTheme.typography.headlineSmall)
            Row {
                OutlinedButton(onClick = { viewModel.refresh() }, modifier = Modifier.padding(end = 8.dp)) {
                    Text("Recargar")
                }
                Button(onClick = onNewProject) {
                    Text("Nuevo proyecto")
                }
            }
        }

        if (viewModel.projects.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay proyectos todavía. Crea uno para empezar.")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
                items(viewModel.projects) { project ->
                    ListItem(
                        headlineContent = { Text(project.name) },
                        supportingContent = { Text(project.config.baseRepositoryPath) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { onEditProject(project) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Editar proyecto")
                                }
                                IconButton(onClick = { projectPendingDeletion = project }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar proyecto")
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

    projectPendingDeletion?.let { project ->
        AlertDialog(
            onDismissRequest = { projectPendingDeletion = null },
            title = { Text("¿Eliminar proyecto?") },
            text = { Text("Se eliminará \"${project.name}\" de la lista de proyectos.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProject(project)
                    projectPendingDeletion = null
                }) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { projectPendingDeletion = null }) {
                    Text("Cancelar")
                }
            },
        )
    }
}
