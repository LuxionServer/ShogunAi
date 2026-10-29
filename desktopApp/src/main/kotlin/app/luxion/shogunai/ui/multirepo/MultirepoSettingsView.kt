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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.MultirepoRepository
import app.luxion.shogunai.domain.model.RepositoryConfigIssue
import app.luxion.shogunai.ui.components.ActionSlot
import app.luxion.shogunai.ui.components.ColumnSpec
import app.luxion.shogunai.ui.components.DataCell
import app.luxion.shogunai.ui.components.DataHeader
import app.luxion.shogunai.ui.components.DataRow
import app.luxion.shogunai.ui.components.Field
import app.luxion.shogunai.ui.components.GhostButton
import app.luxion.shogunai.ui.components.RowLines
import app.luxion.shogunai.ui.components.PathField
import app.luxion.shogunai.ui.components.ShogunDialog
import app.luxion.shogunai.ui.FilePicker
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import app.luxion.shogunai.ui.components.DangerButton
import app.luxion.shogunai.ui.components.DropdownSelector
import app.luxion.shogunai.ui.components.IconAction
import app.luxion.shogunai.ui.components.DangerTextButton
import app.luxion.shogunai.ui.components.ErrorNotice
import app.luxion.shogunai.ui.components.InlineProgress
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.Notice
import app.luxion.shogunai.ui.components.NoticeKind
import app.luxion.shogunai.ui.components.PrimaryButton
import app.luxion.shogunai.ui.components.SecondaryButton
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.ViewTitle
import app.luxion.shogunai.ui.components.handPointer
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.repositoryConfigIssue
import app.luxion.shogunai.ui.projectconfig.AgentFields
import app.luxion.shogunai.ui.projectconfig.SettingsField
import app.luxion.shogunai.ui.projectconfig.SettingsSection
import app.luxion.shogunai.ui.theme.ShogunTheme

private val ContentMaxWidth = 760.dp
private val AliasFieldWidth = 160.dp
private val NAME_ISSUES = setOf(
    RepositoryConfigIssue.InvalidVariableName,
    RepositoryConfigIssue.ReservedVariableName,
    RepositoryConfigIssue.DuplicateVariableName,
)

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
                ViewTitle(strings.multirepoSettingsTitle)
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
        if (state.needsReview) {
            Notice(
                kind = NoticeKind.WARNING,
                message = strings.multirepoNeedsReviewExplanation,
                onDismiss = null,
                modifier = Modifier.padding(horizontal = Spacing.md).testTag("multirepo-settings-needs-review"),
            )
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
                Field(
                    value = state.name,
                    onValueChange = { state.name = it },
                    placeholder = multirepo.folderName,
                    modifier = Modifier.fillMaxWidth(),
                    inputModifier = Modifier.testTag("multirepo-name"),
                )
            }
            SettingsSection(strings.multirepoRepositoriesTitle) {
                if (multirepo.declaredRepositories.isNotEmpty()) {
                    DataHeader(
                        listOf(
                            RepositoryAliasColumn to strings.multirepoAliasLabel,
                            RepositoryPathColumn to strings.repositoryPathLabel,
                            RepositoryRenameColumn to "",
                        ),
                        actionSlotWidth = RepositoryActionWidth,
                    )
                }
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
            SharedVariablesSection(state)
            SettingsSection(strings.multirepoWorktreesRootTitle) {
                Text(strings.multirepoWorktreesRootHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PathField(
                    value = state.worktreesRoot,
                    onValueChange = { state.worktreesRoot = it },
                    onBrowse = { pickFolder(state.worktreesRoot, strings.multirepoWorktreesRootTitle)?.let { state.worktreesRoot = it } },
                    textStyle = ShogunTheme.typography.data,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SettingsSection(strings.multirepoAgentTitle) {
                Text(strings.multirepoAgentHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AgentFields(agent = state.agent, duplicated = false, commandFocus = null, onChange = { state.agent = it })
                if (multirepo.ignoresDeclaredAgent) {
                    Text(strings.declaredAgentIgnored, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
            SettingsSection(strings.deleteMultirepo) {
                Text(strings.deleteMultirepoHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DangerTextButton(onClick = state::requestDelete) { Text(strings.deleteMultirepo) }
            }
        }
    }
    if (state.isConfirmingDelete && multirepo != null) {
        ShogunDialog(
            title = strings.deleteMultirepoTitle(multirepo.name),
            onDismissRequest = state::cancelDelete,
            confirm = { DangerButton(onClick = { state.confirmDelete(onDeleted) }) { Text(strings.delete) } },
            dismiss = { NeutralTextButton(onClick = state::cancelDelete) { Text(strings.cancel) } },
        ) {
            Text(strings.deleteMultirepoMessage)
        }
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
            Field(
                value = state.workspaceAlias,
                onValueChange = { state.workspaceAlias = it.trim().lowercase() },
                label = strings.workspaceAliasLabel,
                textStyle = ShogunTheme.typography.data,
                modifier = Modifier.width(AliasFieldWidth),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            if (state.canReturnWorkspaceToDetection) {
                NeutralTextButton(onClick = state::returnWorkspaceToDetection, enabled = !state.isBusy) { Text(strings.returnWorkspaceToDetection) }
            }
            if (!state.workspaceOff) {
                DangerTextButton(onClick = state::turnOffWorkspace, enabled = !state.isBusy) { Text(strings.turnOffWorkspace) }
            }
        }
    }
}

@Composable
private fun SharedVariablesSection(state: MultirepoSettingsState) {
    val strings = LocalStrings.current
    SettingsSection(strings.sharedVariablesTitle) {
        Text(strings.sharedVariablesHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val problem = state.sharedVariablesProblem
        if (problem != null) {
            Notice(
                kind = NoticeKind.WARNING,
                message = strings.sharedVariablesUnrepresentable(problem),
                onDismiss = null,
                modifier = Modifier.testTag("shared-variables-unrepresentable"),
            )
        } else {
            if (state.sharedVariables.isEmpty()) {
                Text(strings.sharedVariablesEmpty, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.sharedVariables.forEach { draft -> SharedVariableRow(state, draft) }
            SecondaryButton(
                onClick = state::addSharedVariable,
                enabled = state.aliases.isNotEmpty() && !state.needsReview,
                modifier = Modifier.testTag("add-shared-variable"),
            ) { Text(strings.addVariable) }
        }
    }
}

@Composable
private fun SharedVariableRow(state: MultirepoSettingsState, draft: SharedVariableDraft) {
    val strings = LocalStrings.current
    val issue = state.sharedVariableIssue(draft)
    val nameIssue = issue?.takeIf { it in NAME_ISSUES }
    val templateIssue = issue?.takeUnless { it in NAME_ISSUES }
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        DropdownSelector(
            options = state.aliases,
            selected = draft.alias,
            onSelect = { state.updateSharedVariable(draft.copy(alias = it)) },
            label = { it },
            placeholder = strings.multirepoAliasLabel,
            modifier = Modifier.width(AliasFieldWidth),
        )
        Field(
            value = draft.name,
            onValueChange = { state.updateSharedVariable(draft.copy(name = it.trim())) },
            label = strings.environmentVariableNameLabel,
            error = nameIssue?.let { strings.repositoryConfigIssue(it) },
            textStyle = ShogunTheme.typography.data,
            modifier = Modifier.weight(1f),
            inputModifier = Modifier.testTag("shared-variable-name-${draft.id}"),
        )
        Field(
            value = draft.template,
            onValueChange = { state.updateSharedVariable(draft.copy(template = it)) },
            label = strings.environmentVariableTemplateLabel,
            error = templateIssue?.let { strings.repositoryConfigIssue(it) },
            textStyle = ShogunTheme.typography.data,
            modifier = Modifier.weight(2f),
            inputModifier = Modifier.testTag("shared-variable-template-${draft.id}"),
        )
        IconAction(
            icon = Icons.Default.Close,
            label = strings.removeVariable,
            onClick = { state.removeSharedVariable(draft.id) },
            tint = ShogunTheme.status.danger.color,
        )
    }
}

@Composable
private fun RepositoryRow(state: MultirepoSettingsState, repository: MultirepoRepository) {
    val strings = LocalStrings.current
    var alias by remember(repository.alias) { mutableStateOf(repository.alias) }
    DataRow(
        cells = listOf(
            DataCell(RepositoryAliasColumn) {
                Field(
                    value = alias,
                    onValueChange = { alias = it.trim().lowercase() },
                    placeholder = strings.multirepoAliasLabel,
                    textStyle = ShogunTheme.typography.data,
                    inputModifier = Modifier.testTag("repository-alias-${repository.alias}"),
                )
            },
            DataCell(RepositoryPathColumn) {
                Text(repository.path, style = ShogunTheme.typography.dataSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            DataCell(RepositoryRenameColumn) {
                if (alias != repository.alias) {
                    GhostButton(onClick = { state.renameRepository(repository.alias, alias) }, enabled = !state.isBusy) {
                        Text(strings.rename)
                    }
                }
            },
        ),
        lines = RowLines.ONE,
        actions = ActionSlot(width = RepositoryActionWidth, alwaysVisible = true) {
            IconAction(
                icon = Icons.Default.Delete,
                label = strings.remove,
                onClick = { state.removeRepository(repository.alias) },
                enabled = !state.isBusy,
                tint = ShogunTheme.status.danger.color,
            )
        },
    )
}

private val RepositoryAliasColumn = ColumnSpec(width = AliasFieldWidth)
private val RepositoryPathColumn = ColumnSpec()
private val RepositoryRenameColumn = ColumnSpec(width = 88.dp)
private val RepositoryActionWidth = 40.dp

@Composable
private fun AddRepositoryRow(state: MultirepoSettingsState, pickFolder: (String, String) -> String?) {
    val strings = LocalStrings.current
    SettingsField(strings.addRepository) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Field(
                value = state.newAlias,
                onValueChange = { state.newAlias = it.trim().lowercase() },
                label = strings.multirepoAliasLabel,
                textStyle = ShogunTheme.typography.data,
                modifier = Modifier.width(AliasFieldWidth),
            )
            PathField(
                value = state.newPath,
                onValueChange = { state.newPath = it },
                onBrowse = { pickFolder(state.newPath, strings.addRepository)?.let(state::chooseNewPath) },
                label = strings.repositoryPathLabel,
                textStyle = ShogunTheme.typography.data,
                modifier = Modifier.weight(1f),
            )
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
