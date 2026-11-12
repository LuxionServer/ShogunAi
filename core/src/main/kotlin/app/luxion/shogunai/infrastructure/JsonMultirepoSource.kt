package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.MultirepoSource
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.MULTIREPO_FILE_NAME
import app.luxion.shogunai.domain.model.MultirepoDeclaration
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

class JsonMultirepoSource(
    registryPath: Path,
) : MultirepoSource {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = false }

    private val registryFile = VersionedJsonFile(
        path = registryPath,
        serializer = Registry.serializer(),
        json = json,
        empty = Registry(),
        privateFile = true,
        decode = ::registryOf,
    )

    override fun read(folder: String): MultirepoDeclaration? {
        if (folder.isBlank() || !Path.of(folder).isAbsolute) return null
        val file = declarationFile(folder)
        if (!file.isRegularFile()) return null
        val text = try {
            file.readText()
        } catch (error: IOException) {
            throw WorktreeError.InvalidMultirepo(file.toString(), readFailureReason(error))
        }
        return try {
            json.decodeFromString<MultirepoDeclaration>(text)
        } catch (error: IllegalArgumentException) {
            throw WorktreeError.InvalidMultirepo(file.toString(), firstLineOf(error))
        }
    }

    override fun write(folder: String, declaration: MultirepoDeclaration) {
        writeAtomically(declarationFile(folder), json.encodeToString(MultirepoDeclaration.serializer(), declaration))
    }

    override fun delete(folder: String) {
        Files.deleteIfExists(declarationFile(folder))
    }

    override fun knownFolders(): List<String> =
        readRegistry().multirepos.map(::folderOf)

    override fun remember(folder: String) = updateRegistry { registry ->
        val path = declarationKey(folder)
        if (path in registry.multirepos) registry else registry.copy(multirepos = registry.multirepos + path)
    }

    override fun forget(folder: String) = updateRegistry { registry ->
        val path = declarationKey(folder)
        registry.copy(multirepos = registry.multirepos - path, local = registry.local - path)
    }

    private fun folderOf(declaration: String): String {
        val parent = Path.of(declaration).parent
        return if (parent != null) parent.toString() else declaration
    }

    override fun isApproved(folder: String, subject: String): Boolean =
        localOf(folder)?.approved == digestOf(subject)

    override fun approve(folder: String, subject: String) = updateLocal(folder) { it.copy(approved = digestOf(subject)) }

    override fun agent(folder: String): AgentLaunchConfig? = localOf(folder)?.agent

    override fun setAgent(folder: String, agent: AgentLaunchConfig) = updateLocal(folder) { it.copy(agent = agent) }

    private fun localOf(folder: String): LocalSettings? =
        readRegistry().local[declarationKey(folder)]

    private fun updateLocal(folder: String, change: (LocalSettings) -> LocalSettings) = updateRegistry { registry ->
        val path = declarationKey(folder)
        registry.copy(local = registry.local + (path to change(registry.local[path] ?: LocalSettings())))
    }

    private fun updateRegistry(change: (Registry) -> Registry) {
        registryFile.update(change)
    }

    private fun readRegistry(): Registry = try {
        registryFile.read()
    } catch (_: WorktreeError.LocalFileUnreadable) {
        Registry()
    }

    private fun registryOf(element: JsonElement): Registry {
        val root = element.jsonObject
        return Registry(
            multirepos = decodeOrNull<List<String>>(root["multirepos"]).orEmpty(),
            local = (root["local"] as? JsonObject).orEmpty().mapNotNull { (path, entry) ->
                decodeOrNull<LocalSettings>(entry)?.let { path to it }
            }.toMap(),
        )
    }

    private inline fun <reified T> decodeOrNull(element: JsonElement?): T? =
        element?.let { runCatching { json.decodeFromJsonElement<T>(it) }.getOrNull() }

    private fun declarationFile(folder: String): Path = Path.of(folder).resolve(MULTIREPO_FILE_NAME).normalize()

    private fun declarationKey(folder: String): String = declarationFile(folder).toString()

    private fun digestOf(subject: String): String =
        MessageDigest.getInstance("SHA-256").digest(subject.toByteArray()).joinToString("") { "%02x".format(it) }

    @Serializable
    internal data class Registry(
        val multirepos: List<String> = emptyList(),
        val local: Map<String, LocalSettings> = emptyMap(),
    )

    @Serializable
    internal data class LocalSettings(val approved: String? = null, val agent: AgentLaunchConfig? = null)
}
