package app.luxion.shogunai.ui.projectconfig

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import java.util.UUID

class ProjectConfigViewModel(
    private val projectRepository: ProjectRepository,
    existingProject: Project?,
) : ViewModel() {
    private val existingId = existingProject?.id

    var name by mutableStateOf(existingProject?.name ?: "")
    var baseRepositoryPath by mutableStateOf(existingProject?.config?.baseRepositoryPath ?: "")
    var worktreesRoot by mutableStateOf(existingProject?.config?.worktreesRoot ?: "")
    var secretFiles by mutableStateOf(existingProject?.config?.secretFiles ?: emptyList())
        private set

    var agentCommand by mutableStateOf(existingProject?.config?.agentLaunchConfig?.agentCommand ?: "claude")
    var useHeadroom by mutableStateOf(existingProject?.config?.agentLaunchConfig?.useHeadroom ?: true)

    private val initialName = name
    private val initialBaseRepositoryPath = baseRepositoryPath
    private val initialWorktreesRoot = worktreesRoot
    private val initialSecretFiles = secretFiles
    private val initialAgentCommand = agentCommand
    private val initialUseHeadroom = useHeadroom

    val isValid: Boolean
        get() = name.isNotBlank() && baseRepositoryPath.isNotBlank()

    val hasUnsavedChanges: Boolean
        get() = name != initialName ||
            baseRepositoryPath != initialBaseRepositoryPath ||
            worktreesRoot != initialWorktreesRoot ||
            secretFiles != initialSecretFiles ||
            agentCommand != initialAgentCommand ||
            useHeadroom != initialUseHeadroom

    fun addSecretFile(fileName: String) {
        if (fileName.isNotBlank() && fileName !in secretFiles) secretFiles = secretFiles + fileName
    }

    fun removeSecretFile(fileName: String) {
        secretFiles = secretFiles - fileName
    }

    fun save(): Project {
        val project = Project(
            id = existingId ?: UUID.randomUUID().toString(),
            name = name,
            config = ProjectConfig(
                baseRepositoryPath = baseRepositoryPath,
                worktreesRoot = worktreesRoot,
                secretFiles = secretFiles,
                agentLaunchConfig = AgentLaunchConfig(agentCommand = agentCommand, useHeadroom = useHeadroom),
            ),
        )
        projectRepository.save(project)
        return project
    }
}
