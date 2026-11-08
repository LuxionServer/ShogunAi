package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.DerivedDataSizeStore
import app.luxion.shogunai.domain.model.RememberedSize
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import kotlin.io.path.exists
import kotlin.io.path.readText

class JsonDerivedDataSizeStore(
    private val storagePath: Path,
) : DerivedDataSizeStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Synchronized
    override fun load(): Map<String, RememberedSize> {
        if (!storagePath.exists()) return emptyMap()
        val store = runCatching { json.decodeFromString<Store>(storagePath.readText()) }.getOrNull() ?: return emptyMap()
        return store.folders.mapNotNull { (path, entry) ->
            val lastAccessed = runCatching { Instant.parse(entry.lastAccessed) }.getOrNull() ?: return@mapNotNull null
            path to RememberedSize(entry.sizeBytes, lastAccessed, entry.packagesBytes)
        }.toMap()
    }

    @Synchronized
    override fun save(sizes: Map<String, RememberedSize>) {
        val store = Store(sizes.mapValues { (_, size) -> Entry(size.sizeBytes, size.lastAccessed.toString(), size.packagesBytes) })
        Files.createDirectories(storagePath.parent)
        val tempFile = Files.createTempFile(storagePath.parent, "derived-data-sizes", ".json.tmp")
        Files.writeString(tempFile, json.encodeToString(store))
        Files.move(tempFile, storagePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    @Serializable
    internal data class Store(val folders: Map<String, Entry> = emptyMap())

    @Serializable
    internal data class Entry(val sizeBytes: Long, val lastAccessed: String, val packagesBytes: Long? = null)
}
