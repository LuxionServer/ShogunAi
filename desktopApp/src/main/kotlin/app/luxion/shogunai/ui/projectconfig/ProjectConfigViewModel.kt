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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface SimulatorSectionState {
    data object Hidden : SimulatorSectionState
    data object Loading : SimulatorSectionState
    data class Available(val runtimes: List<SimulatorRuntime>, val testsWired: Boolean) : SimulatorSectionState
    data class Failed(val error: Throwable) : SimulatorSectionState
}

sealed interface EmulatorSectionState {
    data object Hidden : EmulatorSectionState
    data object Loading : EmulatorSectionState
    data class Available(val avds: List<String>) : EmulatorSectionState
    data class SdkNotFound(val checked: List<String>) : EmulatorSectionState
    data class Failed(val error: Throwable) : EmulatorSectionState
}

class ProjectConfigViewModel(
    private val projectRepository: ProjectRepository,
    private val provisioningSource: RepositoryProvisioningSource,
    existingProject: Project?,
    private val loadAvds: suspend (repositoryPath: String) -> Result<List<String>> = { Result.success(emptyList()) },
    private val simulatorSetup: SimulatorSetup = SimulatorSetup.NONE,
) : ViewModel() {
    private val existingId = existingProject?.id

    var name by mutableStateOf(existingProject?.name ?: "")
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

    private val worktreesRootOffset = worktreesRootOffset(_baseRepositoryPath, worktreesRoot)
    private var lastNonBlankBaseRepositoryPath = _baseRepositoryPath

    var baseRepositoryPath: String
        get() = _baseRepositoryPath
        set(value) {
            _baseRepositoryPath = value
            repositoryProvisioning = provisioningSource.stateFor(value)
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

    private val initialName = name
    private val initialBaseRepositoryPath = baseRepositoryPath
    private val initialWorktreesRoot = worktreesRoot
    private val initialSecretFiles = secretFiles
    private val initialAgentCommand = agentCommand
    private val initialUseHeadroom = useHeadroom
    private val initialMainBranch = mainBranch
    private val initialEmulatorAvd = emulatorAvd
    private val initialEmulatorNoWindow = emulatorNoWindow
    private val initialSimulatorDevice = simulatorDevice
    private val initialSimulatorRuntime = simulatorRuntime

    init {
        refreshEmulatorSection()
        refreshSimulatorSection()
    }

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
        get() = name.isNotBlank() && baseRepositoryPath.isNotBlank()

    val hasUnsavedChanges: Boolean
        get() = name != initialName ||
            baseRepositoryPath != initialBaseRepositoryPath ||
            worktreesRoot != initialWorktreesRoot ||
            secretFiles != initialSecretFiles ||
            pendingRepositoryPatterns.isNotEmpty() ||
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

    fun save(): Result<Project> = runCatching {
        if (pendingRepositoryPatterns.isNotEmpty()) {
            provisioningSource.addCopyPatterns(baseRepositoryPath, pendingRepositoryPatterns)
            pendingRepositoryPatterns = emptyList()
            repositoryProvisioning = provisioningSource.stateFor(baseRepositoryPath)
        }
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
}
