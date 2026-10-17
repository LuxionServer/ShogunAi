package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.model.Project
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.readText

class JsonProjectRepository(
    private val storagePath: Path = Path.of(System.getProperty("user.home"), ".shogunai", "projects.json"),
) : ProjectRepository {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    override fun list(): List<Project> {
        if (!storagePath.exists()) return emptyList()
        val text = storagePath.readText()
        if (text.isBlank()) return emptyList()
        return json.decodeFromString(text)
    }

    override fun save(project: Project) {
        val updated = list().filterNot { it.id == project.id } + project
        write(updated)
    }

    override fun delete(id: String) {
        write(list().filterNot { it.id == id })
    }

    private fun write(projects: List<Project>) {
        Files.createDirectories(storagePath.parent)
        val tempFile = Files.createTempFile(storagePath.parent, "projects", ".json.tmp")
        Files.writeString(tempFile, json.encodeToString(projects))
        Files.move(tempFile, storagePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
