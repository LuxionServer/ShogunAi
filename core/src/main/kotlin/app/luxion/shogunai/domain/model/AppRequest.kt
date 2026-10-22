package app.luxion.shogunai.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AppRequest(
    val version: Int = CURRENT_VERSION,
    val createdAt: Long,
    val action: String,
    val projectId: String? = null,
    val settings: Boolean = false,
) {
    val isUnderstood: Boolean
        get() = version == CURRENT_VERSION && when (action) {
            OPEN_APP -> true
            OPEN_PROJECT -> projectId != null
            else -> false
        }

    fun toJson(): String = json.encodeToString(this)

    companion object {
        const val CURRENT_VERSION = 1
        const val OPEN_APP = "open-app"
        const val OPEN_PROJECT = "open-project"
        const val MAX_AGE_MILLIS = 60_000L

        private val json = Json { ignoreUnknownKeys = true }

        fun openApp(createdAt: Long) = AppRequest(createdAt = createdAt, action = OPEN_APP)

        fun openProject(createdAt: Long, projectId: String, settings: Boolean) =
            AppRequest(createdAt = createdAt, action = OPEN_PROJECT, projectId = projectId, settings = settings)

        fun parse(text: String): AppRequest? = try {
            json.decodeFromString<AppRequest>(text).takeIf { it.isUnderstood }
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
