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

    init {
        refresh()
    }

    fun refresh() {
        projects = projectRepository.list()
    }
}
