package app.luxion.shogunai.ui.worktree

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import app.luxion.shogunai.ui.components.onEnterKey

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
            viewModel.loadLocalBranches()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(Spacing.md).verticalScroll(rememberScrollState())) {
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
                    val isTaskIdValid = viewModel.taskId.isNotBlank() && viewModel.isTaskIdValid(viewModel.taskId)
                    val createWorktree = {
                        viewModel.create(viewModel.taskId, viewModel.branchType)
                        viewModel.updateTaskId("")
                    }
                    OutlinedTextField(
                        value = viewModel.taskId,
                        onValueChange = { viewModel.updateTaskId(it) },
                        label = { Text("Id de tarea") },
                        isError = viewModel.taskId.isNotBlank() && !viewModel.isTaskIdValid(viewModel.taskId),
                        modifier = Modifier.fillMaxWidth().onEnterKey(enabled = isTaskIdValid, action = createWorktree),
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
                        onClick = createWorktree,
                        enabled = isTaskIdValid,
                    ) {
                        Text("Crear worktree")
                    }
                }
                CreateMode.EXISTING_BRANCH -> {
                    when {
                        viewModel.isLoadingBranches -> Text("Cargando ramas...")
                        viewModel.localBranches.isEmpty() -> Text("No hay ramas disponibles para crear un worktree.")
                        else -> DropdownSelector(
                            options = viewModel.localBranches,
                            selected = viewModel.localBranches.find { it.name == viewModel.selectedBranch },
                            onSelect = { viewModel.selectedBranch = it.name },
                            enabled = { !it.isCheckedOut },
                            label = { if (it.isCheckedOut) "${it.name} (ya tiene un worktree)" else it.name },
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
        viewModel.worktrees.forEach { worktree ->
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
                            modifier = Modifier.padding(top = Spacing.sm),
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
