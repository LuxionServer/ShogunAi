package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.readText

class RepositoryProvisioningFiles : RepositoryProvisioningSource {

    private val json = Json { prettyPrint = true }

    override fun read(repositoryPath: String): RepositoryProvisioning {
        val root = Path.of(repositoryPath)
        val configPath = root.resolve(RepositoryProvisioning.CONFIG_FILE_NAME)
        return RepositoryProvisioning(
            copyPatterns = readConfig(configPath)?.let { copyPatternsOf(it, configPath) }.orEmpty(),
            worktreeIncludePatterns = readWorktreeInclude(root.resolve(RepositoryProvisioning.WORKTREE_INCLUDE_FILE_NAME)),
        )
    }

    override fun addCopyPatterns(repositoryPath: String, patterns: List<String>) {
        val configPath = Path.of(repositoryPath).resolve(RepositoryProvisioning.CONFIG_FILE_NAME)
        val config = readConfig(configPath) ?: JsonObject(emptyMap())
        val existing = copyPatternsOf(config, configPath)
        val merged = (existing + patterns).distinct()
        val updated = JsonObject(config + (COPY_FIELD to JsonArray(merged.map(::JsonPrimitive))))
        writeAtomically(configPath, json.encodeToString(JsonElement.serializer(), updated) + "\n")
    }

    private fun readConfig(path: Path): JsonObject? {
        if (!path.exists()) return null
        val text = path.readText()
        if (text.isBlank()) return JsonObject(emptyMap())
        val element = try {
            Json.parseToJsonElement(text)
        } catch (error: SerializationException) {
            throw WorktreeError.InvalidRepositoryConfig(path.toString(), error.message?.lineSequence()?.firstOrNull())
        }
        return element as? JsonObject
            ?: throw WorktreeError.InvalidRepositoryConfig(path.toString(), "expected a JSON object")
    }

    private fun copyPatternsOf(config: JsonObject, path: Path): List<String> {
        val field = config[COPY_FIELD] ?: return emptyList()
        if (field is JsonNull) return emptyList()
        val invalid = { WorktreeError.InvalidRepositoryConfig(path.toString(), "\"$COPY_FIELD\" must be a list of strings") }
        val array = field as? JsonArray ?: throw invalid()
        return array.map { element ->
            val primitive = element as? JsonPrimitive
            if (primitive == null || !primitive.isString) throw invalid()
            primitive.content
        }.filter { it.isNotBlank() }
    }

    private fun readWorktreeInclude(path: Path): List<String> {
        if (!path.exists()) return emptyList()
        return path.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
    }

    private fun writeAtomically(path: Path, content: String) {
        val tempFile = Files.createTempFile(path.parent, ".shogunai", ".json.tmp")
        Files.writeString(tempFile, content)
        Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private companion object {
        const val COPY_FIELD = "copy"
    }
}
