package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.MultirepoSource
import app.luxion.shogunai.domain.model.MULTIREPO_FILE_NAME
import app.luxion.shogunai.domain.model.MultirepoDeclaration
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
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
            json.decodeFromJsonElement(MultirepoDeclaration.serializer(), upToDate(json.parseToJsonElement(text)))
        } catch (error: SerializationException) {
            throw WorktreeError.InvalidMultirepo(file.toString(), firstLine(error))
        } catch (error: IllegalArgumentException) {
            throw WorktreeError.InvalidMultirepo(file.toString(), firstLine(error))
        }
    }

    override fun write(folder: String, declaration: MultirepoDeclaration) {
        val fields = json.encodeToJsonElement(MultirepoDeclaration.serializer(), declaration).jsonObject
        val versioned = JsonObject(mapOf(VERSION to JsonPrimitive(FORMAT_VERSION)) + fields)
        replace(declarationFile(folder), json.encodeToString(JsonElement.serializer(), versioned) + "\n")
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

    private fun upToDate(element: JsonElement): JsonElement =
        if (element is JsonObject && VERSION !in element) LegacyAgentJson.legacyMultirepoDeclaration(element) else element

    private fun declarationFile(folder: String): Path = Path.of(folder).resolve(MULTIREPO_FILE_NAME).normalize()

    private fun firstLine(error: Exception): String = error.message?.lineSequence()?.firstOrNull().orEmpty().ifBlank { "invalid JSON" }

    private companion object {
        const val VERSION = "version"
        const val FORMAT_VERSION = 2
    }

    @Serializable
    private data class Registry(val multirepos: List<String> = emptyList())
}
