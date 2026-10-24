package app.luxion.shogunai.ui.projectconfig

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.io.RepositoryTrust
import androidx.lifecycle.viewModelScope
import app.luxion.shogunai.domain.model.RepositoryApprovalSubject
import app.luxion.shogunai.domain.model.approvalSubject
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.SimulatorSetup
import app.luxion.shogunai.domain.model.EmulatorSettings
import app.luxion.shogunai.domain.model.SimulatorRuntime
import app.luxion.shogunai.domain.model.SimulatorSettings
import app.luxion.shogunai.domain.io.DirectoryListing
import app.luxion.shogunai.ui.isAbsolutePath
import app.luxion.shogunai.domain.model.DetectedStack
import app.luxion.shogunai.domain.model.MobileSecret
import app.luxion.shogunai.domain.model.MobileSecretsReport
import app.luxion.shogunai.domain.model.isLocalPattern
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.projectDefaultsFor
import app.luxion.shogunai.domain.model.ProviderSuggestion
import app.luxion.shogunai.domain.model.SuggestedProvider
import app.luxion.shogunai.domain.model.suggestProviders
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.ui.shell.LeaveGuard
import app.luxion.shogunai.ui.shell.ToolsPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface SimulatorSectionState {
    data object Hidden : SimulatorSectionState
    data object Loading : SimulatorSectionState
    data class Available(
        val runtimes: List<SimulatorRuntime>,
        val testsWired: Boolean,
        val kotlinMultiplatform: Boolean,
        val initScriptInstalled: Boolean = false,
    ) : SimulatorSectionState
    data class Failed(val error: Throwable) : SimulatorSectionState
}

sealed interface EmulatorSectionState {
    data object Hidden : EmulatorSectionState
    data object Loading : EmulatorSectionState
    data class Available(val avds: List<String>) : EmulatorSectionState
    data class SdkNotFound(val checked: List<String>) : EmulatorSectionState
    data class Failed(val error: Throwable) : EmulatorSectionState
}

enum class ProjectConfigField { NAME, BASE_REPOSITORY_PATH, WORKTREES_ROOT, AGENT_COMMAND, AGENT_NAME }

data class AgentDraft(val name: String = "", val command: String = "claude", val useHeadroom: Boolean = true) {
    fun toConfig() = AgentLaunchConfig(
        name = name.trim().ifEmpty { null },
        agentCommand = command,
        useHeadroom = useHeadroom,
    )

    companion object {
        fun from(config: AgentLaunchConfig) = AgentDraft(config.name.orEmpty(), config.agentCommand, config.useHeadroom)
    }
}

enum class BasePathProblem { NOT_A_REPOSITORY, NOT_TOP_LEVEL }

data class RepositoryInspection(val topLevel: String, val mainBranch: String?)

sealed interface SaveOutcome {
    data class Saved(val project: Project, val repositoryWritten: Boolean = false) : SaveOutcome
    data class Invalid(val firstInvalidField: ProjectConfigField) : SaveOutcome
    data class InvalidRepositoryConfig(val field: RepositoryConfigField) : SaveOutcome
    data class NeedsPreview(val content: String) : SaveOutcome
    data class Failed(val error: Throwable) : SaveOutcome
}

class ProjectConfigViewModel(
    private val projectRepository: ProjectRepository,
    private val provisioningSource: RepositoryProvisioningSource,
    existingProject: Project?,
    private val loadAvds: suspend (repositoryPath: String) -> Result<List<String>> = { Result.success(emptyList()) },
    private val simulatorSetup: SimulatorSetup = SimulatorSetup.NONE,
    private val inspectRepository: suspend (folder: String) -> Result<RepositoryInspection> = {
        Result.failure(WorktreeError.RepositoryNotFound(it))
    },
    private val supportsApplePlatforms: Boolean = true,
    private val trust: RepositoryTrust = RepositoryTrust.NONE,
    val tools: ToolsPreferences = ToolsPreferences.NONE,
    private val detectStacks: (repositoryPath: String) -> List<DetectedStack> = { emptyList() },
    private val multirepoOfWorkspace: (topLevel: String) -> String? = { null },
    private val detectMobileSecrets: (suspend (ProjectConfig) -> MobileSecretsReport)? = null,
    private val listLocalFolder: (path: String) -> DirectoryListing? = { null },
) : ViewModel(), LeaveGuard {
    private val existingId = existingProject?.id
    val isNewProject: Boolean = existingProject == null

    private var _name by mutableStateOf(existingProject?.name ?: "")
    private var touchedFields by mutableStateOf(emptySet<ProjectConfigField>())

    var saveAttempted by mutableStateOf(false)
        private set

    var folderChosen by mutableStateOf(existingProject != null)
        private set

    var rejectedFolder by mutableStateOf<String?>(null)
        private set

    var rejectedMultirepo by mutableStateOf<Pair<String, String>?>(null)
        private set

    var detectedMainBranch by mutableStateOf<String?>(null)
        private set

    var name: String
        get() = _name
        set(value) {
            _name = value
            touch(ProjectConfigField.NAME)
        }

    private var _baseRepositoryPath by mutableStateOf(existingProject?.config?.baseRepositoryPath ?: "")
    private var loadedRepositoryPath = _baseRepositoryPath
    private val otherProjects = runCatching { projectRepository.list() }.getOrDefault(emptyList()).filter { it.id != existingId }
    private var inspectedPath: String? = null
    private var inspectionProblem by mutableStateOf<BasePathProblem?>(null)

    var pendingRepositoryChange by mutableStateOf<String?>(null)
        private set

    val basePathProblem: BasePathProblem?
        get() = inspectionProblem.takeIf { inspectedPath == _baseRepositoryPath }

    val duplicateProject: Project?
        get() = otherProjects.firstOrNull { it.config.baseRepositoryPath.isSameDirectoryAs(_baseRepositoryPath) }
    var worktreesRoot by mutableStateOf(existingProject?.config?.worktreesRoot ?: "")

    var secretFiles by mutableStateOf(
        existingProject?.config?.secretFiles.orEmpty().map { normalizeSecretFile(_baseRepositoryPath, it) },
    )
        private set

    var linkedSecretFiles by mutableStateOf(
        existingProject?.config?.linkedSecretFiles.orEmpty().map { normalizeSecretFile(_baseRepositoryPath, it) }.toSet(),
    )
        private set

    var rejectedLocalPattern by mutableStateOf<String?>(null)
        private set

    var skippedLinksByFolder by mutableStateOf(emptyMap<String, Int>())
        private set

    var mobileSecrets by mutableStateOf(MobileSecretsReport())
        private set

    private var mobileSecretsLoad: Job? = null

    var repositoryProvisioning by mutableStateOf(provisioningSource.stateFor(_baseRepositoryPath))
        private set

    var pendingRepositoryPatterns by mutableStateOf(emptyList<String>())
        private set

    var pendingLinkPatterns by mutableStateOf(emptyList<String>())
        private set

    var repositoryDraft by mutableStateOf(RepositoryConfigDraft())
        private set

    var repositoryChangedOnDisk by mutableStateOf(false)
        private set

    var repositoryApproved by mutableStateOf(false)
        private set

    var approvalSubject by mutableStateOf(RepositoryApprovalSubject())
        private set

    private var detectedStacks by mutableStateOf(emptyList<DetectedStack>())
    private var initialRepositoryDraft = RepositoryConfigDraft()
    private var repositoryFingerprint = ABSENT_FINGERPRINT
    private var nextEnvironmentRowId = 0
    private var repositoryPreviewConfirmed = false
    private var lastSaveWroteRepository = false

    val repositoryConfigEditable: Boolean
        get() = loadedRepositoryPath.isNotBlank() && repositoryProvisioning !is RepositoryProvisioningState.Unreadable

    val showsApplePlatformProviders: Boolean get() = supportsApplePlatforms

    private val repositoryDraftChanged: Boolean
        get() = repositoryConfigEditable && repositoryDraft != initialRepositoryDraft

    private val worktreesRootOffset = worktreesRootOffset(_baseRepositoryPath, worktreesRoot)
    private var lastNonBlankBaseRepositoryPath = _baseRepositoryPath

    var baseRepositoryPath: String
        get() = _baseRepositoryPath
        set(value) {
            _baseRepositoryPath = value
            touch(ProjectConfigField.BASE_REPOSITORY_PATH)
            if (value.isBlank()) return
            val offset = worktreesRootOffset
            val followsBase = offset != null &&
                worktreesRoot.isSameDirectoryAs(applyWorktreesRootOffset(lastNonBlankBaseRepositoryPath, offset))
            if (followsBase) worktreesRoot = applyWorktreesRootOffset(value, offset)
            lastNonBlankBaseRepositoryPath = value
        }

    var agents by mutableStateOf(
        existingProject?.config?.agents?.map(AgentDraft::from) ?: listOf(AgentDraft()),
    )
        private set
    var mainBranch by mutableStateOf(existingProject?.config?.mainBranch.orEmpty())
    var editorOverride by mutableStateOf(existingProject?.config?.editorOverride)
    val editorOverrideMissing: Boolean get() = editorOverride?.let { it !in tools.availableEditors } == true
    var emulatorAvd by mutableStateOf(existingProject?.config?.emulator?.avd)
    var emulatorNoWindow by mutableStateOf(existingProject?.config?.emulator?.noWindow ?: false)

    var emulatorSection by mutableStateOf<EmulatorSectionState>(EmulatorSectionState.Hidden)
        private set
    private var emulatorLoad: Job? = null

    var simulatorDevice by mutableStateOf(existingProject?.config?.simulator?.device)
    var simulatorRuntime by mutableStateOf(existingProject?.config?.simulator?.runtime)

    var simulatorSection by mutableStateOf<SimulatorSectionState>(SimulatorSectionState.Hidden)
        private set
    private var simulatorLoad: Job? = null

    val simulatorSnippet: String get() = simulatorSetup.snippet
    val simulatorInitScriptPath: String get() = simulatorSetup.initScriptPath

    private var initialName = name
    val savedName: String get() = initialName
    private var initialBaseRepositoryPath = baseRepositoryPath
    private var initialWorktreesRoot = worktreesRoot
    private var initialSecretFiles = secretFiles
    private var initialLinkedSecretFiles = linkedSecretFiles
    private var initialAgents = agents
    private var initialMainBranch = mainBranch
    private var initialEditorOverride = editorOverride
    private var initialEmulatorAvd = emulatorAvd
    private var initialEmulatorNoWindow = emulatorNoWindow
    private var initialSimulatorDevice = simulatorDevice
    private var initialSimulatorRuntime = simulatorRuntime

    init {
        loadRepositoryConfig()
        refreshEmulatorSection()
        refreshSimulatorSection()
    }

    private val hasUnsavedRepositoryEdits: Boolean
        get() = repositoryDraftChanged || pendingRepositoryPatterns.isNotEmpty()

    private val repositoryIsStale: Boolean
        get() = hasUnsavedRepositoryEdits && !_baseRepositoryPath.isSameDirectoryAs(loadedRepositoryPath)

    suspend fun settleBaseRepositoryPath() {
        val path = _baseRepositoryPath
        if (path.isBlank() || path.isSameDirectoryAs(loadedRepositoryPath)) {
            inspectedPath = null
            inspectionProblem = null
            pendingRepositoryChange = null
            return
        }
        if (inspectedPath == path && (inspectionProblem != null || pendingRepositoryChange == path)) return
        val result = inspectRepository(path)
        if (_baseRepositoryPath != path) return
        inspectedPath = path
        val inspection = result.getOrNull()
        inspectionProblem = when {
            inspection == null -> BasePathProblem.NOT_A_REPOSITORY
            !inspection.topLevel.isSameDirectoryAs(path) -> BasePathProblem.NOT_TOP_LEVEL
            else -> null
        }
        if (inspection == null || inspectionProblem != null) return
        detectedMainBranch = inspection.mainBranch
        if (hasUnsavedRepositoryEdits) pendingRepositoryChange = path else loadRepository(path)
    }

    fun confirmRepositoryChange() {
        pendingRepositoryChange?.let(::loadRepository)
    }

    fun cancelRepositoryChange() {
        pendingRepositoryChange = null
        baseRepositoryPath = loadedRepositoryPath
    }

    private fun loadRepository(path: String) {
        loadedRepositoryPath = path
        pendingRepositoryChange = null
        pendingRepositoryPatterns = emptyList()
        pendingLinkPatterns = emptyList()
        repositoryProvisioning = provisioningSource.stateFor(path)
        loadRepositoryConfig()
        refreshEmulatorSection()
        refreshSimulatorSection()
    }

    private fun loadRepositoryConfig() {
        val path = loadedRepositoryPath
        val provisioning = if (path.isBlank() || repositoryProvisioning is RepositoryProvisioningState.Unreadable) {
            null
        } else {
            runCatching { provisioningSource.read(path) }.getOrNull()
        }
        repositoryFingerprint = if (path.isBlank()) ABSENT_FINGERPRINT else runCatching { provisioningSource.fingerprint(path) }.getOrDefault(ABSENT_FINGERPRINT)
        repositoryDraft = RepositoryConfigDraft.of(provisioning)
        approvalSubject = provisioning?.approvalSubject ?: RepositoryApprovalSubject()
        detectedStacks = if (path.isBlank()) emptyList() else runCatching { detectStacks(path) }.getOrDefault(emptyList())
        refreshLocalEntries()
        initialRepositoryDraft = repositoryDraft
        nextEnvironmentRowId = repositoryDraft.environment.size
        repositoryChangedOnDisk = false
        repositoryPreviewConfirmed = false
        refreshRepositoryApproval()
    }

    private fun refreshRepositoryApproval() {
        val subject = approvalSubject
        repositoryApproved = !subject.isEmpty && loadedRepositoryPath.isNotBlank() &&
            runCatching { trust.isApproved(loadedRepositoryPath, subject) }.getOrDefault(false)
    }

    fun approveRepository() {
        val subject = approvalSubject
        if (subject.isEmpty || loadedRepositoryPath.isBlank()) return
        trust.approve(loadedRepositoryPath, subject)
        refreshRepositoryApproval()
    }

    fun revokeRepositoryApproval() {
        if (loadedRepositoryPath.isBlank()) return
        trust.revoke(loadedRepositoryPath)
        refreshRepositoryApproval()
    }

    fun reloadRepositoryConfig() {
        repositoryProvisioning = provisioningSource.stateFor(loadedRepositoryPath)
        loadRepositoryConfig()
        refreshEmulatorSection()
    }

    fun updateRepositoryDraft(transform: (RepositoryConfigDraft) -> RepositoryConfigDraft) {
        if (repositoryConfigEditable) repositoryDraft = transform(repositoryDraft)
    }

    fun setGradleEnabled(enabled: Boolean) =
        updateRepositoryDraft { it.copy(gradle = if (enabled) it.gradle ?: GradleDraft() else null) }

    fun setEmulatorEnabled(enabled: Boolean) =
        updateRepositoryDraft { it.copy(emulator = if (enabled) it.emulator ?: EmulatorDraft() else null) }

    fun setIosSimulatorEnabled(enabled: Boolean) =
        updateRepositoryDraft { it.copy(iosSimulator = if (enabled) it.iosSimulator ?: IosSimulatorDraft() else null) }

    fun setXcodeEnabled(enabled: Boolean) =
        updateRepositoryDraft { it.copy(xcode = if (enabled) it.xcode ?: XcodeDraft() else null) }

    fun setMavenEnabled(enabled: Boolean) =
        updateRepositoryDraft { it.copy(maven = if (enabled) it.maven ?: MavenDraft() else null) }

    fun setDockerComposeEnabled(enabled: Boolean) =
        updateRepositoryDraft { it.copy(dockerCompose = if (enabled) it.dockerCompose ?: ComposeDraft() else null) }

    val stackSuggestions: List<ProviderSuggestion>
        get() = if (!repositoryConfigEditable) {
            emptyList()
        } else {
            suggestProviders(detectedStacks, repositoryDraft.toEdit().providers, supportsApplePlatforms).suggestions
        }

    fun acceptSuggestion(provider: SuggestedProvider) {
        when (provider) {
            SuggestedProvider.GRADLE -> setGradleEnabled(true)
            SuggestedProvider.EMULATOR -> setEmulatorEnabled(true)
            SuggestedProvider.IOS_SIMULATOR -> setIosSimulatorEnabled(true)
            SuggestedProvider.XCODE -> setXcodeEnabled(true)
            SuggestedProvider.MAVEN -> setMavenEnabled(true)
            SuggestedProvider.DOCKER_COMPOSE -> setDockerComposeEnabled(true)
        }
    }

    fun addEnvironmentRow() =
        updateRepositoryDraft { it.copy(environment = it.environment + EnvironmentRow(nextEnvironmentRowId++, "", "")) }

    fun updateEnvironmentRow(id: Int, name: String? = null, template: String? = null) =
        updateRepositoryDraft { draft ->
            draft.copy(
                environment = draft.environment.map { row ->
                    if (row.id == id) row.copy(name = name ?: row.name, template = template ?: row.template) else row
                },
            )
        }

    fun removeEnvironmentRow(id: Int) =
        updateRepositoryDraft { draft -> draft.copy(environment = draft.environment.filterNot { it.id == id }) }

    private fun refreshSimulatorSection() {
        simulatorLoad?.cancel()
        val repositoryPath = loadedRepositoryPath
        if (repositoryPath.isBlank() || !simulatorSetup.isAvailable(repositoryPath)) {
            simulatorSection = SimulatorSectionState.Hidden
            return
        }
        simulatorSection = SimulatorSectionState.Loading
        simulatorLoad = viewModelScope.launch {
            simulatorSection = simulatorSetup.runtimes().fold(
                onSuccess = { runtimes ->
                    SimulatorSectionState.Available(
                        runtimes = runtimes.filter { it.platform.equals("iOS", ignoreCase = true) },
                        testsWired = simulatorSetup.isWired(repositoryPath),
                        kotlinMultiplatform = simulatorSetup.usesKotlinMultiplatform(repositoryPath),
                        initScriptInstalled = simulatorSetup.isInitScriptInstalled(),
                    )
                },
                onFailure = { SimulatorSectionState.Failed(it) },
            )
        }
    }

    fun installSimulatorInitScript(): Result<Unit> =
        simulatorSetup.installInitScript().onSuccess {
            val current = simulatorSection
            if (current is SimulatorSectionState.Available) simulatorSection = current.copy(testsWired = true, initScriptInstalled = true)
        }

    fun uninstallSimulatorInitScript(): Result<Unit> =
        simulatorSetup.uninstallInitScript().onSuccess {
            val current = simulatorSection
            if (current is SimulatorSectionState.Available) {
                simulatorSection = current.copy(
                    testsWired = simulatorSetup.isWired(loadedRepositoryPath),
                    initScriptInstalled = false,
                )
            }
        }

    private fun refreshEmulatorSection() {
        emulatorLoad?.cancel()
        val repositoryPath = loadedRepositoryPath
        val declared = (repositoryProvisioning as? RepositoryProvisioningState.Declared)?.provisioning?.providers?.emulator
            ?: runCatching { provisioningSource.read(repositoryPath).providers.emulator }.getOrNull()
        if (repositoryPath.isBlank() || declared == null) {
            emulatorSection = EmulatorSectionState.Hidden
            return
        }
        emulatorSection = EmulatorSectionState.Loading
        emulatorLoad = viewModelScope.launch {
            emulatorSection = loadAvds(repositoryPath).fold(
                onSuccess = { EmulatorSectionState.Available(it) },
                onFailure = { error ->
                    if (error is WorktreeError.AndroidSdkNotFound) {
                        EmulatorSectionState.SdkNotFound(error.checked)
                    } else {
                        EmulatorSectionState.Failed(error)
                    }
                },
            )
        }
    }

    val isValid: Boolean
        get() = firstInvalidField == null

    val firstInvalidField: ProjectConfigField?
        get() = ProjectConfigField.entries.firstOrNull(::isInvalid)

    fun isErrorVisible(field: ProjectConfigField): Boolean =
        isInvalid(field) && (saveAttempted || field in touchedFields)

    private fun isInvalid(field: ProjectConfigField): Boolean = when (field) {
        ProjectConfigField.NAME -> name.isBlank()
        ProjectConfigField.BASE_REPOSITORY_PATH ->
            baseRepositoryPath.isBlank() || duplicateProject != null || basePathProblem != null
        ProjectConfigField.WORKTREES_ROOT -> worktreesRoot.isBlank()
        ProjectConfigField.AGENT_COMMAND -> agents.any { it.command.isBlank() }
        ProjectConfigField.AGENT_NAME -> duplicatedAgentNames.isNotEmpty()
    }

    val duplicatedAgentNames: Set<String>
        get() = agents.map { it.toConfig().displayName }
            .groupingBy { it }.eachCount()
            .filterValues { it > 1 }.keys

    fun updateAgent(index: Int, transform: (AgentDraft) -> AgentDraft) {
        agents = agents.mapIndexed { position, agent -> if (position == index) transform(agent) else agent }
    }

    fun addAgent() {
        agents = agents + AgentDraft(command = "")
    }

    fun removeAgent(index: Int) {
        if (agents.size > 1) agents = agents.filterIndexed { position, _ -> position != index }
    }

    fun makeDefault(index: Int) = moveAgent(index, 0)

    fun moveAgent(index: Int, target: Int) {
        if (index !in agents.indices || target !in agents.indices) return
        val reordered = agents.toMutableList()
        reordered.add(target, reordered.removeAt(index))
        agents = reordered
    }

    private fun touch(field: ProjectConfigField) {
        touchedFields = touchedFields + field
    }

    suspend fun chooseFolder(folder: String): Result<Unit> {
        val inspected = inspectRepository(folder).getOrNull()
        val multirepo = inspected?.let { multirepoOfWorkspace(it.topLevel) }
        if (inspected != null && multirepo != null) {
            rejectedFolder = null
            rejectedMultirepo = inspected.topLevel to multirepo
            return Result.failure(IllegalStateException("$folder belongs to the multirepo $multirepo"))
        }
        rejectedMultirepo = null
        return chooseInspected(folder)
    }

    private suspend fun chooseInspected(folder: String): Result<Unit> =
        inspectRepository(folder).map { inspection ->
            val defaults = projectDefaultsFor(inspection.topLevel)
            baseRepositoryPath = inspection.topLevel
            loadRepository(inspection.topLevel)
            name = defaults.name
            worktreesRoot = defaults.worktreesRoot
            detectedMainBranch = inspection.mainBranch
            rejectedFolder = null
            folderChosen = true
            touchedFields = emptySet()
            markPristine()
        }.onFailure { rejectedFolder = folder }

    fun dismissRejectedFolder() {
        rejectedFolder = null
    }

    fun dismissRejectedMultirepo() {
        rejectedMultirepo = null
    }

    private fun markPristine() {
        initialName = name
        initialBaseRepositoryPath = baseRepositoryPath
        initialWorktreesRoot = worktreesRoot
        initialSecretFiles = secretFiles
        initialLinkedSecretFiles = linkedSecretFiles
    }

    fun submit(previewConfirmed: Boolean = false): SaveOutcome {
        saveAttempted = true
        firstInvalidField?.let { return SaveOutcome.Invalid(it) }
        if (pendingRepositoryChange != null || repositoryIsStale) return SaveOutcome.Invalid(ProjectConfigField.BASE_REPOSITORY_PATH)
        if (repositoryConfigEditable) {
            repositoryDraft.problems.firstOrNull()?.let { return SaveOutcome.InvalidRepositoryConfig(it.field) }
        }
        if (previewConfirmed) repositoryPreviewConfirmed = true
        if (needsRepositoryWrite && !repositoryPreviewConfirmed) {
            val preview = runCatching { provisioningSource.preview(baseRepositoryPath, pendingRepositoryEdit()) }
                .getOrElse { return SaveOutcome.Failed(it) }
            return SaveOutcome.NeedsPreview(preview)
        }
        return save().fold(
            onSuccess = { SaveOutcome.Saved(it, lastSaveWroteRepository) },
            onFailure = { error ->
                if (error is WorktreeError.RepositoryConfigChanged) repositoryChangedOnDisk = true
                SaveOutcome.Failed(error)
            },
        )
    }

    private val needsRepositoryWrite: Boolean
        get() = repositoryDraftChanged || pendingRepositoryPatterns.isNotEmpty() || pendingLinkPatterns.isNotEmpty()

    private fun pendingRepositoryEdit() =
        repositoryDraft.toEdit(addCopyPatterns = pendingRepositoryPatterns, addLinkPatterns = pendingLinkPatterns)

    override val hasUnsavedChanges: Boolean
        get() = name != initialName ||
            baseRepositoryPath != initialBaseRepositoryPath ||
            worktreesRoot != initialWorktreesRoot ||
            secretFiles != initialSecretFiles ||
            linkedSecretFiles != initialLinkedSecretFiles ||
            pendingRepositoryPatterns.isNotEmpty() ||
            pendingLinkPatterns.isNotEmpty() ||
            repositoryDraftChanged ||
            agents != initialAgents ||
            mainBranch != initialMainBranch ||
            editorOverride != initialEditorOverride ||
            emulatorAvd != initialEmulatorAvd ||
            emulatorNoWindow != initialEmulatorNoWindow ||
            simulatorDevice != initialSimulatorDevice ||
            simulatorRuntime != initialSimulatorRuntime

    val canMoveLocalFilesToRepository: Boolean
        get() = baseRepositoryPath.isNotBlank() &&
            repositoryProvisioning !is RepositoryProvisioningState.Unreadable &&
            secretFiles.any(::isInsideRepository)

    fun addSecretFile(fileName: String) {
        val normalized = normalizeSecretFile(baseRepositoryPath, fileName.trim())
        if (isLocalPattern(normalized) && !isInsideRepository(normalized)) {
            rejectedLocalPattern = normalized
            return
        }
        rejectedLocalPattern = null
        if (normalized.isNotBlank() && normalized !in secretFiles) {
            secretFiles = secretFiles + normalized
            refreshLocalEntries()
        }
    }

    fun removeSecretFile(fileName: String) {
        secretFiles = secretFiles - fileName
        linkedSecretFiles = linkedSecretFiles - fileName
        refreshLocalEntries()
    }

    fun isPatternEntry(entry: String): Boolean = isLocalPattern(entry)

    fun isLinkedEntry(entry: String): Boolean = entry in linkedSecretFiles

    fun setEntryLinked(entry: String, linked: Boolean) {
        if (entry !in secretFiles) return
        linkedSecretFiles = if (linked) linkedSecretFiles + entry else linkedSecretFiles - entry
        refreshLocalEntries()
    }

    fun moveLocalFilesToRepository() {
        if (!canMoveLocalFilesToRepository) return
        val movable = secretFiles.filter(::isInsideRepository)
        val (linked, copied) = movable.partition(::isLinkedEntry)
        pendingRepositoryPatterns = (pendingRepositoryPatterns + copied.map(::repositoryPatternOf)).distinct()
        pendingLinkPatterns = (pendingLinkPatterns + linked.map(::repositoryPatternOf)).distinct()
        secretFiles = secretFiles - movable.toSet()
        linkedSecretFiles = linkedSecretFiles - movable.toSet()
        refreshLocalEntries()
    }

    private fun repositoryPatternOf(entry: String): String = if (isLocalPattern(entry)) entry else anchoredPattern(entry)

    val mobileSecretSuggestions: List<MobileSecret>
        get() = mobileSecrets.secrets.filterNot { it.linkPattern in pendingLinkPatterns }

    val canLinkMobileSecrets: Boolean
        get() = repositoryConfigEditable

    fun linkMobileSecret(secret: MobileSecret) {
        if (!canLinkMobileSecrets) return
        pendingLinkPatterns = (pendingLinkPatterns + secret.linkPattern).distinct()
    }

    private fun refreshLocalEntries() {
        val base = loadedRepositoryPath
        skippedLinksByFolder = if (base.isBlank()) {
            emptyMap()
        } else {
            secretFiles.filterNot { isLocalPattern(it) || it in linkedSecretFiles }
                .mapNotNull { entry -> listLocalFolder(joinedPath(base, entry))?.let { entry to it.skippedLinks.size } }
                .filter { it.second > 0 }
                .toMap()
        }
        refreshMobileSecrets()
    }

    private fun refreshMobileSecrets() {
        val detect = detectMobileSecrets ?: return
        mobileSecretsLoad?.cancel()
        val base = loadedRepositoryPath
        if (base.isBlank()) {
            mobileSecrets = MobileSecretsReport()
            return
        }
        val config = ProjectConfig(
            baseRepositoryPath = base,
            worktreesRoot = worktreesRoot,
            secretFiles = secretFiles,
            linkedSecretFiles = linkedSecretFiles.toList(),
        )
        mobileSecretsLoad = viewModelScope.launch {
            mobileSecrets = runCatching { detect(config) }.getOrDefault(MobileSecretsReport())
        }
    }

    private fun joinedPath(base: String, entry: String): String =
        if (isAbsolutePath(entry)) entry else "${base.trimEnd('/', '\\')}/$entry"

    private fun String.isSameDirectoryAs(other: String): Boolean =
        trimEnd('/', '\\') == other.trimEnd('/', '\\')

    private fun writeRepositoryConfig() {
        when {
            repositoryDraftChanged -> writeAndApproveEnvironment()
            pendingRepositoryPatterns.isNotEmpty() || pendingLinkPatterns.isNotEmpty() ->
                provisioningSource.addPatterns(baseRepositoryPath, pendingRepositoryPatterns, pendingLinkPatterns, repositoryFingerprint)
            else -> return
        }
        lastSaveWroteRepository = true
        pendingRepositoryPatterns = emptyList()
        pendingLinkPatterns = emptyList()
        repositoryProvisioning = provisioningSource.stateFor(baseRepositoryPath)
        loadRepositoryConfig()
        repositoryPreviewConfirmed = true
        refreshEmulatorSection()
    }

    private fun writeAndApproveEnvironment() {
        val before = runCatching { provisioningSource.read(baseRepositoryPath).approvalSubject }.getOrNull()
        val approveSaved = before != null && (before.commands.isEmpty() || trust.isApproved(baseRepositoryPath, before))
        provisioningSource.write(baseRepositoryPath, pendingRepositoryEdit(), repositoryFingerprint)
        if (!approveSaved) return
        runCatching { provisioningSource.read(baseRepositoryPath).approvalSubject }.getOrNull()
            ?.takeIf { !it.isEmpty }
            ?.let { trust.approve(baseRepositoryPath, it) }
    }

    fun save(): Result<Project> = runCatching {
        lastSaveWroteRepository = false
        writeRepositoryConfig()
        val project = Project(
            id = existingId ?: UUID.randomUUID().toString(),
            name = name,
            config = ProjectConfig(
                baseRepositoryPath = baseRepositoryPath,
                worktreesRoot = worktreesRoot,
                secretFiles = secretFiles,
                linkedSecretFiles = secretFiles.filter(::isLinkedEntry),
                agentLaunchConfig = agents.first().toConfig(),
                additionalAgents = agents.drop(1).map(AgentDraft::toConfig),
                mainBranch = mainBranch.trim().ifEmpty { null },
                emulator = EmulatorSettings(avd = emulatorAvd?.trim()?.ifEmpty { null }, noWindow = emulatorNoWindow),
                simulator = SimulatorSettings(device = simulatorDevice, runtime = simulatorRuntime),
                editorOverride = editorOverride,
            ),
        )
        projectRepository.save(project)
        project
    }

    private companion object {
        const val ABSENT_FINGERPRINT = "absent"
    }
}
