package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.ProjectRepository
import app.luxion.shogunai.domain.model.Project
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.readText

class JsonProjectRepository(
    private val storagePath: Path,
) : ProjectRepository {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    override fun list(): List<Project> = LocalFileLock.withLock(storagePath) { read() }

    override fun save(project: Project) = LocalFileLock.withLock(storagePath) {
        write(readKeepingUnreadable().filterNot { it.id == project.id } + project)
    }

    override fun delete(id: String) = LocalFileLock.withLock(storagePath) {
        write(readKeepingUnreadable().filterNot { it.id == id })
    }

    private fun read(): List<Project> {
        if (!storagePath.exists()) return emptyList()
        val text = try {
            storagePath.readText()
        } catch (error: IOException) {
            throw WorktreeError.LocalFileUnreadable(storagePath.toString(), error.message)
        }
        if (text.isBlank()) return emptyList()
        return try {
            json.decodeFromJsonElement(LegacyAgentJson.projects(json.parseToJsonElement(text)))
        } catch (error: IllegalArgumentException) {
            throw WorktreeError.LocalFileUnreadable(storagePath.toString(), firstLineOf(error))
        }
    }

    private fun readKeepingUnreadable(): List<Project> = try {
        read()
    } catch (_: WorktreeError.LocalFileUnreadable) {
        keepUnreadableCopy(storagePath)
        emptyList()
    }

    private fun write(projects: List<Project>) {
        Files.createDirectories(storagePath.parent)
        val tempFile = Files.createTempFile(storagePath.parent, "projects", ".json.tmp")
        Files.writeString(tempFile, json.encodeToString(projects))
        Files.move(tempFile, storagePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
