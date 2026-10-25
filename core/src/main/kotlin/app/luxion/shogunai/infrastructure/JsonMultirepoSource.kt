package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.MultirepoSource
import app.luxion.shogunai.domain.model.MULTIREPO_FILE_NAME
import app.luxion.shogunai.domain.model.MultirepoDeclaration
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

class JsonMultirepoSource(
    private val registryPath: Path = Path.of(System.getProperty("user.home"), ".shogunai", "multirepos.json"),
) : MultirepoSource {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = false }

    override fun read(folder: String): MultirepoDeclaration? {
        if (folder.isBlank() || !Path.of(folder).isAbsolute) return null
        val file = declarationFile(folder)
        if (!file.isRegularFile()) return null
        val text = try {
            file.readText()
        } catch (error: IOException) {
            throw WorktreeError.InvalidMultirepo(file.toString(), error.message ?: "unreadable")
        }
        return try {
            json.decodeFromString<MultirepoDeclaration>(text)
        } catch (error: SerializationException) {
            throw WorktreeError.InvalidMultirepo(file.toString(), firstLine(error))
        } catch (error: IllegalArgumentException) {
            throw WorktreeError.InvalidMultirepo(file.toString(), firstLine(error))
        }
    }

    override fun write(folder: String, declaration: MultirepoDeclaration) {
        replace(declarationFile(folder), json.encodeToString(MultirepoDeclaration.serializer(), declaration) + "\n")
    }

    override fun delete(folder: String) {
        Files.deleteIfExists(declarationFile(folder))
    }

    override fun knownFolders(): List<String> =
        LocalFileLock.withLock(registryPath) { readRegistry() }.map { Path.of(it).parent?.toString() ?: it }

    override fun remember(folder: String) = LocalFileLock.withLock(registryPath) {
        val path = declarationFile(folder).toString()
        val known = readRegistry()
        if (path !in known) writeRegistry(known + path)
    }

    override fun forget(folder: String) = LocalFileLock.withLock(registryPath) {
        val path = declarationFile(folder).toString()
        val known = readRegistry()
        if (path in known) writeRegistry(known - path)
    }

    override fun forgetMissing(): List<String> = LocalFileLock.withLock(registryPath) {
        val known = readRegistry()
        val missing = known.filter { path -> Path.of(path).parent?.exists() != true }
        if (missing.isNotEmpty()) writeRegistry(known - missing.toSet())
        missing.map { Path.of(it).parent?.toString() ?: it }
    }

    private fun readRegistry(): List<String> {
        if (!registryPath.exists()) return emptyList()
        val text = runCatching { registryPath.readText() }.getOrNull()?.takeIf { it.isNotBlank() } ?: return emptyList()
        return runCatching { json.decodeFromString<Registry>(text).multirepos }.getOrDefault(emptyList())
    }

    private fun writeRegistry(paths: List<String>) =
        replace(registryPath, json.encodeToString(Registry.serializer(), Registry(paths)) + "\n")

    private fun replace(target: Path, text: String) {
        Files.createDirectories(target.parent)
        val tempFile = Files.createTempFile(target.parent, target.fileName.toString(), ".tmp")
        Files.writeString(tempFile, text)
        Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun declarationFile(folder: String): Path = Path.of(folder).resolve(MULTIREPO_FILE_NAME).normalize()

    private fun firstLine(error: Exception): String = error.message?.lineSequence()?.firstOrNull().orEmpty().ifBlank { "invalid JSON" }

    @Serializable
    private data class Registry(val multirepos: List<String> = emptyList())
}
