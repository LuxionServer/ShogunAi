package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.ComposeProjectRegistry
import app.luxion.shogunai.domain.io.ComposeRegistryEntry
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.readText

class JsonComposeProjectRegistry(
    private val storagePath: Path = Path.of(System.getProperty("user.home"), ".shogunai", "compose-projects.json"),
) : ComposeProjectRegistry {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun entries(repositoryPath: String): List<ComposeRegistryEntry> =
        read().repositories[keyOf(repositoryPath)].orEmpty().map { ComposeRegistryEntry(it.project, it.worktreePath) }

    override fun record(repositoryPath: String, entry: ComposeRegistryEntry) {
        val stored = StoredEntry(entry.project, entry.worktreePath)
        update { repositories ->
            val key = keyOf(repositoryPath)
            val current = repositories[key].orEmpty()
            if (stored in current) return@update repositories
            repositories + (key to current.filterNot { it.project == stored.project } + stored)
        }
    }

    override fun drop(repositoryPath: String, projects: Set<String>) {
        if (projects.isEmpty()) return
        update { repositories ->
            val key = keyOf(repositoryPath)
            val kept = repositories[key].orEmpty().filterNot { it.project in projects }
            if (kept.isEmpty()) repositories - key else repositories + (key to kept)
        }
    }

    override fun recordedElsewhere(repositoryPath: String): Set<String> {
        val key = keyOf(repositoryPath)
        return read().repositories.filterKeys { it != key }.values.flatten().map { it.project }.toSet()
    }

    private fun update(change: (Map<String, List<StoredEntry>>) -> Map<String, List<StoredEntry>>) {
        LocalFileLock.withLock(storagePath) {
            val current = read()
            val changed = change(current.repositories)
            if (changed != current.repositories) write(current.copy(repositories = changed))
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
        val tempFile = Files.createTempFile(storagePath.parent, "compose-projects", ".json.tmp")
        Files.writeString(tempFile, json.encodeToString(store))
        Files.move(tempFile, storagePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun keyOf(repository: String): String {
        val path = Path.of(repository)
        return runCatching { path.toRealPath() }.getOrDefault(path.toAbsolutePath().normalize()).toString()
    }

    @Serializable
    private data class Store(val repositories: Map<String, List<StoredEntry>> = emptyMap())

    @Serializable
    private data class StoredEntry(val project: String, val worktreePath: String)
}
