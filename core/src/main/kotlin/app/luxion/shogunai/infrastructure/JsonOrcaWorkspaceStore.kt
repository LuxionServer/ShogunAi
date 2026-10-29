package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.OrcaWorkspaceStore
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.readText

class JsonOrcaWorkspaceStore(
    private val storagePath: Path = Path.of(System.getProperty("user.home"), ".shogunai", "orca.json"),
) : OrcaWorkspaceStore {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    override fun workspacesRoot(): String? = read().workspacesRoot

    override fun remember(workspacesRoot: String) {
        LocalFileLock.withLock(storagePath) {
            val current = read()
            if (current.workspacesRoot != workspacesRoot) write(current.copy(workspacesRoot = workspacesRoot))
        }
    }

    override fun boardStatuses(): Map<String, String> = read().boardStatuses

    override fun rememberBoardStatuses(statuses: Map<String, String>) {
        LocalFileLock.withLock(storagePath) {
            val current = read()
            if (current.boardStatuses != statuses) write(current.copy(boardStatuses = statuses))
        }
    }

    private fun read(): Store {
        if (!storagePath.exists()) return Store()
        val text = runCatching { storagePath.readText() }.getOrDefault("")
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
        val tempFile = Files.createTempFile(storagePath.parent, "orca", ".json.tmp")
        Files.writeString(tempFile, json.encodeToString(Store.serializer(), store))
        Files.move(tempFile, storagePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    @Serializable
    private data class Store(val workspacesRoot: String? = null, val boardStatuses: Map<String, String> = emptyMap())
}
