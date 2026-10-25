package app.luxion.shogunai.ui.multirepo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.MultirepoRepository
import app.luxion.shogunai.ui.FilePicker
import app.luxion.shogunai.ui.components.DangerButton
import app.luxion.shogunai.ui.components.DangerTextButton
import app.luxion.shogunai.ui.components.ErrorNotice
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.PrimaryButton
import app.luxion.shogunai.ui.components.SecondaryButton
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.handPointer
import app.luxion.shogunai.ui.components.trackTextInputFocus
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.projectconfig.AgentFields
import app.luxion.shogunai.ui.projectconfig.SettingsField
import app.luxion.shogunai.ui.projectconfig.SettingsSection
import app.luxion.shogunai.ui.theme.ShogunTheme

private val ContentMaxWidth = 760.dp
private val AliasFieldWidth = 160.dp

@Composable
fun MultirepoSettingsView(
    state: MultirepoSettingsState,
    onClose: () -> Unit,
    onDeleted: () -> Unit,
    pickFolder: (String, String) -> String? = FilePicker::pickDirectory,
) {
    val strings = LocalStrings.current
    val multirepo = state.multirepo
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(strings.multirepoSettingsTitle, style = MaterialTheme.typography.headlineSmall)
                multirepo?.let {
                    Text(it.folder, style = ShogunTheme.typography.dataSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.isBusy) InlineProgress()
            NeutralTextButton(onClick = onClose) { Text(strings.close) }
            PrimaryButton(onClick = state::save, enabled = state.canSave) { Text(strings.save) }
        }
        state.error?.let { ErrorNotice(error = it, onDismiss = state::dismissError, modifier = Modifier.padding(horizontal = Spacing.md)) }
        if (multirepo == null) {
            Text(strings.multirepoNotFound, modifier = Modifier.padding(Spacing.md))
            return@Column
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md)
                .widthIn(max = ContentMaxWidth),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            SettingsSection(strings.multirepoNameTitle) {
                Text(strings.multirepoNameHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { state.name = it },
                    placeholder = { Text(multirepo.folderName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("multirepo-name").trackTextInputFocus(),
                )
            }
            SettingsSection(strings.multirepoRepositoriesTitle) {
                multirepo.declaredRepositories.forEach { repository -> RepositoryRow(state, repository) }
                AddRepositoryRow(state, pickFolder)
            }
            if (state.hasWorkspaceSection) WorkspaceSection(state)
            SettingsSection(strings.defaultRepositoriesTitle) {
                Text(strings.defaultRepositoriesHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.aliases.forEach { alias ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = alias in state.defaults,
                            onCheckedChange = { state.toggleDefault(alias) },
                            modifier = Modifier.handPointer(),
                        )
                        Text(alias, style = ShogunTheme.typography.data)
                    }
                }
            }
            SettingsSection(strings.multirepoWorktreesRootTitle) {
                Text(strings.multirepoWorktreesRootHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = state.worktreesRoot,
                        onValueChange = { state.worktreesRoot = it },
                        textStyle = ShogunTheme.typography.data,
                        singleLine = true,
                        modifier = Modifier.weight(1f).trackTextInputFocus(),
                    )
                    SecondaryButton(onClick = { pickFolder(state.worktreesRoot, strings.multirepoWorktreesRootTitle)?.let { state.worktreesRoot = it } }) {
                        Text(strings.browse)
                    }
                }
            }
            SettingsSection(strings.multirepoAgentTitle) {
                Text(strings.multirepoAgentHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AgentFields(agent = state.agent, duplicated = false, commandFocus = null, onChange = { state.agent = it })
            }
            HorizontalDivider()
            SettingsSection(strings.deleteMultirepo) {
                Text(strings.deleteMultirepoHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DangerButton(onClick = state::requestDelete) { Text(strings.deleteMultirepo) }
            }
        }
    }
    if (state.isConfirmingDelete && multirepo != null) {
        AlertDialog(
            onDismissRequest = state::cancelDelete,
            title = { Text(strings.deleteMultirepoTitle(multirepo.name)) },
            text = { Text(strings.deleteMultirepoMessage) },
            confirmButton = { DangerButton(onClick = { state.confirmDelete(onDeleted) }) { Text(strings.delete) } },
            dismissButton = { NeutralTextButton(onClick = state::cancelDelete) { Text(strings.cancel) } },
        )
    }
}

@Composable
private fun WorkspaceSection(state: MultirepoSettingsState) {
    val strings = LocalStrings.current
    SettingsSection(strings.workspaceRepositoryTitle) {
        Text(strings.workspaceRepositoryHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.workspaceOff) {
            Text(strings.workspaceTurnedOff, style = MaterialTheme.typography.bodySmall)
        } else if (state.workspaceDetected) {
            Text(strings.workspaceDetectedMark, style = ShogunTheme.typography.dataSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = state.workspaceDeclared,
                onCheckedChange = { state.workspaceDeclared = it },
                enabled = state.multirepo?.workspaceDeclared != true,
                modifier = Modifier.handPointer(),
            )
            Text(strings.declareWorkspace, style = MaterialTheme.typography.bodyMedium)
        }
        if (state.workspaceDeclared) {
            OutlinedTextField(
                value = state.workspaceAlias,
                onValueChange = { state.workspaceAlias = it.trim().lowercase() },
                textStyle = ShogunTheme.typography.data,
                singleLine = true,
                label = { Text(strings.workspaceAliasLabel) },
                modifier = Modifier.width(AliasFieldWidth).trackTextInputFocus(),
            )
        }
        if (!state.workspaceOff) {
            DangerTextButton(onClick = state::turnOffWorkspace, enabled = !state.isBusy) { Text(strings.turnOffWorkspace) }
        }
    }
}

@Composable
private fun RepositoryRow(state: MultirepoSettingsState, repository: MultirepoRepository) {
    val strings = LocalStrings.current
    var alias by remember(repository.alias) { mutableStateOf(repository.alias) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedTextField(
            value = alias,
            onValueChange = { alias = it.trim().lowercase() },
            textStyle = ShogunTheme.typography.data,
            singleLine = true,
            label = { Text(strings.multirepoAliasLabel) },
            modifier = Modifier.width(AliasFieldWidth).trackTextInputFocus(),
        )
        Text(
            repository.path,
            style = ShogunTheme.typography.dataSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (alias != repository.alias) {
            NeutralTextButton(onClick = { state.renameRepository(repository.alias, alias) }, enabled = !state.isBusy) { Text(strings.rename) }
        }
        DangerTextButton(onClick = { state.removeRepository(repository.alias) }, enabled = !state.isBusy) { Text(strings.remove) }
    }
}

@Composable
private fun AddRepositoryRow(state: MultirepoSettingsState, pickFolder: (String, String) -> String?) {
    val strings = LocalStrings.current
    SettingsField(strings.addRepository) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedTextField(
                value = state.newAlias,
                onValueChange = { state.newAlias = it.trim().lowercase() },
                textStyle = ShogunTheme.typography.data,
                singleLine = true,
                label = { Text(strings.multirepoAliasLabel) },
                modifier = Modifier.width(AliasFieldWidth).trackTextInputFocus(),
            )
            OutlinedTextField(
                value = state.newPath,
                onValueChange = { state.newPath = it },
                textStyle = ShogunTheme.typography.data,
                singleLine = true,
                label = { Text(strings.repositoryPathLabel) },
                modifier = Modifier.weight(1f).trackTextInputFocus(),
            )
            SecondaryButton(onClick = { pickFolder(state.newPath, strings.addRepository)?.let(state::chooseNewPath) }) { Text(strings.browse) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = state.newUsesProposedFolder,
                onCheckedChange = { state.newUsesProposedFolder = it },
                modifier = Modifier.handPointer(),
            )
            Text(
                strings.useWorktreesFolder(state.multirepo?.proposedWorktreesFolder(state.newAlias.ifBlank { "…" }).orEmpty()),
                style = ShogunTheme.typography.dataSmall,
            )
        }
        PrimaryButton(onClick = state::addRepository, enabled = state.canAddRepository) { Text(strings.addRepository) }
    }
}
