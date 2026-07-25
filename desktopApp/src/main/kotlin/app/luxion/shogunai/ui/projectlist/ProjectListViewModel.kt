package app.luxion.shogunai.ui.projectlist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.model.Project

class ProjectListViewModel(private val projectRepository: ProjectRepository) : ViewModel() {
    var projects by mutableStateOf<List<Project>>(emptyList())
        private set
    var searchQuery by mutableStateOf("")
    var sortOrder by mutableStateOf(ProjectSortOrder.NAME_ASC)

    val visibleProjects: List<Project>
        get() = projects
            .filter { it.name.contains(searchQuery, ignoreCase = true) }
            .let { filtered ->
                when (sortOrder) {
                    ProjectSortOrder.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
                    ProjectSortOrder.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
                }
            }

    init {
        refresh()
    }

    fun refresh() {
        projects = projectRepository.list()
    }

    fun deleteProject(project: Project) {
        projectRepository.delete(project.id)
        refresh()
    }
}

enum class ProjectSortOrder { NAME_ASC, NAME_DESC }
