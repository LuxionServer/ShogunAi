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
import androidx.compose.material3.AlertDialog
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
import app.luxion.shogunai.ui.FilePicker
import app.luxion.shogunai.ui.components.SectionCard
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.onEnterKey
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.relativeToBase

@Composable
fun ProjectConfigScreen(
    viewModel: ProjectConfigViewModel,
    onSaved: (Project) -> Unit,
    onCancel: () -> Unit,
) {
    val strings = LocalStrings.current
    var newSecretFile by remember { mutableStateOf("") }
    var showDiscardChangesDialog by remember { mutableStateOf(false) }
    val requestCancel = {
        if (viewModel.hasUnsavedChanges) showDiscardChangesDialog = true else onCancel()
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(Spacing.md).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(strings.projectConfigTitle, style = MaterialTheme.typography.headlineSmall)

        SectionCard(title = strings.identitySection) {
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                label = { Text(strings.nameLabel) },
                isError = viewModel.name.isBlank(),
                supportingText = if (viewModel.name.isBlank()) {
                    { Text(strings.nameEmptyError) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionCard(title = strings.pathsSection) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val pickBaseRepositoryPath = {
                    FilePicker.pickDirectory(viewModel.baseRepositoryPath, strings.selectFolderDialogTitle)?.let {
                        viewModel.baseRepositoryPath = it
                    }
                    Unit
                }
                OutlinedTextField(
                    value = viewModel.baseRepositoryPath,
                    onValueChange = { viewModel.baseRepositoryPath = it },
                    label = { Text(strings.baseRepositoryPathLabel) },
                    isError = viewModel.baseRepositoryPath.isBlank(),
                    supportingText = if (viewModel.baseRepositoryPath.isBlank()) {
                        { Text(strings.baseRepositoryPathEmptyError) }
                    } else {
                        null
                    },
                    modifier = Modifier.weight(1f).onEnterKey(action = pickBaseRepositoryPath),
                )
                OutlinedButton(
                    onClick = pickBaseRepositoryPath,
                    modifier = Modifier.padding(start = Spacing.sm),
                ) {
                    Text(strings.browse)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val pickWorktreesRoot = {
                    FilePicker.pickDirectory(viewModel.worktreesRoot, strings.selectFolderDialogTitle)?.let {
                        viewModel.worktreesRoot = it
                    }
                    Unit
                }
                OutlinedTextField(
                    value = viewModel.worktreesRoot,
                    onValueChange = { viewModel.worktreesRoot = it },
                    label = { Text(strings.worktreesRootLabel) },
                    modifier = Modifier.weight(1f).onEnterKey(action = pickWorktreesRoot),
                )
                OutlinedButton(
                    onClick = pickWorktreesRoot,
                    modifier = Modifier.padding(start = Spacing.sm),
                ) {
                    Text(strings.browse)
                }
            }
            OutlinedTextField(
                value = viewModel.mainBranch,
                onValueChange = { viewModel.mainBranch = it },
                label = { Text(strings.mainBranchLabel) },
                placeholder = { Text(strings.mainBranchPlaceholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionCard(title = strings.secretFilesSection) {
            LazyColumn(modifier = Modifier.heightIn(max = 160.dp)) {
                items(viewModel.secretFiles) { file ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(file)
                        IconButton(onClick = { viewModel.removeSecretFile(file) }) {
                            Icon(Icons.Default.Close, contentDescription = strings.removeSecretFile)
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
                    label = { Text(strings.newSecretFileLabel) },
                    modifier = Modifier.weight(1f).onEnterKey(action = addSecretFile),
                )
                Button(
                    onClick = addSecretFile,
                    modifier = Modifier.padding(start = Spacing.sm),
                ) {
                    Text(strings.add)
                }
                OutlinedButton(
                    onClick = {
                        FilePicker.pickFiles(viewModel.baseRepositoryPath, strings.selectSecretFilesDialogTitle).forEach { absolutePath ->
                            viewModel.addSecretFile(relativeToBase(viewModel.baseRepositoryPath, absolutePath))
                        }
                    },
                    modifier = Modifier.padding(start = Spacing.sm),
                ) {
                    Text(strings.browse)
                }
            }
        }

        SectionCard(title = strings.agentSection) {
            OutlinedTextField(
                value = viewModel.agentCommand,
                onValueChange = { viewModel.agentCommand = it },
                label = { Text(strings.agentCommandLabel) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = viewModel.useHeadroom, onCheckedChange = { viewModel.useHeadroom = it })
                Text(strings.useHeadroomLabel, modifier = Modifier.padding(start = Spacing.sm))
            }
            Text(
                strings.headroomDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = requestCancel, modifier = Modifier.padding(end = Spacing.sm)) {
                Text(strings.cancel)
            }
            Button(
                onClick = { onSaved(viewModel.save()) },
                enabled = viewModel.isValid,
            ) {
                Text(strings.save)
            }
        }
    }

    if (showDiscardChangesDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardChangesDialog = false },
            title = { Text(strings.discardChangesTitle) },
            text = { Text(strings.discardChangesMessage) },
            confirmButton = {
                Button(onClick = {
                    showDiscardChangesDialog = false
                    onCancel()
                }) {
                    Text(strings.discard)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDiscardChangesDialog = false }) {
                    Text(strings.keepEditing)
                }
            },
        )
    }
}
