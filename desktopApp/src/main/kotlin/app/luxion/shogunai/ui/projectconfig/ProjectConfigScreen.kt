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
import androidx.compose.material3.MaterialTheme
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
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.ui.FilePicker
import app.luxion.shogunai.ui.components.IconAction
import app.luxion.shogunai.ui.components.Notice
import app.luxion.shogunai.ui.components.NoticeKind
import app.luxion.shogunai.ui.components.PrimaryButton
import app.luxion.shogunai.ui.components.SecondaryButton
import app.luxion.shogunai.ui.components.SectionCard
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.handPointer
import app.luxion.shogunai.ui.components.onEnterKey
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.worktreeError
import app.luxion.shogunai.ui.relativeToBase
import app.luxion.shogunai.ui.theme.ShogunTheme

@Composable
fun ProjectConfigScreen(
    viewModel: ProjectConfigViewModel,
    onSaved: (Project) -> Unit,
    onCancel: () -> Unit,
) {
    val strings = LocalStrings.current
    var newSecretFile by remember { mutableStateOf("") }
    var showDiscardChangesDialog by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
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
                singleLine = true,
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
                    textStyle = ShogunTheme.typography.data,
                    singleLine = true,
                    isError = viewModel.baseRepositoryPath.isBlank(),
                    supportingText = if (viewModel.baseRepositoryPath.isBlank()) {
                        { Text(strings.baseRepositoryPathEmptyError) }
                    } else {
                        null
                    },
                    modifier = Modifier.weight(1f).onEnterKey(action = pickBaseRepositoryPath),
                )
                SecondaryButton(
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
                    textStyle = ShogunTheme.typography.data,
                    singleLine = true,
                    modifier = Modifier.weight(1f).onEnterKey(action = pickWorktreesRoot),
                )
                SecondaryButton(
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
                textStyle = ShogunTheme.typography.data,
                placeholder = { Text(strings.mainBranchPlaceholder) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionCard(title = strings.repositoryProvisioningSection) {
            Text(
                strings.repositoryProvisioningDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RepositoryProvisioningList(
                state = viewModel.repositoryProvisioning,
                pendingPatterns = viewModel.pendingRepositoryPatterns,
            )
        }

        SectionCard(title = strings.localFilesSection) {
            Text(
                strings.localFilesDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(modifier = Modifier.heightIn(max = LocalFilesListMaxHeight)) {
                items(viewModel.secretFiles) { file ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(file, style = ShogunTheme.typography.data)
                        IconAction(
                            icon = Icons.Default.Close,
                            label = strings.removeLocalFile,
                            onClick = { viewModel.removeSecretFile(file) },
                            tint = ShogunTheme.status.danger.color,
                        )
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
                    label = { Text(strings.newLocalFileLabel) },
                    textStyle = ShogunTheme.typography.data,
                    singleLine = true,
                    modifier = Modifier.weight(1f).onEnterKey(action = addSecretFile),
                )
                SecondaryButton(
                    onClick = addSecretFile,
                    modifier = Modifier.padding(start = Spacing.sm),
                ) {
                    Text(strings.add)
                }
                SecondaryButton(
                    onClick = {
                        FilePicker.pickFiles(viewModel.baseRepositoryPath, strings.selectLocalFilesDialogTitle).forEach { absolutePath ->
                            viewModel.addSecretFile(relativeToBase(viewModel.baseRepositoryPath, absolutePath))
                        }
                    },
                    modifier = Modifier.padding(start = Spacing.sm),
                ) {
                    Text(strings.browse)
                }
            }
            if (viewModel.canMoveLocalFilesToRepository) {
                SecondaryButton(onClick = viewModel::moveLocalFilesToRepository) {
                    Text(strings.moveToRepositoryConfig)
                }
            }
        }

        SectionCard(title = strings.agentSection) {
            OutlinedTextField(
                value = viewModel.agentCommand,
                onValueChange = { viewModel.agentCommand = it },
                label = { Text(strings.agentCommandLabel) },
                textStyle = ShogunTheme.typography.data,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = viewModel.useHeadroom, onCheckedChange = { viewModel.useHeadroom = it }, modifier = Modifier.handPointer())
                Text(strings.useHeadroomLabel, modifier = Modifier.padding(start = Spacing.sm))
            }
            Text(
                strings.headroomDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        EmulatorSettingsSection(viewModel)
        SimulatorSettingsSection(viewModel)

        saveError?.let { error ->
            Notice(kind = NoticeKind.ERROR, message = error, onDismiss = { saveError = null })
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            SecondaryButton(onClick = requestCancel, modifier = Modifier.padding(end = Spacing.sm)) {
                Text(strings.cancel)
            }
            PrimaryButton(
                onClick = {
                    viewModel.save()
                        .onSuccess { saveError = null; onSaved(it) }
                        .onFailure { saveError = strings.saveFailed(if (it is WorktreeError) strings.worktreeError(it) else it.message) }
                },
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
                PrimaryButton(onClick = {
                    showDiscardChangesDialog = false
                    onCancel()
                }) {
                    Text(strings.discard)
                }
            },
            dismissButton = {
                SecondaryButton(onClick = { showDiscardChangesDialog = false }) {
                    Text(strings.keepEditing)
                }
            },
        )
    }
}

@Composable
private fun RepositoryProvisioningList(
    state: RepositoryProvisioningState,
    pendingPatterns: List<String>,
) {
    val strings = LocalStrings.current
    val declared = (state as? RepositoryProvisioningState.Declared)?.provisioning
    val rows = declared?.copyPatterns.orEmpty().map { it to RepositoryProvisioning.CONFIG_FILE_NAME } +
        declared?.worktreeIncludePatterns.orEmpty().map { it to RepositoryProvisioning.WORKTREE_INCLUDE_FILE_NAME } +
        pendingPatterns.map { it to "${RepositoryProvisioning.CONFIG_FILE_NAME} · ${strings.pendingSave}" }

    when {
        state is RepositoryProvisioningState.Unreadable -> Text(
            strings.repositoryConfigUnreadable(state.path) + (state.detail?.let { "\n$it" } ?: ""),
            color = ShogunTheme.status.danger.color,
        )
        rows.isEmpty() -> Text(strings.repositoryProvisioningEmpty)
    }
    rows.forEach { (pattern, source) ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(pattern, style = ShogunTheme.typography.data)
            Text(
                source,
                style = ShogunTheme.typography.dataSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val LocalFilesListMaxHeight = 160.dp
