package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.Project

interface ProjectRepository {
    fun list(): List<Project>
    fun save(project: Project)
    fun delete(id: String)
}
