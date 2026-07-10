package app.luxion.shogunai.ui.projectconfig

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import app.luxion.shogunai.domain.io.ProjectRepository
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

    val isValid: Boolean
        get() = name.isNotBlank() && baseRepositoryPath.isNotBlank()

    fun addSecretFile(fileName: String) {
        if (fileName.isNotBlank()) secretFiles = secretFiles + fileName
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
            ),
        )
        projectRepository.save(project)
        return project
    }
}
