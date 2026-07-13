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
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import app.luxion.shogunai.ui.relativeToBase

@Composable
fun ProjectConfigScreen(
    viewModel: ProjectConfigViewModel,
    onSaved: (Project) -> Unit,
    onCancel: () -> Unit,
) {
    var newSecretFile by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()),
    ) {
        Text("Configuración de proyecto", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = viewModel.name,
            onValueChange = { viewModel.name = it },
            label = { Text("Nombre") },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = viewModel.baseRepositoryPath,
                onValueChange = { viewModel.baseRepositoryPath = it },
                label = { Text("Ruta del repositorio base") },
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = {
                    FilePicker.pickDirectory(viewModel.baseRepositoryPath)?.let {
                        viewModel.baseRepositoryPath = it
                    }
                },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text("Examinar…")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = viewModel.worktreesRoot,
                onValueChange = { viewModel.worktreesRoot = it },
                label = { Text("Carpeta raíz de worktrees") },
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = {
                    FilePicker.pickDirectory(viewModel.worktreesRoot)?.let {
                        viewModel.worktreesRoot = it
                    }
                },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text("Examinar…")
            }
        }

        Text("Archivos de secretos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        LazyColumn(modifier = Modifier.heightIn(max = 160.dp).padding(top = 8.dp)) {
            items(viewModel.secretFiles) { file ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(file)
                    IconButton(onClick = { viewModel.removeSecretFile(file) }) {
                        Text("✕")
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newSecretFile,
                onValueChange = { newSecretFile = it },
                label = { Text("Nuevo archivo de secretos") },
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    viewModel.addSecretFile(newSecretFile)
                    newSecretFile = ""
                },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text("Añadir")
            }
            OutlinedButton(
                onClick = {
                    FilePicker.pickFiles(viewModel.baseRepositoryPath).forEach { absolutePath ->
                        viewModel.addSecretFile(relativeToBase(viewModel.baseRepositoryPath, absolutePath))
                    }
                },
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text("Examinar…")
            }
        }

        Text("Terminal", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
        Column(modifier = Modifier.padding(top = 8.dp)) {
            TerminalSelectionMode.entries.forEach { mode ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = viewModel.terminalSelectionMode == mode,
                        onClick = { viewModel.terminalSelectionMode = mode },
                    )
                    Text(mode.name, modifier = Modifier.padding(end = 16.dp))
                }
            }
            if (viewModel.terminalSelectionMode == TerminalSelectionMode.FIXED) {
                Column(modifier = Modifier.padding(start = 16.dp, top = 4.dp)) {
                    TerminalEmulator.entries.forEach { emulator ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = viewModel.terminalEmulator == emulator,
                                onClick = { viewModel.terminalEmulator = emulator },
                            )
                            Text(emulator.name, modifier = Modifier.padding(end = 16.dp))
                        }
                    }
                }
            }
            if (viewModel.terminalSelectionMode == TerminalSelectionMode.CUSTOM) {
                OutlinedTextField(
                    value = viewModel.customCommandTemplateText,
                    onValueChange = { viewModel.customCommandTemplateText = it },
                    label = { Text("Comando personalizado (un argumento por línea; usa {path} y {command})") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }

        Text("Agente", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            OutlinedTextField(
                value = viewModel.agentCommand,
                onValueChange = { viewModel.agentCommand = it },
                label = { Text("Comando del agente") },
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(checked = viewModel.useHeadroom, onCheckedChange = { viewModel.useHeadroom = it })
            Text("Usar Headroom (headroom wrap)", modifier = Modifier.padding(start = 8.dp))
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.padding(end = 8.dp)) {
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
