package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.PendingNotificationStore
import app.luxion.shogunai.domain.model.NotifiedWorktree
import app.luxion.shogunai.domain.model.PendingSession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.nio.file.Path

class JsonPendingNotificationStore(path: Path) : PendingNotificationStore {
    private val file = VersionedJsonFile(path, JsonElement.serializer(), Json, JsonObject(emptyMap()))

    override fun <T> update(block: (List<PendingSession>) -> Pair<List<PendingSession>, T>): T {
        val results = mutableListOf<T>()
        file.update { stored ->
            val (sessions, result) = block(sessionsOf(stored))
            results += result
            buildJsonObject { put(SESSIONS, buildJsonArray { sessions.forEach { add(jsonOf(it)) } }) }
        }
        return results.single()
    }

    private fun sessionsOf(stored: JsonElement): List<PendingSession> =
        ((stored as? JsonObject)?.get(SESSIONS) as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::sessionOf) }

    private fun sessionOf(json: JsonObject): PendingSession? {
        val worktree = NotifiedWorktree(
            projectId = json.text("projectId") ?: return null,
            projectName = json.text("projectName") ?: return null,
            worktreePath = json.text("worktreePath") ?: return null,
            worktreeName = json.text("worktreeName") ?: return null,
        )
        val since = (json["sinceMillis"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull ?: return null
        return PendingSession(json.text("sessionId") ?: return null, worktree, since)
    }

    private fun jsonOf(session: PendingSession): JsonObject = buildJsonObject {
        put("sessionId", session.sessionId)
        put("projectId", session.worktree.projectId)
        put("projectName", session.worktree.projectName)
        put("worktreePath", session.worktree.worktreePath)
        put("worktreeName", session.worktree.worktreeName)
        put("sinceMillis", session.sinceMillis)
    }

    private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private companion object {
        const val SESSIONS = "sessions"
    }
}
