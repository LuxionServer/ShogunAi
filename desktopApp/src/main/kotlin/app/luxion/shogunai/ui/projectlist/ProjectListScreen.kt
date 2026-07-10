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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.Project

@Composable
fun ProjectListScreen(
    viewModel: ProjectListViewModel,
    onSelectProject: (Project) -> Unit,
    onNewProject: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Proyectos", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onNewProject) {
                Text("Nuevo proyecto")
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
                        modifier = Modifier.fillMaxWidth().clickable { onSelectProject(project) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
