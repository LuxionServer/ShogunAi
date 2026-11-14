package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.RepositoryTrust
import app.luxion.shogunai.domain.model.RepositoryApprovalSubject
import app.luxion.shogunai.domain.model.WorktreeError
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Path
import java.security.MessageDigest

class JsonRepositoryTrust(
    storagePath: Path,
) : RepositoryTrust {
    private val file = VersionedJsonFile(
        path = storagePath,
        serializer = Store.serializer(),
        json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        },
        empty = Store(),
        privateFile = true,
    )

    override fun isApproved(repository: String, subject: RepositoryApprovalSubject): Boolean =
        subject.isEmpty || read().approvals[keyOf(repository)]?.sha256 == digestOf(subject)

    override fun approve(repository: String, subject: RepositoryApprovalSubject) {
        val approval = Approval(digestOf(subject), subject.commands, subject.variables.toMap(), subject.envFile, subject.nodeInstall)
        file.update { it.copy(approvals = it.approvals + (keyOf(repository) to approval)) }
    }

    override fun revoke(repository: String) {
        file.update { it.copy(approvals = it.approvals - keyOf(repository)) }
    }

    private fun read(): Store = try {
        file.read()
    } catch (_: WorktreeError.LocalFileUnreadable) {
        Store()
    }

    private fun keyOf(repository: String): String {
        val path = Path.of(repository)
        return runCatching { path.toRealPath() }.getOrDefault(path.toAbsolutePath().normalize()).toString()
    }

    private fun digestOf(subject: RepositoryApprovalSubject): String {
        val canonical = if (subject.declaresEnvironment || subject.nodeInstall) canonicalOf(subject) else Json.encodeToString(subject.commands)
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun canonicalOf(subject: RepositoryApprovalSubject): String {
        val commands = JsonArray(subject.commands.map { command -> JsonArray(command.map(::JsonPrimitive)) })
        val variables = JsonArray(
            subject.variables.sortedBy { it.first }.map { (name, template) -> JsonArray(listOf(JsonPrimitive(name), JsonPrimitive(template))) },
        )
        val envFile: JsonElement = if (subject.envFile != null) JsonPrimitive(subject.envFile) else JsonNull
        val fields = linkedMapOf("commands" to commands, "env" to variables, "envFile" to envFile)
        if (subject.nodeInstall) fields["nodeInstall"] = JsonPrimitive(true)
        val canonical = JsonObject(fields)
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
        val nodeInstall: Boolean = false,
    )
}
