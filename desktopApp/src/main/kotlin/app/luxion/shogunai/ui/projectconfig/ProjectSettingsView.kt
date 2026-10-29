package app.luxion.shogunai.ui.projectconfig

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.dp
import app.luxion.shogunai.domain.model.EditorId
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.ui.components.DialogSize
import app.luxion.shogunai.ui.components.Field
import app.luxion.shogunai.ui.components.PathField
import app.luxion.shogunai.ui.components.ShogunDialog
import app.luxion.shogunai.ui.FilePicker
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.ui.components.DangerTextButton
import app.luxion.shogunai.ui.components.DropdownSelector
import app.luxion.shogunai.ui.components.IconAction
import app.luxion.shogunai.ui.components.NeutralTextButton
import app.luxion.shogunai.ui.components.Notice
import app.luxion.shogunai.ui.components.NoticeKind
import app.luxion.shogunai.ui.components.PrimaryButton
import app.luxion.shogunai.ui.components.SecondaryButton
import app.luxion.shogunai.ui.components.Spacing
import app.luxion.shogunai.ui.components.ViewTitle
import app.luxion.shogunai.ui.components.handPointer
import app.luxion.shogunai.ui.components.interactive
import app.luxion.shogunai.ui.components.onEnterKey
import app.luxion.shogunai.ui.i18n.LocalStrings
import app.luxion.shogunai.ui.i18n.Strings
import app.luxion.shogunai.ui.components.ErrorNotice
import app.luxion.shogunai.ui.relativeToBase
import app.luxion.shogunai.ui.shell.LeaveGuard
import app.luxion.shogunai.ui.theme.ShogunTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val ContentMaxWidth = 720.dp
private val IndexMinContentWidth = 1000.dp
private val IndexWidth = 200.dp
private val LocalFilesListMaxHeight = 160.dp
private val PreviewMaxHeight = 280.dp
private const val BasePathSettleDelayMillis = 400L
private const val SaveBarAnimationMillis = 150
private const val SectionActivationOffsetPx = 48

private enum class SettingsSectionId { GENERAL, WORKTREE_FILES, REPOSITORY_CONFIG, AGENT, EMULATOR, SIMULATOR }

@Composable
fun ProjectSettingsView(
    viewModel: ProjectConfigViewModel,
    saveRequests: Int,
    initialFolder: String?,
    onAttachLeaveGuard: (LeaveGuard) -> Unit,
    onDetachLeaveGuard: (LeaveGuard) -> Unit,
    onCanSaveChanged: (Boolean) -> Unit,
    onSaved: (Project, Boolean) -> Unit,
    onCancel: () -> Unit,
    onDiscard: () -> Unit = onCancel,
) {
    DisposableEffect(viewModel) {
        onAttachLeaveGuard(viewModel)
        onDispose { onDetachLeaveGuard(viewModel) }
    }
    if (!viewModel.folderChosen) {
        AddProjectFolderStep(viewModel, initialFolder, onCancel)
        return
    }
    SettingsForm(viewModel, saveRequests, onCanSaveChanged, onSaved, onDiscard)
}

@Composable
private fun SettingsForm(
    viewModel: ProjectConfigViewModel,
    saveRequests: Int,
    onCanSaveChanged: (Boolean) -> Unit,
    onSaved: (Project, Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val sectionTops = remember { mutableStateMapOf<SettingsSectionId, Int>() }
    val nameFocus = remember { FocusRequester() }
    val baseRepositoryFocus = remember { FocusRequester() }
    val worktreesRootFocus = remember { FocusRequester() }
    val agentCommandFocus = remember { FocusRequester() }
    var saveError by remember { mutableStateOf<Throwable?>(null) }
    var newSecretFile by remember { mutableStateOf("") }
    var repositoryPreview by remember { mutableStateOf<String?>(null) }

    val sections = buildList {
        add(SettingsSectionId.GENERAL)
        add(SettingsSectionId.WORKTREE_FILES)
        add(SettingsSectionId.REPOSITORY_CONFIG)
        add(SettingsSectionId.AGENT)
        if (viewModel.emulatorSection != EmulatorSectionState.Hidden) add(SettingsSectionId.EMULATOR)
        if (viewModel.simulatorSection != SimulatorSectionState.Hidden) add(SettingsSectionId.SIMULATOR)
    }
    val currentSection by remember(sections) {
        derivedStateOf {
            val atBottom = scrollState.maxValue > 0 && scrollState.value >= scrollState.maxValue
            if (atBottom) {
                sections.last()
            } else {
                sections.lastOrNull { (sectionTops[it] ?: 0) <= scrollState.value + SectionActivationOffsetPx }
                    ?: sections.first()
            }
        }
    }

    val submitNow: () -> Unit = {
        when (val outcome = viewModel.submit()) {
            is SaveOutcome.Saved -> {
                saveError = null
                onSaved(outcome.project, outcome.repositoryWritten)
            }
            is SaveOutcome.NeedsPreview -> repositoryPreview = outcome.content
            is SaveOutcome.InvalidRepositoryConfig -> scope.launch {
                scrollState.animateScrollTo(sectionTops[SettingsSectionId.REPOSITORY_CONFIG] ?: 0)
            }
            is SaveOutcome.Invalid -> when (outcome.firstInvalidField) {
                ProjectConfigField.NAME -> nameFocus.requestFocus()
                ProjectConfigField.BASE_REPOSITORY_PATH -> baseRepositoryFocus.requestFocus()
                ProjectConfigField.WORKTREES_ROOT -> worktreesRootFocus.requestFocus()
                ProjectConfigField.AGENT_COMMAND, ProjectConfigField.AGENT_NAME -> agentCommandFocus.requestFocus()
            }
            is SaveOutcome.Failed -> saveError = outcome.error
        }
    }
    val save: () -> Unit = {
        scope.launch {
            viewModel.settleBaseRepositoryPath()
            submitNow()
        }
    }
    val settleBasePath: () -> Unit = { scope.launch { viewModel.settleBaseRepositoryPath() } }
    LaunchedEffect(viewModel.baseRepositoryPath) {
        delay(BasePathSettleDelayMillis)
        viewModel.settleBaseRepositoryPath()
    }
    viewModel.pendingRepositoryChange?.let { path ->
        DiscardRepositoryEditsDialog(
            path = path,
            onConfirm = viewModel::confirmRepositoryChange,
            onDismiss = viewModel::cancelRepositoryChange,
        )
    }
    val currentSave by rememberUpdatedState(save)
    val confirmPreview: () -> Unit = {
        repositoryPreview = null
        when (val outcome = viewModel.submit(previewConfirmed = true)) {
            is SaveOutcome.Saved -> {
                saveError = null
                onSaved(outcome.project, outcome.repositoryWritten)
            }
            is SaveOutcome.Failed -> saveError = outcome.error
            else -> Unit
        }
    }
    repositoryPreview?.let { content ->
        RepositoryPreviewDialog(content, onConfirm = confirmPreview, onDismiss = { repositoryPreview = null })
    }

    val canSave = viewModel.hasUnsavedChanges || viewModel.isNewProject
    LaunchedEffect(canSave) { onCanSaveChanged(canSave) }
    DisposableEffect(Unit) { onDispose { onCanSaveChanged(false) } }
    val saveRequestsOnEntry = remember { saveRequests }
    LaunchedEffect(saveRequests) {
        if (saveRequests != saveRequestsOnEntry) currentSave()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val showIndex = maxWidth >= IndexMinContentWidth
            Row(modifier = Modifier.fillMaxSize()) {
                if (showIndex) {
                    SectionIndex(
                        sections = sections,
                        current = currentSection,
                        onSelect = { section ->
                            scope.launch { scrollState.animateScrollTo(sectionTops[section] ?: 0) }
                        },
                        modifier = Modifier.width(IndexWidth).fillMaxHeight().padding(Spacing.md),
                    )
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(scrollState)) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .widthIn(max = ContentMaxWidth)
                            .fillMaxWidth()
                            .padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                    ) {
                        ViewTitle(settingsTitle(strings, viewModel.isNewProject, viewModel.savedName))
                        GeneralSection(viewModel, nameFocus, baseRepositoryFocus, worktreesRootFocus, settleBasePath, sectionTops)
                        HorizontalDivider()
                        WorktreeFilesSection(viewModel, newSecretFile, { newSecretFile = it }, sectionTops)
                        HorizontalDivider()
                        RepositoryConfigSection(viewModel, Modifier.trackTop(SettingsSectionId.REPOSITORY_CONFIG, sectionTops))
                        HorizontalDivider()
                        AgentSection(viewModel, agentCommandFocus, sectionTops)
                        EmulatorSettingsSection(viewModel, Modifier.trackTop(SettingsSectionId.EMULATOR, sectionTops))
                        SimulatorSettingsSection(viewModel, Modifier.trackTop(SettingsSectionId.SIMULATOR, sectionTops))
                        saveError?.let { error ->
                            ErrorNotice(error = error, onDismiss = { saveError = null })
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = viewModel.isNewProject || viewModel.hasUnsavedChanges,
            enter = slideInVertically(tween(SaveBarAnimationMillis)) { it },
            exit = slideOutVertically(tween(SaveBarAnimationMillis)) { it },
        ) {
            SaveBar(strings, viewModel.isNewProject, onSave = save, onDiscard = onCancel)
        }
    }
}

private fun Modifier.trackTop(
    section: SettingsSectionId,
    tops: MutableMap<SettingsSectionId, Int>,
): Modifier = onGloballyPositioned { tops[section] = it.positionInParent().y.toInt() }

@Composable
private fun RepositoryPreviewDialog(content: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    ShogunDialog(
        title = strings.repositoryPreviewTitle,
        onDismissRequest = onDismiss,
        size = DialogSize.Medium,
        confirm = { PrimaryButton(onClick = onConfirm) { Text(strings.repositoryPreviewConfirm) } },
        dismiss = { NeutralTextButton(onClick = onDismiss) { Text(strings.cancel) } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(strings.repositoryPreviewMessage)
            Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth().heightIn(max = PreviewMaxHeight)) {
                Text(
                    content,
                    style = ShogunTheme.typography.data,
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(Spacing.sm),
                )
            }
        }
    }
}

@Composable
private fun SectionIndex(
    sections: List<SettingsSectionId>,
    current: SettingsSectionId,
    onSelect: (SettingsSectionId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        sections.forEach { section ->
            val selected = section == current
            Text(
                text = section.title(strings),
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) ShogunTheme.colors.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .interactive { onSelect(section) }
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            )
        }
    }
}

private fun SettingsSectionId.title(strings: Strings): String = when (this) {
    SettingsSectionId.GENERAL -> strings.generalSection
    SettingsSectionId.WORKTREE_FILES -> strings.worktreeFilesSection
    SettingsSectionId.REPOSITORY_CONFIG -> strings.repositoryConfigSection
    SettingsSectionId.AGENT -> strings.agentSection
    SettingsSectionId.EMULATOR -> strings.emulatorSection
    SettingsSectionId.SIMULATOR -> strings.simulatorSection
}

@Composable
private fun SaveBar(strings: Strings, adding: Boolean, onSave: () -> Unit, onDiscard: () -> Unit) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column {
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
            ) {
                if (!adding) {
                    Text(
                        strings.unsavedChanges,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                NeutralTextButton(onClick = onDiscard) { Text(if (adding) strings.cancel else strings.discard) }
                PrimaryButton(onClick = onSave) { Text(if (adding) strings.addProject else strings.save) }
            }
        }
    }
}

@Composable
private fun GeneralSection(
    viewModel: ProjectConfigViewModel,
    nameFocus: FocusRequester,
    baseRepositoryFocus: FocusRequester,
    worktreesRootFocus: FocusRequester,
    onSettleBasePath: () -> Unit,
    tops: MutableMap<SettingsSectionId, Int>,
) {
    val strings = LocalStrings.current
    SettingsSection(strings.generalSection, Modifier.trackTop(SettingsSectionId.GENERAL, tops)) {
        SettingsField(strings.nameLabel) {
            val error = viewModel.isErrorVisible(ProjectConfigField.NAME)
            Field(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                help = strings.nameHelp,
                error = if (error) strings.nameEmptyError else null,
                modifier = Modifier.fillMaxWidth(),
                inputModifier = Modifier.focusRequester(nameFocus),
            )
        }
        SettingsField(strings.baseRepositoryPathLabel) {
            val error = viewModel.isErrorVisible(ProjectConfigField.BASE_REPOSITORY_PATH)
            val errorMessage = baseRepositoryErrorMessage(viewModel, strings)
            PathField(
                value = viewModel.baseRepositoryPath,
                onValueChange = { viewModel.baseRepositoryPath = it },
                onBrowse = {
                    FilePicker.pickDirectory(viewModel.baseRepositoryPath, strings.selectFolderDialogTitle)?.let {
                        viewModel.baseRepositoryPath = it
                        onSettleBasePath()
                    }
                },
                help = strings.baseRepositoryPathHelp,
                error = if (error && errorMessage != null) errorMessage else null,
                textStyle = ShogunTheme.typography.data,
                modifier = Modifier.fillMaxWidth(),
                inputModifier = Modifier.focusRequester(baseRepositoryFocus).onFocusChanged { if (!it.isFocused) onSettleBasePath() },
            )
        }
        SettingsField(strings.worktreesRootLabel) {
            val rootError = viewModel.worktreesRoot.isBlank()
            PathField(
                value = viewModel.worktreesRoot,
                onValueChange = { viewModel.worktreesRoot = it },
                onBrowse = {
                    FilePicker.pickDirectory(viewModel.worktreesRoot, strings.selectFolderDialogTitle)?.let {
                        viewModel.worktreesRoot = it
                    }
                },
                help = strings.worktreesRootHelp,
                error = if (rootError) strings.worktreesRootEmptyError else null,
                textStyle = ShogunTheme.typography.data,
                modifier = Modifier.fillMaxWidth(),
                inputModifier = Modifier.focusRequester(worktreesRootFocus),
            )
        }
        SettingsField(strings.mainBranchLabel) {
            Field(
                value = viewModel.mainBranch,
                onValueChange = { viewModel.mainBranch = it },
                help = viewModel.detectedMainBranch?.let(strings::detectedMainBranch) ?: strings.mainBranchHelp,
                placeholder = viewModel.detectedMainBranch ?: strings.mainBranchPlaceholder,
                textStyle = ShogunTheme.typography.data,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun baseRepositoryErrorMessage(viewModel: ProjectConfigViewModel, strings: Strings): String? = when {
    viewModel.baseRepositoryPath.isBlank() -> strings.baseRepositoryPathEmptyError
    viewModel.duplicateProject != null -> strings.baseRepositoryAlreadyAdded(viewModel.duplicateProject!!.name)
    viewModel.basePathProblem == BasePathProblem.NOT_A_REPOSITORY -> strings.notAGitRepository(viewModel.baseRepositoryPath)
    viewModel.basePathProblem == BasePathProblem.NOT_TOP_LEVEL -> strings.baseRepositoryNotTopLevelError
    else -> null
}

@Composable
private fun DiscardRepositoryEditsDialog(path: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val strings = LocalStrings.current
    ShogunDialog(
        title = strings.discardRepositoryEditsTitle,
        onDismissRequest = onDismiss,
        confirm = { PrimaryButton(onClick = onConfirm) { Text(strings.discard) } },
        dismiss = { NeutralTextButton(onClick = onDismiss) { Text(strings.keepEditing) } },
    ) {
        Text(strings.discardRepositoryEditsMessage(path))
    }
}

@Composable
private fun WorktreeFilesSection(
    viewModel: ProjectConfigViewModel,
    newSecretFile: String,
    onNewSecretFileChange: (String) -> Unit,
    tops: MutableMap<SettingsSectionId, Int>,
) {
    val strings = LocalStrings.current
    SettingsSection(strings.worktreeFilesSection, Modifier.trackTop(SettingsSectionId.WORKTREE_FILES, tops)) {
        SettingsField(strings.repositoryProvisioningSection) {
            Text(
                strings.repositoryProvisioningDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RepositoryProvisioningList(
                state = viewModel.repositoryProvisioning,
                pendingPatterns = viewModel.pendingRepositoryPatterns,
                pendingLinkPatterns = viewModel.pendingLinkPatterns,
            )
            MobileSecretsSuggestions(viewModel)
        }
        SettingsField(strings.localFilesSection) {
            Text(
                strings.localFilesDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(modifier = Modifier.heightIn(max = LocalFilesListMaxHeight)) {
                items(viewModel.secretFiles) { file ->
                    LocalEntryRow(viewModel, file)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val addSecretFile = {
                    viewModel.addSecretFile(newSecretFile)
                    onNewSecretFileChange("")
                }
                Field(
                    value = newSecretFile,
                    onValueChange = onNewSecretFileChange,
                    placeholder = strings.newLocalFileLabel,
                    textStyle = ShogunTheme.typography.data,
                    modifier = Modifier.weight(1f),
                    inputModifier = Modifier.onEnterKey(action = addSecretFile),
                )
                SecondaryButton(onClick = addSecretFile, modifier = Modifier.padding(start = Spacing.sm)) {
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
            viewModel.rejectedLocalPattern?.let {
                Text(strings.localPatternMustBeRelative, color = ShogunTheme.status.danger.color, style = MaterialTheme.typography.bodySmall)
            }
            if (viewModel.canMoveLocalFilesToRepository) {
                SecondaryButton(onClick = viewModel::moveLocalFilesToRepository) {
                    Text(strings.moveToRepositoryConfig)
                }
            }
        }
    }
}

@Composable
internal fun LocalEntryRow(viewModel: ProjectConfigViewModel, entry: String) {
    val strings = LocalStrings.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(entry, style = ShogunTheme.typography.data, modifier = Modifier.weight(1f))
            if (viewModel.isPatternEntry(entry)) {
                Text(
                    strings.localPatternLabel,
                    style = ShogunTheme.typography.dataSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(strings.linkLocalEntry, style = MaterialTheme.typography.bodySmall)
            Switch(
                checked = viewModel.isLinkedEntry(entry),
                onCheckedChange = { viewModel.setEntryLinked(entry, it) },
                modifier = Modifier.handPointer(),
            )
            IconAction(
                icon = Icons.Default.Close,
                label = strings.removeLocalFile,
                onClick = { viewModel.removeSecretFile(entry) },
                tint = ShogunTheme.status.danger.color,
            )
        }
        viewModel.skippedLinksByFolder[entry]?.let { count ->
            Text(
                strings.skippedSymbolicLinks(count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun MobileSecretsSuggestions(viewModel: ProjectConfigViewModel) {
    val strings = LocalStrings.current
    val secrets = viewModel.mobileSecretSuggestions
    val warnings = viewModel.mobileSecrets.warnings
    if (secrets.isEmpty() && warnings.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.padding(top = Spacing.sm)) {
        Text(
            strings.mobileSecretsTitle,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        secrets.forEach { secret ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    "${secret.path} · ${strings.mobileSecretKind(secret.kind)}",
                    style = ShogunTheme.typography.data,
                    modifier = Modifier.weight(1f),
                )
                if (viewModel.canLinkMobileSecrets) {
                    SecondaryButton(onClick = { viewModel.linkMobileSecret(secret) }) { Text(strings.linkFromRepositoryConfig) }
                }
            }
        }
        warnings.forEach { warning ->
            Text(
                strings.keystoreOutsideRepository(warning.file, warning.key, warning.value),
                style = MaterialTheme.typography.bodySmall,
                color = ShogunTheme.status.warning.color,
            )
        }
    }
}

@Composable
private fun AgentSection(
    viewModel: ProjectConfigViewModel,
    agentCommandFocus: FocusRequester,
    tops: MutableMap<SettingsSectionId, Int>,
) {
    val strings = LocalStrings.current
    SettingsSection(strings.agentSection, Modifier.trackTop(SettingsSectionId.AGENT, tops)) {
        val duplicates = viewModel.duplicatedAgentNames
        viewModel.agents.forEachIndexed { index, agent ->
            AgentEditor(
                viewModel = viewModel,
                index = index,
                agent = agent,
                duplicated = agent.toConfig().displayName in duplicates,
                commandFocus = agentCommandFocus.takeIf { index == 0 },
            )
            if (index < viewModel.agents.lastIndex) HorizontalDivider()
        }
        SecondaryButton(onClick = viewModel::addAgent) { Text(strings.addAgent) }
        Text(
            strings.headroomDescription,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        EditorOverrideField(viewModel)
    }
}

@Composable
private fun AgentEditor(
    viewModel: ProjectConfigViewModel,
    index: Int,
    agent: AgentDraft,
    duplicated: Boolean,
    commandFocus: FocusRequester?,
) {
    val strings = LocalStrings.current
    val config = agent.toConfig()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (viewModel.agents.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (index == 0) "${config.displayName} · ${strings.defaultAgentMark}" else config.displayName,
                    style = ShogunTheme.typography.sectionTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (index > 0) NeutralTextButton(onClick = { viewModel.makeDefault(index) }) { Text(strings.makeDefaultAgent) }
                if (index > 0) NeutralTextButton(onClick = { viewModel.moveAgent(index, index - 1) }) { Text(strings.moveAgentUp) }
                if (index < viewModel.agents.lastIndex) {
                    NeutralTextButton(onClick = { viewModel.moveAgent(index, index + 1) }) { Text(strings.moveAgentDown) }
                }
                DangerTextButton(onClick = { viewModel.removeAgent(index) }) { Text(strings.removeAgent) }
            }
        }
        AgentFields(
            agent = agent,
            duplicated = duplicated,
            commandFocus = commandFocus,
            onChange = { changed -> viewModel.updateAgent(index) { changed } },
        )
    }
}

@Composable
internal fun AgentFields(
    agent: AgentDraft,
    duplicated: Boolean,
    commandFocus: FocusRequester?,
    onChange: (AgentDraft) -> Unit,
) {
    val strings = LocalStrings.current
    val config = agent.toConfig()
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SettingsField(strings.agentNameLabel) {
            Field(
                value = agent.name,
                onValueChange = { value -> onChange(agent.copy(name = value)) },
                error = if (duplicated) strings.duplicateAgentNameError(config.displayName) else null,
                placeholder = AgentLaunchConfig(agentCommand = agent.command).displayName,
                textStyle = ShogunTheme.typography.data,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SettingsField(strings.agentCommandLabel) {
            Field(
                value = agent.command,
                onValueChange = { value -> onChange(agent.copy(command = value)) },
                error = if (agent.command.isBlank()) strings.agentCommandEmptyError else null,
                textStyle = ShogunTheme.typography.data,
                modifier = Modifier.fillMaxWidth(),
                inputModifier = Modifier.let { if (commandFocus != null) it.focusRequester(commandFocus) else it },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = agent.useHeadroom,
                onCheckedChange = { value -> onChange(agent.copy(useHeadroom = value)) },
                modifier = Modifier.handPointer(),
            )
            Text(strings.useHeadroomLabel, modifier = Modifier.padding(start = Spacing.sm))
        }
    }
}

@Composable
private fun EditorOverrideField(viewModel: ProjectConfigViewModel) {
    val strings = LocalStrings.current
    val tools = viewModel.tools
    if (tools.detected == null) return
    val override = viewModel.editorOverride
    val options = listOf<EditorId?>(null) + (tools.availableEditors + listOfNotNull(override)).distinct()
    SettingsField(strings.projectEditorLabel) {
        Text(strings.machineOnlyLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        DropdownSelector(
            options = options,
            selected = override,
            onSelect = { viewModel.editorOverride = it },
            label = { option ->
                when {
                    option == null -> strings.defaultEditorOption(tools.editorShown?.displayName)
                    option !in tools.availableEditors -> strings.editorNotFoundOption(option.displayName)
                    else -> option.displayName
                }
            },
            placeholder = strings.projectEditorLabel,
            modifier = Modifier.fillMaxWidth(),
        )
        if (viewModel.editorOverrideMissing && override != null) {
            Text(
                strings.editorOverrideMissing(override.displayName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun AddProjectFolderStep(viewModel: ProjectConfigViewModel, initialFolder: String?, onCancel: () -> Unit) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(initialFolder) {
        if (initialFolder != null) viewModel.chooseFolder(initialFolder)
    }
    Box(modifier = Modifier.fillMaxSize().padding(Spacing.xl), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = ContentMaxWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ViewTitle(strings.addProjectTitle)
            Text(
                strings.addProjectIntro,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            viewModel.rejectedFolder?.let { folder ->
                Notice(
                    kind = NoticeKind.ERROR,
                    message = strings.notAGitRepository(folder),
                    onDismiss = viewModel::dismissRejectedFolder,
                )
            }
            viewModel.rejectedMultirepo?.let { (folder, multirepo) ->
                Notice(
                    kind = NoticeKind.ERROR,
                    message = strings.folderIsMultirepoWorkspace(folder, multirepo),
                    onDismiss = viewModel::dismissRejectedMultirepo,
                )
            }
            PrimaryButton(onClick = {
                FilePicker.pickDirectory("", strings.selectFolderDialogTitle)?.let { folder ->
                    scope.launch { viewModel.chooseFolder(folder) }
                }
            }) {
                Text(strings.chooseRepositoryFolder)
            }
            Text(
                strings.dropRepositoryHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NeutralTextButton(onClick = onCancel) { Text(strings.cancel) }
        }
    }
}

@Composable
private fun RepositoryProvisioningList(
    state: RepositoryProvisioningState,
    pendingPatterns: List<String>,
    pendingLinkPatterns: List<String>,
) {
    val strings = LocalStrings.current
    val declared = (state as? RepositoryProvisioningState.Declared)?.provisioning
    val rows = provisioningRows(declared, pendingPatterns, strings, pendingLinkPatterns)
    val commandCount = declared?.postCreateCommands?.size ?: 0

    when {
        state is RepositoryProvisioningState.Unreadable -> Text(
            strings.repositoryConfigUnreadable(state.path) + (state.detail?.let { "\n$it" } ?: ""),
            color = ShogunTheme.status.danger.color,
        )
        rows.isEmpty() && commandCount == 0 -> Text(strings.repositoryProvisioningEmpty)
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
    if (commandCount > 0) {
        Text(
            strings.postCreationCommandsNote(commandCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
