package app.luxion.shogunai.ui.projectconfig

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import androidx.lifecycle.viewModelScope
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.SimulatorSetup
import app.luxion.shogunai.domain.model.EmulatorSettings
import app.luxion.shogunai.domain.model.SimulatorRuntime
import app.luxion.shogunai.domain.model.SimulatorSettings
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.ui.shell.LeaveGuard
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

sealed interface SimulatorSectionState {
    data object Hidden : SimulatorSectionState
    data object Loading : SimulatorSectionState
    data class Available(
        val runtimes: List<SimulatorRuntime>,
        val testsWired: Boolean,
        val kotlinMultiplatform: Boolean,
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

enum class ProjectConfigField { NAME, BASE_REPOSITORY_PATH }

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

    var detectedMainBranch by mutableStateOf<String?>(null)
        private set

    var name: String
        get() = _name
        set(value) {
            _name = value
            touch(ProjectConfigField.NAME)
        }

    private var _baseRepositoryPath by mutableStateOf(existingProject?.config?.baseRepositoryPath ?: "")
    var worktreesRoot by mutableStateOf(existingProject?.config?.worktreesRoot ?: "")

    var secretFiles by mutableStateOf(
        existingProject?.config?.secretFiles.orEmpty().map { normalizeSecretFile(_baseRepositoryPath, it) },
    )
        private set

    var repositoryProvisioning by mutableStateOf(provisioningSource.stateFor(_baseRepositoryPath))
        private set

    var pendingRepositoryPatterns by mutableStateOf(emptyList<String>())
        private set

    var repositoryDraft by mutableStateOf(RepositoryConfigDraft())
        private set

    var repositoryChangedOnDisk by mutableStateOf(false)
        private set

    private var initialRepositoryDraft = RepositoryConfigDraft()
    private var repositoryFingerprint = ABSENT_FINGERPRINT
    private var nextEnvironmentRowId = 0
    private var repositoryPreviewConfirmed = false
    private var lastSaveWroteRepository = false

    val repositoryConfigEditable: Boolean
        get() = _baseRepositoryPath.isNotBlank() && repositoryProvisioning !is RepositoryProvisioningState.Unreadable

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
            repositoryProvisioning = provisioningSource.stateFor(value)
            loadRepositoryConfig()
            refreshEmulatorSection()
            refreshSimulatorSection()
            if (value.isBlank()) return
            val offset = worktreesRootOffset
            val followsBase = offset != null &&
                worktreesRoot.isSameDirectoryAs(applyWorktreesRootOffset(lastNonBlankBaseRepositoryPath, offset))
            if (followsBase) worktreesRoot = applyWorktreesRootOffset(value, offset!!)
            lastNonBlankBaseRepositoryPath = value
        }

    var agentCommand by mutableStateOf(existingProject?.config?.agentLaunchConfig?.agentCommand ?: "claude")
    var useHeadroom by mutableStateOf(existingProject?.config?.agentLaunchConfig?.useHeadroom ?: true)
    var mainBranch by mutableStateOf(existingProject?.config?.mainBranch.orEmpty())
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
    private var initialBaseRepositoryPath = baseRepositoryPath
    private var initialWorktreesRoot = worktreesRoot
    private var initialSecretFiles = secretFiles
    private var initialAgentCommand = agentCommand
    private var initialUseHeadroom = useHeadroom
    private var initialMainBranch = mainBranch
    private var initialEmulatorAvd = emulatorAvd
    private var initialEmulatorNoWindow = emulatorNoWindow
    private var initialSimulatorDevice = simulatorDevice
    private var initialSimulatorRuntime = simulatorRuntime

    init {
        loadRepositoryConfig()
        refreshEmulatorSection()
        refreshSimulatorSection()
    }

    private fun loadRepositoryConfig() {
        val path = _baseRepositoryPath
        val provisioning = if (path.isBlank() || repositoryProvisioning is RepositoryProvisioningState.Unreadable) {
            null
        } else {
            runCatching { provisioningSource.read(path) }.getOrNull()
        }
        repositoryFingerprint = if (path.isBlank()) ABSENT_FINGERPRINT else runCatching { provisioningSource.fingerprint(path) }.getOrDefault(ABSENT_FINGERPRINT)
        repositoryDraft = RepositoryConfigDraft.of(provisioning)
        initialRepositoryDraft = repositoryDraft
        nextEnvironmentRowId = repositoryDraft.environment.size
        repositoryChangedOnDisk = false
    }

    fun reloadRepositoryConfig() {
        repositoryProvisioning = provisioningSource.stateFor(_baseRepositoryPath)
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
        val repositoryPath = _baseRepositoryPath
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
                    )
                },
                onFailure = { SimulatorSectionState.Failed(it) },
            )
        }
    }

    fun installSimulatorInitScript(): Result<Unit> =
        simulatorSetup.installInitScript().onSuccess {
            val current = simulatorSection
            if (current is SimulatorSectionState.Available) simulatorSection = current.copy(testsWired = true)
        }

    private fun refreshEmulatorSection() {
        emulatorLoad?.cancel()
        val repositoryPath = _baseRepositoryPath
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
        ProjectConfigField.BASE_REPOSITORY_PATH -> baseRepositoryPath.isBlank()
    }

    private fun touch(field: ProjectConfigField) {
        touchedFields = touchedFields + field
    }

    suspend fun chooseFolder(folder: String): Result<Unit> =
        inspectRepository(folder).map { inspection ->
            val repository = File(inspection.topLevel)
            baseRepositoryPath = inspection.topLevel
            name = repository.name
            worktreesRoot = File(repository.parentFile, "${repository.name}-worktrees").path
            detectedMainBranch = inspection.mainBranch
            rejectedFolder = null
            folderChosen = true
            touchedFields = emptySet()
            markPristine()
        }.onFailure { rejectedFolder = folder }

    fun dismissRejectedFolder() {
        rejectedFolder = null
    }

    private fun markPristine() {
        initialName = name
        initialBaseRepositoryPath = baseRepositoryPath
        initialWorktreesRoot = worktreesRoot
        initialSecretFiles = secretFiles
    }

    fun submit(previewConfirmed: Boolean = false): SaveOutcome {
        saveAttempted = true
        firstInvalidField?.let { return SaveOutcome.Invalid(it) }
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
        get() = repositoryDraftChanged || pendingRepositoryPatterns.isNotEmpty()

    private fun pendingRepositoryEdit() = repositoryDraft.toEdit(addCopyPatterns = pendingRepositoryPatterns)

    override val hasUnsavedChanges: Boolean
        get() = name != initialName ||
            baseRepositoryPath != initialBaseRepositoryPath ||
            worktreesRoot != initialWorktreesRoot ||
            secretFiles != initialSecretFiles ||
            pendingRepositoryPatterns.isNotEmpty() ||
            repositoryDraftChanged ||
            agentCommand != initialAgentCommand ||
            useHeadroom != initialUseHeadroom ||
            mainBranch != initialMainBranch ||
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
        if (normalized.isNotBlank() && normalized !in secretFiles) secretFiles = secretFiles + normalized
    }

    fun removeSecretFile(fileName: String) {
        secretFiles = secretFiles - fileName
    }

    fun moveLocalFilesToRepository() {
        if (!canMoveLocalFilesToRepository) return
        val movable = secretFiles.filter(::isInsideRepository)
        pendingRepositoryPatterns = (pendingRepositoryPatterns + movable.map(::anchoredPattern)).distinct()
        secretFiles = secretFiles - movable.toSet()
    }

    private fun String.isSameDirectoryAs(other: String): Boolean =
        trimEnd('/', '\\') == other.trimEnd('/', '\\')

    private fun writeRepositoryConfig() {
        when {
            repositoryDraftChanged -> provisioningSource.write(baseRepositoryPath, pendingRepositoryEdit(), repositoryFingerprint)
            pendingRepositoryPatterns.isNotEmpty() ->
                provisioningSource.addCopyPatterns(baseRepositoryPath, pendingRepositoryPatterns, repositoryFingerprint)
            else -> return
        }
        lastSaveWroteRepository = true
        pendingRepositoryPatterns = emptyList()
        repositoryProvisioning = provisioningSource.stateFor(baseRepositoryPath)
        loadRepositoryConfig()
        refreshEmulatorSection()
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
                agentLaunchConfig = AgentLaunchConfig(agentCommand = agentCommand, useHeadroom = useHeadroom),
                mainBranch = mainBranch.trim().ifEmpty { null },
                emulator = EmulatorSettings(avd = emulatorAvd?.trim()?.ifEmpty { null }, noWindow = emulatorNoWindow),
                simulator = SimulatorSettings(device = simulatorDevice, runtime = simulatorRuntime),
            ),
        )
        projectRepository.save(project)
        project
    }

    private companion object {
        const val ABSENT_FINGERPRINT = "absent"
    }
}
