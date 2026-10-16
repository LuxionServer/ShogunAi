package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.model.Project

class FakeProjectRepository(
    initialProjects: List<Project> = emptyList(),
) : ProjectRepository {

    private val projects = initialProjects.toMutableList()

    override fun list(): List<Project> = projects.toList()

    override fun save(project: Project) {
        projects.removeAll { it.id == project.id }
        projects += project
    }

    override fun delete(id: String) {
        projects.removeAll { it.id == id }
    }
}
