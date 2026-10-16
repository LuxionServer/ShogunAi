package app.luxion.shogunai.ui.projectconfig

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import java.util.UUID

class ProjectConfigViewModel(
    private val projectRepository: ProjectRepository,
    private val provisioningSource: RepositoryProvisioningSource,
    existingProject: Project?,
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

    private val initialName = name
    private val initialBaseRepositoryPath = baseRepositoryPath
    private val initialWorktreesRoot = worktreesRoot
    private val initialSecretFiles = secretFiles
    private val initialAgentCommand = agentCommand
    private val initialUseHeadroom = useHeadroom
    private val initialMainBranch = mainBranch

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
            mainBranch != initialMainBranch

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
            ),
        )
        projectRepository.save(project)
        project
    }
}
