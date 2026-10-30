package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.RepositoryTrust
import app.luxion.shogunai.domain.model.RepositoryApprovalSubject
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import kotlin.io.path.exists
import kotlin.io.path.readText

class JsonRepositoryTrust(
    private val storagePath: Path,
) : RepositoryTrust {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Synchronized
    override fun isApproved(repository: String, subject: RepositoryApprovalSubject): Boolean =
        subject.isEmpty || read().approvals[keyOf(repository)]?.sha256 == digestOf(subject)

    @Synchronized
    override fun approve(repository: String, subject: RepositoryApprovalSubject) {
        val current = read()
        val approval = Approval(digestOf(subject), subject.commands, subject.variables.toMap(), subject.envFile)
        write(current.copy(approvals = current.approvals + (keyOf(repository) to approval)))
    }

    @Synchronized
    override fun revoke(repository: String) {
        val current = read()
        write(current.copy(approvals = current.approvals - keyOf(repository)))
    }

    private fun read(): Store {
        if (!storagePath.exists()) return Store()
        val text = storagePath.readText()
        if (text.isBlank()) return Store()
        return try {
            json.decodeFromString<Store>(text)
        } catch (_: SerializationException) {
            Store()
        } catch (_: IllegalArgumentException) {
            Store()
        }
    }

    private fun write(store: Store) {
        Files.createDirectories(storagePath.parent)
        val tempFile = Files.createTempFile(storagePath.parent, "trust", ".json.tmp")
        runCatching { Files.setPosixFilePermissions(tempFile, PosixFilePermissions.fromString("rw-------")) }
        Files.writeString(tempFile, json.encodeToString(store))
        Files.move(tempFile, storagePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun keyOf(repository: String): String {
        val path = Path.of(repository)
        return runCatching { path.toRealPath() }.getOrDefault(path.toAbsolutePath().normalize()).toString()
    }

    private fun digestOf(subject: RepositoryApprovalSubject): String {
        val canonical = if (subject.declaresEnvironment) canonicalOf(subject) else Json.encodeToString(subject.commands)
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun canonicalOf(subject: RepositoryApprovalSubject): String {
        val commands = JsonArray(subject.commands.map { command -> JsonArray(command.map(::JsonPrimitive)) })
        val variables = JsonArray(
            subject.variables.sortedBy { it.first }.map { (name, template) -> JsonArray(listOf(JsonPrimitive(name), JsonPrimitive(template))) },
        )
        val envFile: JsonElement = if (subject.envFile != null) JsonPrimitive(subject.envFile) else JsonNull
        val canonical = JsonObject(linkedMapOf("commands" to commands, "env" to variables, "envFile" to envFile))
        return Json.encodeToString(JsonElement.serializer(), canonical)
    }

    @Serializable
    internal data class Store(val approvals: Map<String, Approval> = emptyMap())

    @Serializable
    internal data class Approval(
        val sha256: String,
        val commands: List<List<String>>,
        val env: Map<String, String> = emptyMap(),
        val envFile: String? = null,
    )
}
