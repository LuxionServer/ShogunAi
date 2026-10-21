package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.AgentSessionStore
import app.luxion.shogunai.domain.model.AgentSessionRecord
import app.luxion.shogunai.domain.model.AgentState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

class FileAgentSessionStore : AgentSessionStore {

    override fun write(worktreePath: String, record: AgentSessionRecord) {
        val directory = sessionsDirectory(worktreePath) ?: return
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, ".${record.sessionId}", ".tmp")
        Files.writeString(temporary, encode(record))
        Files.move(temporary, directory.resolve(fileName(record.sessionId)), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    override fun remove(worktreePath: String, sessionId: String) {
        val directory = sessionsDirectory(worktreePath) ?: return
        Files.deleteIfExists(directory.resolve(fileName(sessionId)))
    }

    override fun list(worktreePath: String): List<AgentSessionRecord> {
        val directory = sessionsDirectory(worktreePath)?.takeIf { it.isDirectory() } ?: return emptyList()
        return directory.listDirectoryEntries("*$EXTENSION").mapNotNull { file -> runCatching { decode(file.readText()) }.getOrNull() }
    }

    private fun sessionsDirectory(worktreePath: String): Path? =
        gitDirectory(Path.of(worktreePath))?.resolve(FOLDER)?.resolve(SESSIONS)

    private fun gitDirectory(worktree: Path): Path? {
        val dotGit = worktree.resolve(".git")
        return when {
            dotGit.isDirectory() -> dotGit
            dotGit.isRegularFile() -> dotGit.readText().lineSequence()
                .firstOrNull { it.startsWith(GITDIR_PREFIX) }
                ?.removePrefix(GITDIR_PREFIX)?.trim()
                ?.let { worktree.resolve(it).normalize() }
                ?.takeIf { it.exists() }
            else -> null
        }
    }

    private fun fileName(sessionId: String) = sessionId.replace(UNSAFE, "_") + EXTENSION

    private fun encode(record: AgentSessionRecord): String = buildJsonObject {
        put("sessionId", record.sessionId)
        put("agent", record.agent)
        put("state", record.state.name.lowercase())
        put("updatedAt", record.updatedAt)
        put("transcriptPath", record.transcriptPath)
        put("pid", record.pid)
    }.toString()

    private fun decode(text: String): AgentSessionRecord? {
        val json = Json.parseToJsonElement(text) as? JsonObject ?: return null
        val state = json.string("state")?.let { name -> AgentState.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }
        return AgentSessionRecord(
            sessionId = json.string("sessionId") ?: return null,
            agent = json.string("agent") ?: return null,
            state = state ?: return null,
            updatedAt = (json["updatedAt"] as? JsonPrimitive)?.longOrNull ?: return null,
            transcriptPath = json.string("transcriptPath"),
            pid = (json["pid"] as? JsonPrimitive)?.longOrNull,
        )
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private companion object {
        const val FOLDER = "shogunai"
        const val SESSIONS = "agent-sessions"
        const val EXTENSION = ".json"
        const val GITDIR_PREFIX = "gitdir:"
        val UNSAFE = Regex("[^A-Za-z0-9_.-]")
    }
}
