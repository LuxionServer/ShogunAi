package app.luxion.shogunai.ui.projectconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.SuggestedProvider
import app.luxion.shogunai.domain.model.ShellQuoting
import app.luxion.shogunai.ui.components.IconAction
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.SecondaryButton
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.handPointer
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.i18n.repositoryConfigIssue
import app.luxion.shogunai.ui.theme.ShogunTheme

private val OptionIndent = 48.dp

@Composable
fun RepositoryConfigSection(viewModel: ProjectConfigViewModel, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    SettingsSection(strings.repositoryConfigSection, modifier) {
        Text(
            strings.repositoryConfigSharedNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val unreadable = viewModel.repositoryProvisioning as? RepositoryProvisioningState.Unreadable
        when {
            unreadable != null -> UnreadableRepositoryConfig(unreadable, viewModel)
            !viewModel.repositoryConfigEditable -> Unit
            else -> {
                if (viewModel.repositoryChangedOnDisk) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(
                            strings.repositoryConfigChanged(RepositoryProvisioning.CONFIG_FILE_NAME),
                            color = ShogunTheme.status.danger.color,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(onClick = viewModel::reloadRepositoryConfig) { Text(strings.reloadRepositoryConfig) }
                    }
                }
                ProvidersEditor(viewModel, strings)
                EnvironmentEditor(viewModel, strings)
                if (viewModel.postCreateCommands.isNotEmpty()) PostCreateCommandsView(viewModel, strings)
            }
        }
    }
}

@Composable
private fun UnreadableRepositoryConfig(state: RepositoryProvisioningState.Unreadable, viewModel: ProjectConfigViewModel) {
    val strings = LocalStrings.current
    Text(
        strings.repositoryConfigUnreadable(state.path) + (state.detail?.let { "\n$it" } ?: ""),
        color = ShogunTheme.status.danger.color,
    )
    Text(
        strings.repositoryConfigUnreadableExplanation,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SecondaryButton(onClick = viewModel::reloadRepositoryConfig) { Text(strings.reloadRepositoryConfig) }
}

@Composable
private fun ProvidersEditor(viewModel: ProjectConfigViewModel, strings: Strings) {
    val draft = viewModel.repositoryDraft
    SettingsField(strings.repositoryConfigProvidersTitle) {
        StackSuggestions(viewModel, strings)
        ProviderSwitch(strings.providerGradle, draft.gradle != null, viewModel::setGradleEnabled)
        draft.gradle?.let { gradle ->
            OptionField(
                label = strings.gradleDaemonMaxHeapLabel,
                value = gradle.daemonMaxHeap,
                placeholder = "2g",
                issue = draft.issueFor(RepositoryConfigField.GradleDaemonMaxHeap),
                onChange = { value -> viewModel.updateRepositoryDraft { it.copy(gradle = gradle.copy(daemonMaxHeap = value)) } },
            )
            OptionField(
                label = strings.gradleIdleTimeoutLabel,
                value = gradle.idleTimeoutMinutes,
                placeholder = "30",
                issue = draft.issueFor(RepositoryConfigField.GradleIdleTimeout),
                onChange = { value -> viewModel.updateRepositoryDraft { it.copy(gradle = gradle.copy(idleTimeoutMinutes = value)) } },
            )
            OptionField(
                label = strings.gradleKotlinDaemonMaxHeapLabel,
                value = gradle.kotlinDaemonMaxHeap,
                placeholder = "1g",
                issue = draft.issueFor(RepositoryConfigField.GradleKotlinDaemonMaxHeap),
                onChange = { value -> viewModel.updateRepositoryDraft { it.copy(gradle = gradle.copy(kotlinDaemonMaxHeap = value)) } },
            )
        }
        ProviderSwitch(strings.providerEmulator, draft.emulator != null, viewModel::setEmulatorEnabled)
        draft.emulator?.let { emulator ->
            OptionField(
                label = strings.preferredAvdLabel,
                value = emulator.preferredAvd,
                placeholder = "Pixel_8",
                issue = null,
                onChange = { value -> viewModel.updateRepositoryDraft { it.copy(emulator = emulator.copy(preferredAvd = value)) } },
            )
        }
        ProviderSwitch(strings.providerMaven, draft.maven != null, viewModel::setMavenEnabled)
        draft.maven?.let { maven ->
            OptionField(
                label = strings.mavenMaxHeapLabel,
                value = maven.mavenMaxHeap,
                placeholder = "1g",
                issue = draft.issueFor(RepositoryConfigField.MavenMaxHeap),
                onChange = { value -> viewModel.updateRepositoryDraft { it.copy(maven = maven.copy(mavenMaxHeap = value)) } },
            )
        }
        ProviderSwitch(strings.providerDockerCompose, draft.dockerCompose != null, viewModel::setDockerComposeEnabled)
        draft.dockerCompose?.let { compose ->
            val issue = draft.issueFor(RepositoryConfigField.ComposePortVariables)
            OutlinedTextField(
                value = compose.portVariables,
                onValueChange = { value -> viewModel.updateRepositoryDraft { it.copy(dockerCompose = compose.copy(portVariables = value)) } },
                label = { Text(strings.composePortVariablesLabel) },
                placeholder = { Text("DB_PORT, API_PORT") },
                textStyle = ShogunTheme.typography.data,
                singleLine = true,
                isError = issue != null,
                supportingText = { Text(issue?.let(strings::repositoryConfigIssue) ?: strings.composePortVariablesHelp) },
                modifier = Modifier.fillMaxWidth().padding(start = OptionIndent),
            )
        }
        if (viewModel.showsApplePlatformProviders) {
            ProviderSwitch(strings.providerIosSimulator, draft.iosSimulator != null, viewModel::setIosSimulatorEnabled)
            draft.iosSimulator?.let { simulator ->
                OptionField(
                    label = strings.preferredDeviceLabel,
                    value = simulator.device,
                    placeholder = "iPhone 16",
                    issue = null,
                    onChange = { value -> viewModel.updateRepositoryDraft { it.copy(iosSimulator = simulator.copy(device = value)) } },
                )
                OptionField(
                    label = strings.preferredRuntimeLabel,
                    value = simulator.runtime,
                    placeholder = "iOS 18.0",
                    issue = null,
                    onChange = { value -> viewModel.updateRepositoryDraft { it.copy(iosSimulator = simulator.copy(runtime = value)) } },
                )
            }
            ProviderSwitch(strings.providerXcode, draft.xcode != null, viewModel::setXcodeEnabled)
            draft.xcode?.let { xcode ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = OptionIndent)) {
                    Switch(
                        checked = xcode.compilationCache,
                        onCheckedChange = { value ->
                            viewModel.updateRepositoryDraft { it.copy(xcode = xcode.copy(compilationCache = value)) }
                        },
                        modifier = Modifier.handPointer(),
                    )
                    Text(strings.xcodeCompilationCacheLabel, modifier = Modifier.padding(start = Spacing.sm))
                }
            }
        }
    }
}

@Composable
private fun StackSuggestions(viewModel: ProjectConfigViewModel, strings: Strings) {
    val suggestions = viewModel.stackSuggestions
    if (suggestions.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            strings.stackSuggestionsTitle,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        suggestions.forEach { suggestion ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    strings.stackSuggestion(suggestion.provider.label(strings), suggestion.evidence.joinToString(", ")),
                    style = ShogunTheme.typography.data,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(onClick = { viewModel.acceptSuggestion(suggestion.provider) }) { Text(strings.turnOnProvider) }
            }
        }
    }
}

internal fun SuggestedProvider.label(strings: Strings): String = when (this) {
    SuggestedProvider.GRADLE -> strings.providerGradle
    SuggestedProvider.EMULATOR -> strings.providerEmulator
    SuggestedProvider.IOS_SIMULATOR -> strings.providerIosSimulator
    SuggestedProvider.XCODE -> strings.providerXcode
    SuggestedProvider.MAVEN -> strings.providerMaven
    SuggestedProvider.DOCKER_COMPOSE -> strings.providerDockerCompose
}

@Composable
private fun ProviderSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.handPointer())
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = Spacing.sm))
    }
}

@Composable
private fun OptionField(
    label: String,
    value: String,
    placeholder: String,
    issue: app.luxion.shogunai.domain.model.RepositoryConfigIssue?,
    onChange: (String) -> Unit,
) {
    val strings = LocalStrings.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        textStyle = ShogunTheme.typography.data,
        singleLine = true,
        isError = issue != null,
        supportingText = issue?.let { { Text(strings.repositoryConfigIssue(it)) } },
        modifier = Modifier.fillMaxWidth().padding(start = OptionIndent),
    )
}

@Composable
private fun EnvironmentEditor(viewModel: ProjectConfigViewModel, strings: Strings) {
    val draft = viewModel.repositoryDraft
    var showTemplates by remember { mutableStateOf(false) }
    SettingsField(strings.repositoryConfigEnvironmentTitle) {
        if (draft.environment.isEmpty()) {
            Text(
                strings.environmentEmpty,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        draft.environment.forEach { row ->
            val nameIssue = draft.issueFor(RepositoryConfigField.EnvironmentName(row.id))
            val templateIssue = draft.issueFor(RepositoryConfigField.EnvironmentTemplate(row.id))
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = row.name,
                    onValueChange = { viewModel.updateEnvironmentRow(row.id, name = it) },
                    label = { Text(strings.environmentVariableNameLabel) },
                    textStyle = ShogunTheme.typography.data,
                    singleLine = true,
                    isError = nameIssue != null,
                    supportingText = nameIssue?.let { { Text(strings.repositoryConfigIssue(it)) } },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = row.template,
                    onValueChange = { viewModel.updateEnvironmentRow(row.id, template = it) },
                    label = { Text(strings.environmentVariableTemplateLabel) },
                    textStyle = ShogunTheme.typography.data,
                    singleLine = true,
                    isError = templateIssue != null,
                    supportingText = templateIssue?.let { { Text(strings.repositoryConfigIssue(it)) } },
                    modifier = Modifier.weight(1f),
                )
                IconAction(
                    icon = Icons.Default.Close,
                    label = strings.removeVariable,
                    onClick = { viewModel.removeEnvironmentRow(row.id) },
                    tint = ShogunTheme.status.danger.color,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton(onClick = viewModel::addEnvironmentRow) { Text(strings.addVariable) }
            NeutralTextButton(onClick = { showTemplates = !showTemplates }) { Text(strings.templateHelpTitle) }
        }
        if (showTemplates) TemplateHelpList(strings)
        val envFileIssue = draft.issueFor(RepositoryConfigField.EnvFile)
        OutlinedTextField(
            value = draft.envFile,
            onValueChange = { value -> viewModel.updateRepositoryDraft { it.copy(envFile = value) } },
            label = { Text(strings.envFileLabel) },
            placeholder = { Text(".env.shogunai") },
            textStyle = ShogunTheme.typography.data,
            singleLine = true,
            isError = envFileIssue != null,
            supportingText = { Text(envFileIssue?.let(strings::repositoryConfigIssue) ?: strings.envFileHelp) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PostCreateCommandsView(viewModel: ProjectConfigViewModel, strings: Strings) {
    SettingsField(strings.postCreateCommandsTitle) {
        viewModel.postCreateCommands.forEach { command ->
            Text(ShellQuoting.posixCommand(command), style = ShogunTheme.typography.data)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                if (viewModel.postCreateApproved) strings.postCreateApproved else strings.postCreateNotApproved,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (viewModel.postCreateApproved) {
                SecondaryButton(onClick = viewModel::revokePostCreateApproval) { Text(strings.revokeApproval) }
            } else {
                SecondaryButton(onClick = viewModel::approvePostCreateCommands) { Text(strings.approveCommands) }
            }
        }
    }
}

@Composable
private fun TemplateHelpList(strings: Strings) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        strings.templateHelp.forEach { help ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(help.template, style = ShogunTheme.typography.data, modifier = Modifier.padding(end = Spacing.sm))
                Text(
                    help.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
