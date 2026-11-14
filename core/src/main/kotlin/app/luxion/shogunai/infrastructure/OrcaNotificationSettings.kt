package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.model.AgentState
import app.luxion.shogunai.domain.model.HostOs
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.io.File
import java.net.URI

class OrcaNotificationSettings(
    private val executor: ShellCommandExecutor,
    private val environmentVariable: (String) -> String?,
    private val userHome: String,
    private val os: HostOs,
) {

    suspend fun notifiedStates(): Set<AgentState> {
        val dataDirectory = dataDirectory()
        if (!dataDirectory.isDirectory) return emptySet()
        val database = File(File(File(dataDirectory, "profiles"), activeProfile(dataDirectory)), "profile-state.db")
        if (!database.isFile) return DEFAULT_STATES
        val payload = settingsPayload(database) ?: return DEFAULT_STATES
        val notifications = parsedObject(payload)?.get("notifications") as? JsonObject ?: return DEFAULT_STATES
        return statesOf(notifications)
    }

    private fun statesOf(notifications: JsonObject): Set<AgentState> {
        if (notifications.flag("enabled") == false) return emptySet()
        val taskComplete = notifications.flag("agentTaskComplete") ?: true
        val needsInput = notifications.flag("agentNeedsInput") ?: taskComplete
        return setOfNotNull(AgentState.BLOCKED.takeIf { needsInput }, AgentState.DONE.takeIf { taskComplete })
    }

    private suspend fun settingsPayload(database: File): String? {
        val uri = URI("file", null, database.absoluteFile.toURI().path, "mode=ro&immutable=1", null).toASCIIString()
        val command = listOf("sqlite3", "-readonly", uri, "select payload from profile_state_documents where domain = 'settings'")
        val result = try {
            executor.executeWithin(command, CommandTimeouts.LISTING)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return null
        }
        return result.stdout.trim().takeIf { result.isSuccess && it.isNotEmpty() }
    }

    private fun activeProfile(dataDirectory: File): String {
        val indexFile = File(dataDirectory, "orca-profile-index.json")
        val index = if (indexFile.isFile) parsedObject(indexFile.readText()) else null
        val id = ((index?.get("activeProfileId") as? JsonPrimitive)?.takeIf { it.isString } ?: return DEFAULT_PROFILE).content
        return if (id.isBlank() || '/' in id || '\\' in id) DEFAULT_PROFILE else id
    }

    private fun dataDirectory(): File = when (os) {
        HostOs.MACOS -> File(userHome, "Library/Application Support/orca")
        HostOs.WINDOWS -> File(environmentVariable("APPDATA")?.takeIf { it.isNotBlank() } ?: File(userHome, "AppData/Roaming").path, "orca")
        HostOs.LINUX -> File(environmentVariable("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() } ?: File(userHome, ".config").path, "orca")
    }

    private fun parsedObject(text: String): JsonObject? = try {
        Json.parseToJsonElement(text) as? JsonObject
    } catch (_: SerializationException) {
        null
    }

    private fun JsonObject.flag(name: String): Boolean? = (this[name] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
}

private const val DEFAULT_PROFILE = "local-default"
private val DEFAULT_STATES = setOf(AgentState.BLOCKED, AgentState.DONE)
