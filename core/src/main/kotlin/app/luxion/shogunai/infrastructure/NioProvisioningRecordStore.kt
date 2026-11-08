package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.ProvisioningRecordStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class NioProvisioningRecordStore : ProvisioningRecordStore {

    private val json = Json { prettyPrint = true }

    override fun read(worktreePath: String): Map<String, List<String>> = runCatching {
        val file = recordFile(worktreePath) ?: return emptyMap()
        if (!Files.isRegularFile(file)) return emptyMap()
        val folders = (Json.parseToJsonElement(Files.readString(file)) as? JsonObject)?.get(LOCAL_FOLDERS_FIELD) as? JsonObject
            ?: return emptyMap()
        folders.mapNotNull { (folder, files) ->
            (files as? JsonArray)?.let { array -> folder to array.mapNotNull { (it as? JsonPrimitive)?.stringOrNull() } }
        }.toMap()
    }.getOrDefault(emptyMap())

    override fun write(worktreePath: String, localFolders: Map<String, List<String>>) {
        val file = recordFile(worktreePath)
        if (localFolders.isEmpty()) {
            file?.let(Files::deleteIfExists)
            return
        }
        if (file == null) throw IOException("$worktreePath has no Git admin folder")
        val content = JsonObject(
            mapOf(LOCAL_FOLDERS_FIELD to JsonObject(localFolders.mapValues { (_, files) -> JsonArray(files.map(::JsonPrimitive)) })),
        )
        val tempFile = Files.createTempFile(file.parent, ".${file.fileName}", ".tmp")
        Files.writeString(tempFile, json.encodeToString(JsonElement.serializer(), content) + "\n")
        Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun recordFile(worktreePath: String): Path? {
        val worktree = Path.of(worktreePath)
        val dotGit = worktree.resolve(".git")
        if (!Files.isRegularFile(dotGit)) return null
        val gitDir = Files.readAllLines(dotGit).firstOrNull { it.startsWith(GITDIR_PREFIX) }
            ?.let { it.removePrefix(GITDIR_PREFIX).trim().ifEmpty { null } }
            ?: return null
        val adminDirectory = worktree.resolve(gitDir).normalize()
        return adminDirectory.takeIf { Files.isDirectory(it) }?.resolve(RECORD_FILE_NAME)
    }

    private companion object {
        const val RECORD_FILE_NAME = "shogunai-provisioning.json"
        const val LOCAL_FOLDERS_FIELD = "localFolders"
        const val GITDIR_PREFIX = "gitdir:"
    }
}
