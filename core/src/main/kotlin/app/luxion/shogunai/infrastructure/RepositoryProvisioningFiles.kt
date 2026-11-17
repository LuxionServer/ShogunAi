package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.EnvironmentDeclaration
import app.luxion.shogunai.domain.model.RepositoryConfigEdit
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest
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
        val config = readConfig(configPath)
        return RepositoryProvisioning(
            copyPatterns = config?.let { copyPatternsOf(it, configPath) }.orEmpty(),
            linkPatterns = config?.let { linkPatternsOf(it, configPath) }.orEmpty(),
            postCreateCommands = config?.let { postCreateCommandsOf(it, configPath) }.orEmpty(),
            worktreeIncludePatterns = readWorktreeInclude(root.resolve(RepositoryProvisioning.WORKTREE_INCLUDE_FILE_NAME)),
            providers = if (config != null) providersOf(config, configPath) else RepositoryProviders(),
            environment = if (config != null) environmentOf(config, configPath) else EnvironmentDeclaration(),
            pools = CustomPoolDeclarations.read(config, configPath, (config?.get(ENV_FIELD) as? JsonObject)?.keys.orEmpty()),
            overlapIgnore = config?.let { overlapIgnoreOf(it, configPath) }.orEmpty(),
        )
    }

    override fun fingerprint(repositoryPath: String): String {
        val path = configPathOf(repositoryPath)
        if (!path.exists()) return ABSENT_FINGERPRINT
        return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
    }

    override fun addPatterns(
        repositoryPath: String,
        copyPatterns: List<String>,
        linkPatterns: List<String>,
        expectedFingerprint: String?,
    ) {
        val configPath = configPathOf(repositoryPath)
        expectedFingerprint?.let { requireUnchanged(repositoryPath, configPath, it) }
        val config = readConfig(configPath) ?: JsonObject(emptyMap())
        val updated = LinkedHashMap(config)
        if (copyPatterns.isNotEmpty()) {
            updated[COPY_FIELD] = JsonArray((copyPatternsOf(config, configPath) + copyPatterns).distinct().map(::JsonPrimitive))
        }
        if (linkPatterns.isNotEmpty()) {
            updated[LINK_FIELD] = JsonArray((linkPatternsOf(config, configPath) + linkPatterns).distinct().map(::JsonPrimitive))
        }
        writeAtomically(configPath, render(JsonObject(updated)))
    }

    override fun preview(repositoryPath: String, edit: RepositoryConfigEdit): String {
        val configPath = configPathOf(repositoryPath)
        return render(applyEdit(readConfig(configPath) ?: JsonObject(emptyMap()), edit, configPath))
    }

    override fun write(repositoryPath: String, edit: RepositoryConfigEdit, expectedFingerprint: String) {
        val configPath = configPathOf(repositoryPath)
        requireUnchanged(repositoryPath, configPath, expectedFingerprint)
        val updated = applyEdit(readConfig(configPath) ?: JsonObject(emptyMap()), edit, configPath)
        if (!configPath.exists() && updated.isEmpty()) return
        writeAtomically(configPath, render(updated))
    }

    private fun configPathOf(repositoryPath: String): Path =
        Path.of(repositoryPath).resolve(RepositoryProvisioning.CONFIG_FILE_NAME)

    private fun requireUnchanged(repositoryPath: String, configPath: Path, expected: String) {
        if (fingerprint(repositoryPath) != expected) throw WorktreeError.RepositoryConfigChanged(configPath.toString())
    }

    private fun render(config: JsonObject): String = json.encodeToString(JsonElement.serializer(), config) + "\n"

    private fun applyEdit(config: JsonObject, edit: RepositoryConfigEdit, path: Path): JsonObject {
        val result = LinkedHashMap(config)
        if (edit.addCopyPatterns.isNotEmpty()) {
            val merged = (copyPatternsOf(config, path) + edit.addCopyPatterns).distinct()
            result[COPY_FIELD] = JsonArray(merged.map(::JsonPrimitive))
        }
        if (edit.addLinkPatterns.isNotEmpty()) {
            val merged = (linkPatternsOf(config, path) + edit.addLinkPatterns).distinct()
            result[LINK_FIELD] = JsonArray(merged.map(::JsonPrimitive))
        }
        result.putOrRemove(PROVIDERS_FIELD, editedProviders(config, edit.providers, path))
        result.putOrRemove(
            ENV_FIELD,
            edit.environment.takeIf { it.isNotEmpty() }
                ?.let { entries -> JsonObject(entries.associate { it.name to JsonPrimitive(it.template) }) },
        )
        result.putOrRemove(ENV_FILE_FIELD, edit.envFile?.let(::JsonPrimitive))
        return JsonObject(result)
    }

    private fun MutableMap<String, JsonElement>.putOrRemove(key: String, value: JsonElement?) {
        if (value == null) remove(key) else this[key] = value
    }

    private fun readConfig(path: Path): JsonObject? {
        if (!path.exists()) return null
        val text = path.readText()
        if (text.isBlank()) return JsonObject(emptyMap())
        val element = try {
            Json.parseToJsonElement(text)
        } catch (error: SerializationException) {
            throw WorktreeError.InvalidRepositoryConfig(path.toString(), firstLineOf(error))
        }
        return element as? JsonObject
            ?: throw WorktreeError.InvalidRepositoryConfig(path.toString(), "expected a JSON object")
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
        const val ABSENT_FINGERPRINT = "absent"
    }
}
