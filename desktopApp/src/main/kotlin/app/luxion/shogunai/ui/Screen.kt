package app.luxion.shogunai.ui

import app.luxion.shogunai.domain.model.Project

sealed class Screen {
    data object ProjectList : Screen()
    data class ProjectConfigForm(val existingProject: Project? = null) : Screen()
    data class WorktreeManagement(val project: Project) : Screen()
}
