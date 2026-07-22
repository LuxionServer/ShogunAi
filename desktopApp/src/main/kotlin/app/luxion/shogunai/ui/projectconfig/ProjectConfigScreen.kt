package app.luxion.shogunai.ui.projectconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.TerminalEmulator
import app.luxion.shogunai.domain.model.TerminalSelectionMode
import app.luxion.shogunai.ui.FilePicker
import app.luxion.shogunai.ui.components.DropdownSelector
import app.luxion.shogunai.ui.components.SectionCard
import app.luxion.shogunai.ui.components.SegmentedSelector
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.onEnterKey
import app.luxion.shogunai.ui.relativeToBase

private fun TerminalSelectionMode.label(): String = when (this) {
    TerminalSelectionMode.AUTO_DETECT -> "Detectar automáticamente"
    TerminalSelectionMode.FIXED -> "Fijo"
    TerminalSelectionMode.CUSTOM -> "Personalizado"
}

private fun TerminalEmulator.label(): String = when (this) {
    TerminalEmulator.MACOS_TERMINAL -> "Terminal (macOS)"
    TerminalEmulator.ITERM2 -> "iTerm2"
    TerminalEmulator.WARP -> "Warp"
    TerminalEmulator.GNOME_TERMINAL -> "GNOME Terminal"
    TerminalEmulator.KONSOLE -> "Konsole"
    TerminalEmulator.XTERM -> "XTerm"
}

@Composable
fun ProjectConfigScreen(
    viewModel: ProjectConfigViewModel,
    onSaved: (Project) -> Unit,
    onCancel: () -> Unit,
) {
    var newSecretFile by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth().padding(Spacing.md).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("Configuración de proyecto", style = MaterialTheme.typography.headlineSmall)

        SectionCard(title = "Identidad") {
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                label = { Text("Nombre") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionCard(title = "Rutas") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val pickBaseRepositoryPath = {
                    FilePicker.pickDirectory(viewModel.baseRepositoryPath)?.let {
                        viewModel.baseRepositoryPath = it
                    }
                    Unit
                }
                OutlinedTextField(
                    value = viewModel.baseRepositoryPath,
                    onValueChange = { viewModel.baseRepositoryPath = it },
                    label = { Text("Ruta del repositorio base") },
                    modifier = Modifier.weight(1f).onEnterKey(action = pickBaseRepositoryPath),
                )
                OutlinedButton(
                    onClick = pickBaseRepositoryPath,
                    modifier = Modifier.padding(start = Spacing.sm),
                ) {
                    Text("Examinar…")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val pickWorktreesRoot = {
                    FilePicker.pickDirectory(viewModel.worktreesRoot)?.let {
                        viewModel.worktreesRoot = it
                    }
                    Unit
                }
                OutlinedTextField(
                    value = viewModel.worktreesRoot,
                    onValueChange = { viewModel.worktreesRoot = it },
                    label = { Text("Carpeta raíz de worktrees") },
                    modifier = Modifier.weight(1f).onEnterKey(action = pickWorktreesRoot),
                )
                OutlinedButton(
                    onClick = pickWorktreesRoot,
                    modifier = Modifier.padding(start = Spacing.sm),
                ) {
                    Text("Examinar…")
                }
            }
        }

        SectionCard(title = "Archivos de secretos") {
            LazyColumn(modifier = Modifier.heightIn(max = 160.dp)) {
                items(viewModel.secretFiles) { file ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(file)
                        IconButton(onClick = { viewModel.removeSecretFile(file) }) {
                            Icon(Icons.Default.Close, contentDescription = "Eliminar archivo de secretos")
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val addSecretFile = {
                    viewModel.addSecretFile(newSecretFile)
                    newSecretFile = ""
                }
                OutlinedTextField(
                    value = newSecretFile,
                    onValueChange = { newSecretFile = it },
                    label = { Text("Nuevo archivo de secretos") },
                    modifier = Modifier.weight(1f).onEnterKey(action = addSecretFile),
                )
                Button(
                    onClick = addSecretFile,
                    modifier = Modifier.padding(start = Spacing.sm),
                ) {
                    Text("Añadir")
                }
                OutlinedButton(
                    onClick = {
                        FilePicker.pickFiles(viewModel.baseRepositoryPath).forEach { absolutePath ->
                            viewModel.addSecretFile(relativeToBase(viewModel.baseRepositoryPath, absolutePath))
                        }
                    },
                    modifier = Modifier.padding(start = Spacing.sm),
                ) {
                    Text("Examinar…")
                }
            }
        }

        SectionCard(title = "Terminal") {
            SegmentedSelector(
                options = TerminalSelectionMode.entries,
                selected = viewModel.terminalSelectionMode,
                onSelect = { viewModel.terminalSelectionMode = it },
                label = { it.label() },
            )
            if (viewModel.terminalSelectionMode == TerminalSelectionMode.FIXED) {
                DropdownSelector(
                    options = TerminalEmulator.entries,
                    selected = viewModel.terminalEmulator,
                    onSelect = { viewModel.terminalEmulator = it },
                    label = { it.label() },
                    placeholder = "Selecciona un emulador",
                )
            }
            if (viewModel.terminalSelectionMode == TerminalSelectionMode.CUSTOM) {
                OutlinedTextField(
                    value = viewModel.customCommandTemplateText,
                    onValueChange = { viewModel.customCommandTemplateText = it },
                    label = { Text("Comando personalizado (un argumento por línea; usa {path} y {command})") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        SectionCard(title = "Agente") {
            OutlinedTextField(
                value = viewModel.agentCommand,
                onValueChange = { viewModel.agentCommand = it },
                label = { Text("Comando del agente") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = viewModel.useHeadroom, onCheckedChange = { viewModel.useHeadroom = it })
                Text("Usar Headroom (headroom wrap)", modifier = Modifier.padding(start = Spacing.sm))
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.padding(end = Spacing.sm)) {
                Text("Cancelar")
            }
            Button(
                onClick = { onSaved(viewModel.save()) },
                enabled = viewModel.isValid,
            ) {
                Text("Guardar")
            }
        }
    }
}
