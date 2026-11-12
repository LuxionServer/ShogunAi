package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.model.Project
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.nio.file.Path

class JsonProjectRepository(
    storagePath: Path,
) : ProjectRepository {

    private val file = VersionedJsonFile(
        path = storagePath,
        serializer = ListSerializer(Project.serializer()),
        json = Json { prettyPrint = true; ignoreUnknownKeys = true },
        empty = emptyList(),
        schema = JsonSchema(normalize = LegacyAgentJson::projects),
    )

    override fun list(): List<Project> = file.read()

    override fun save(project: Project) {
        file.update { projects -> projects.filterNot { it.id == project.id } + project }
    }

    override fun delete(id: String) {
        file.update { projects -> projects.filterNot { it.id == id } }
    }
}
