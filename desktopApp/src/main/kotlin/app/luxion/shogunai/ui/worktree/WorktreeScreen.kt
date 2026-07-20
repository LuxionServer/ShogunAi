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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.BranchType
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.ui.components.DropdownSelector
import app.luxion.shogunai.ui.components.SectionCard
import app.luxion.shogunai.ui.components.SegmentedSelector
import app.luxion.shogunai.ui.components.Spacing

private fun CreateMode.label(): String = when (this) {
    CreateMode.NEW_BRANCH -> "Rama nueva"
    CreateMode.EXISTING_BRANCH -> "Rama existente"
}

@Composable
fun WorktreeScreen(
    projectName: String,
    viewModel: WorktreeViewModel,
    onBack: () -> Unit,
) {
    LaunchedEffect(viewModel.createMode) {
        if (viewModel.createMode == CreateMode.EXISTING_BRANCH) {
            viewModel.loadEligibleBranches()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(Spacing.md)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(projectName, style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = Spacing.sm).size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
                OutlinedButton(
                    onClick = { viewModel.refresh() },
                    enabled = !viewModel.isLoading,
                    modifier = Modifier.padding(end = Spacing.sm),
                ) { Text("Recargar") }
                OutlinedButton(onClick = onBack) { Text("Proyectos") }
            }
        }

        viewModel.errorMessage?.let { message ->
            Text(message, color = Color.Red, modifier = Modifier.padding(top = Spacing.sm))
        }

        SectionCard(title = "Nuevo worktree", modifier = Modifier.padding(top = Spacing.md)) {
            SegmentedSelector(
                options = CreateMode.entries,
                selected = viewModel.createMode,
                onSelect = { viewModel.createMode = it },
                label = { it.label() },
            )
            when (viewModel.createMode) {
                CreateMode.NEW_BRANCH -> {
                    OutlinedTextField(
                        value = viewModel.taskId,
                        onValueChange = { viewModel.updateTaskId(it) },
                        label = { Text("Id de tarea") },
                        isError = viewModel.taskId.isNotBlank() && !viewModel.isTaskIdValid(viewModel.taskId),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (viewModel.taskId.isNotBlank() && !viewModel.isTaskIdValid(viewModel.taskId)) {
                        Text(
                            "Id de tarea inválido",
                            color = Color.Red,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    SegmentedSelector(
                        options = BranchType.entries,
                        selected = viewModel.branchType,
                        onSelect = { viewModel.branchType = it },
                        label = { it.prefix },
                    )
                    Button(
                        onClick = {
                            viewModel.create(viewModel.taskId, viewModel.branchType)
                            viewModel.updateTaskId("")
                        },
                        enabled = viewModel.taskId.isNotBlank() && viewModel.isTaskIdValid(viewModel.taskId),
                    ) {
                        Text("Crear worktree")
                    }
                }
                CreateMode.EXISTING_BRANCH -> {
                    when {
                        viewModel.isLoadingBranches -> Text("Cargando ramas...")
                        viewModel.eligibleBranches.isEmpty() -> Text("No hay ramas disponibles para crear un worktree.")
                        else -> DropdownSelector(
                            options = viewModel.eligibleBranches,
                            selected = viewModel.selectedBranch,
                            onSelect = { viewModel.selectedBranch = it },
                            label = { it },
                            placeholder = "Selecciona una rama",
                        )
                    }
                    Button(
                        onClick = {
                            viewModel.selectedBranch?.let { viewModel.createFromBranch(it) }
                            viewModel.selectedBranch = null
                        },
                        enabled = viewModel.selectedBranch != null,
                    ) {
                        Text("Crear worktree")
                    }
                }
            }
        }

        Text("Worktrees", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.lg))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(viewModel.worktrees) { worktree ->
                WorktreeRow(
                    worktree = worktree,
                    onRemove = { viewModel.remove(worktree, worktree.branch) },
                    onOpenTerminal = { viewModel.openTerminal(worktree) },
                )
                HorizontalDivider()
            }
        }
    }

    viewModel.worktreePendingForceRemoval?.let { worktree ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissForceRemoval() },
            title = { Text("¿Forzar eliminación?") },
            text = { Text("\"${worktree.path}\" tiene cambios sin guardar. Se perderán si se elimina el worktree.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmForceRemoval() }) {
                    Text("Forzar eliminación")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissForceRemoval() }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun WorktreeRow(worktree: Worktree, onRemove: () -> Unit, onOpenTerminal: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(worktree.path)
            Text(worktree.branch ?: "(detached)", style = MaterialTheme.typography.bodySmall)
        }
        Row {
            OutlinedButton(onClick = onOpenTerminal, modifier = Modifier.padding(end = Spacing.sm)) {
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
