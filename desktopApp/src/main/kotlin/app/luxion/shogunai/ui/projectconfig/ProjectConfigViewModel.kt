package app.luxion.shogunai.ui.projectconfig

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.TerminalPreference
import app.luxion.shogunai.domain.model.TerminalSelectionMode
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

    var terminalSelectionMode by mutableStateOf(
        existingProject?.config?.terminalPreference?.mode ?: TerminalSelectionMode.AUTO_DETECT,
    )
    var terminalEmulator by mutableStateOf(existingProject?.config?.terminalPreference?.emulator)
    var customCommandTemplateText by mutableStateOf(
        existingProject?.config?.terminalPreference?.customCommandTemplate?.joinToString("\n") ?: "",
    )

    var agentCommand by mutableStateOf(existingProject?.config?.agentLaunchConfig?.agentCommand ?: "claude")
    var useHeadroom by mutableStateOf(existingProject?.config?.agentLaunchConfig?.useHeadroom ?: true)

    val isValid: Boolean
        get() = name.isNotBlank() && baseRepositoryPath.isNotBlank()

    fun addSecretFile(fileName: String) {
        if (fileName.isNotBlank()) secretFiles = secretFiles + fileName
    }

    fun removeSecretFile(fileName: String) {
        secretFiles = secretFiles - fileName
    }

    fun save(): Project {
        val customCommandTemplate = customCommandTemplateText
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .takeIf { it.isNotEmpty() }

        val project = Project(
            id = existingId ?: UUID.randomUUID().toString(),
            name = name,
            config = ProjectConfig(
                baseRepositoryPath = baseRepositoryPath,
                worktreesRoot = worktreesRoot,
                secretFiles = secretFiles,
                terminalPreference = TerminalPreference(
                    mode = terminalSelectionMode,
                    emulator = terminalEmulator,
                    customCommandTemplate = customCommandTemplate,
                ),
                agentLaunchConfig = AgentLaunchConfig(agentCommand = agentCommand, useHeadroom = useHeadroom),
            ),
        )
        projectRepository.save(project)
        return project
    }
}
