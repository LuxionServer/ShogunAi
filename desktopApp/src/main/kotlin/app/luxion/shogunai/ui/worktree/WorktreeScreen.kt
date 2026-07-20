package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
    var createMode by remember { mutableStateOf(CreateMode.NEW_BRANCH) }
    var selectedBranch by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(createMode) {
        if (createMode == CreateMode.EXISTING_BRANCH) {
            viewModel.loadEligibleBranches()
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(projectName, style = MaterialTheme.typography.headlineSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (viewModel.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp).size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.refresh() },
                        enabled = !viewModel.isLoading,
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text("Recargar") }
                    OutlinedButton(onClick = onBack) { Text("Proyectos") }
                }
            }
        }

        viewModel.errorMessage?.let { message ->
            item {
                Text(message, color = Color.Red, modifier = Modifier.padding(top = 8.dp))
            }
        }

        item {
            Text("Nuevo worktree", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = createMode == CreateMode.NEW_BRANCH,
                        onClick = { createMode = CreateMode.NEW_BRANCH },
                    )
                    Text("Rama nueva", modifier = Modifier.padding(end = 16.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = createMode == CreateMode.EXISTING_BRANCH,
                        onClick = { createMode = CreateMode.EXISTING_BRANCH },
                    )
                    Text("Rama existente")
                }
            }
        }

        when (createMode) {
            CreateMode.NEW_BRANCH -> item {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = taskId,
                        onValueChange = { taskId = it.replace(' ', '-') },
                        label = { Text("Id de tarea") },
                        isError = taskId.isNotBlank() && !viewModel.isTaskIdValid(taskId),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (taskId.isNotBlank() && !viewModel.isTaskIdValid(taskId)) {
                    Text(
                        "Id de tarea inválido",
                        color = Color.Red,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    BranchType.entries.forEach { type ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                    enabled = taskId.isNotBlank() && viewModel.isTaskIdValid(taskId),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text("Crear worktree")
                }
            }
            CreateMode.EXISTING_BRANCH -> {
                when {
                    viewModel.isLoadingBranches -> item {
                        Text("Cargando ramas...", modifier = Modifier.padding(top = 8.dp))
                    }
                    viewModel.eligibleBranches.isEmpty() -> item {
                        Text("No hay ramas disponibles para crear un worktree.", modifier = Modifier.padding(top = 8.dp))
                    }
                    else -> items(viewModel.eligibleBranches) { branch ->
                        Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedBranch == branch, onClick = { selectedBranch = branch })
                            Text(branch)
                        }
                    }
                }
                item {
                    Button(
                        onClick = {
                            selectedBranch?.let { viewModel.createFromBranch(it) }
                            selectedBranch = null
                        },
                        enabled = selectedBranch != null,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("Crear worktree")
                    }
                }
            }
        }

        item {
            Text("Worktrees", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
        }
        items(viewModel.worktrees) { worktree ->
            WorktreeRow(
                worktree = worktree,
                onRemove = { viewModel.requestRemoval(worktree) },
                onOpenTerminal = { viewModel.openTerminal(worktree) },
            )
            HorizontalDivider()
        }
    }

    viewModel.worktreePendingRemoval?.let { worktree ->
        val requiresForce = viewModel.pendingRemovalRequiresForce
        AlertDialog(
            onDismissRequest = { viewModel.dismissPendingRemoval() },
            title = { Text(if (requiresForce) "¿Forzar eliminación?" else "¿Eliminar worktree?") },
            text = {
                Column {
                    Text(
                        if (requiresForce) {
                            "\"${worktree.path}\" tiene cambios sin guardar. Se perderán si se elimina el worktree."
                        } else {
                            "¿Eliminar el worktree \"${worktree.path}\"?"
                        },
                    )
                    worktree.branch?.let { branch ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Checkbox(
                                checked = viewModel.pendingRemovalDeleteBranch,
                                onCheckedChange = { viewModel.setPendingRemovalDeleteBranch(it) },
                            )
                            Text("Eliminar también la rama local ($branch)")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmPendingRemoval() }) {
                    Text(if (requiresForce) "Forzar eliminación" else "Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPendingRemoval() }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

private enum class CreateMode { NEW_BRANCH, EXISTING_BRANCH }

@Composable
private fun WorktreeRow(worktree: Worktree, onRemove: () -> Unit, onOpenTerminal: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(worktree.path)
            Text(worktree.branch ?: "(detached)", style = MaterialTheme.typography.bodySmall)
        }
        Row {
            OutlinedButton(onClick = onOpenTerminal, modifier = Modifier.padding(end = 8.dp)) {
                Text("Terminal")
            }
            if (!worktree.isMain) {
                OutlinedButton(onClick = onRemove) {
                    Text("Eliminar")
                }
            }
        }
    }
}
