package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.MultirepoSource
import app.luxion.shogunai.domain.model.AgentLaunchConfig
import app.luxion.shogunai.domain.model.MULTIREPO_FILE_NAME
import app.luxion.shogunai.domain.model.MultirepoDeclaration
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
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
        LocalFileLock.withLock(registryPath) { readRegistry() }.multirepos.map { Path.of(it).parent?.toString() ?: it }

    override fun remember(folder: String) = updateRegistry { registry ->
        val path = declarationKey(folder)
        if (path in registry.multirepos) registry else registry.copy(multirepos = registry.multirepos + path)
    }

    override fun forget(folder: String) = updateRegistry { registry ->
        val path = declarationKey(folder)
        registry.copy(multirepos = registry.multirepos - path, local = registry.local - path)
    }

    override fun isApproved(folder: String, subject: String): Boolean =
        localOf(folder)?.approved == digestOf(subject)

    override fun approve(folder: String, subject: String) = updateLocal(folder) { it.copy(approved = digestOf(subject)) }

    override fun agent(folder: String): AgentLaunchConfig? = localOf(folder)?.agent

    override fun setAgent(folder: String, agent: AgentLaunchConfig) = updateLocal(folder) { it.copy(agent = agent) }

    private fun localOf(folder: String): LocalSettings? =
        LocalFileLock.withLock(registryPath) { readRegistry() }.local[declarationKey(folder)]

    private fun updateLocal(folder: String, change: (LocalSettings) -> LocalSettings) = updateRegistry { registry ->
        val path = declarationKey(folder)
        registry.copy(local = registry.local + (path to change(registry.local[path] ?: LocalSettings())))
    }

    private fun updateRegistry(change: (Registry) -> Registry) = LocalFileLock.withLock(registryPath) {
        val current = readRegistry()
        val updated = change(current)
        if (updated != current) writeRegistry(updated)
    }

    private fun readRegistry(): Registry {
        if (!registryPath.exists()) return Registry()
        val text = runCatching { registryPath.readText() }.getOrNull()?.takeIf { it.isNotBlank() } ?: return Registry()
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return Registry()
        return Registry(
            multirepos = decodeOrNull<List<String>>(root["multirepos"]).orEmpty(),
            local = (root["local"] as? JsonObject).orEmpty().mapNotNull { (path, entry) ->
                decodeOrNull<LocalSettings>(entry)?.let { path to it }
            }.toMap(),
        )
    }

    private inline fun <reified T> decodeOrNull(element: JsonElement?): T? =
        element?.let { runCatching { json.decodeFromJsonElement<T>(it) }.getOrNull() }

    private fun writeRegistry(registry: Registry) =
        replace(registryPath, json.encodeToString(Registry.serializer(), registry) + "\n", privateFile = true)

    private fun replace(target: Path, text: String, privateFile: Boolean = false) {
        Files.createDirectories(target.parent)
        val tempFile = Files.createTempFile(target.parent, target.fileName.toString(), ".tmp")
        if (privateFile) runCatching { Files.setPosixFilePermissions(tempFile, PosixFilePermissions.fromString("rw-------")) }
        Files.writeString(tempFile, text)
        Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun declarationFile(folder: String): Path = Path.of(folder).resolve(MULTIREPO_FILE_NAME).normalize()

    private fun declarationKey(folder: String): String = declarationFile(folder).toString()

    private fun digestOf(subject: String): String =
        MessageDigest.getInstance("SHA-256").digest(subject.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun firstLine(error: Exception): String = error.message?.lineSequence()?.firstOrNull().orEmpty().ifBlank { "invalid JSON" }

    @Serializable
    private data class Registry(
        val multirepos: List<String> = emptyList(),
        val local: Map<String, LocalSettings> = emptyMap(),
    )

    @Serializable
    private data class LocalSettings(val approved: String? = null, val agent: AgentLaunchConfig? = null)
}
