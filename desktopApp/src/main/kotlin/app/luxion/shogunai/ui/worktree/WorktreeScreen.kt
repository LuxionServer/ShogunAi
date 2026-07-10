package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.Worktree

@Composable
fun WorktreeScreen(
    projectName: String,
    viewModel: WorktreeViewModel,
    onBack: () -> Unit,
) {
    var taskId by remember { mutableStateOf("") }
    var branchType by remember { mutableStateOf(BranchType.FEATURE) }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(projectName, style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = onBack) { Text("Proyectos") }
        }

        viewModel.errorMessage?.let { message ->
            Text(message, color = Color.Red, modifier = Modifier.padding(top = 8.dp))
        }

        Text("Nuevo worktree", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            OutlinedTextField(
                value = taskId,
                onValueChange = { taskId = it },
                label = { Text("Id de tarea") },
                modifier = Modifier.weight(1f),
            )
        }
        Row(modifier = Modifier.padding(top = 4.dp)) {
            BranchType.entries.forEach { type ->
                Row {
                    RadioButton(selected = branchType == type, onClick = { branchType = type })
                    Text(type.prefix, modifier = Modifier.padding(end = 16.dp))
                }
            }
        }
        Button(
            onClick = {
                viewModel.create(taskId, branchType)
                taskId = ""
            },
            enabled = taskId.isNotBlank(),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text("Crear worktree")
        }

        Text("Worktrees", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
        LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
            items(viewModel.worktrees) { worktree ->
                WorktreeRow(worktree = worktree, onRemove = { viewModel.remove(worktree, worktree.branch) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun WorktreeRow(worktree: Worktree, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(worktree.path)
            Text(worktree.branch ?: "(detached)", style = MaterialTheme.typography.bodySmall)
        }
        if (!worktree.isMain) {
            OutlinedButton(onClick = onRemove) {
                Text("Eliminar")
            }
        }
    }
}
