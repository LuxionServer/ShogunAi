package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.ClaudeSessionRegistry
import app.luxion.shogunai.domain.model.ClaudeSession
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText

class FileClaudeSessionRegistry(private val directory: String) : ClaudeSessionRegistry {

    override fun list(): List<ClaudeSession> {
        val files = runCatching { Path.of(directory).listDirectoryEntries("*$EXTENSION") }.getOrDefault(emptyList())
        return files.filter { runCatching { it.isRegularFile() }.getOrDefault(false) }.mapNotNull(::read)
    }

    private fun read(file: Path): ClaudeSession? {
        val entry = runCatching { LENIENT.decodeFromString(SessionFile.serializer(), file.readText()) }.getOrNull() ?: return null
        return ClaudeSession(
            pid = entry.pid ?: return null,
            cwd = entry.cwd?.takeIf { it.isNotBlank() } ?: return null,
            name = entry.name?.takeIf { it.isNotBlank() },
            startedAt = entry.startedAt,
        )
    }

    @Serializable
    private data class SessionFile(
        val pid: Long? = null,
        val cwd: String? = null,
        val name: String? = null,
        val startedAt: Long? = null,
    )

    private companion object {
        const val EXTENSION = ".json"
        val LENIENT = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }
}
