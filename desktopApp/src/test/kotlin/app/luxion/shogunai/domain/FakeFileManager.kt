package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.FileManager
import java.io.IOException

class FakeFileManager(
    existing: Collection<String> = emptyList(),
    private val failCopyFor: Set<String> = emptySet(),
    private val sizes: Map<String, Long> = emptyMap(),
) : FileManager {

    private val paths = existing.toMutableSet()
    val copied = mutableListOf<Pair<String, String>>()
    val sizeRequests = mutableListOf<Pair<String, Set<String>>>()

    override fun directorySize(path: String, excluding: Set<String>): Long {
        sizeRequests += path to excluding
        return sizes[path] ?: 0L
    }

    override fun exists(path: String): Boolean = path in paths

    override fun copy(source: String, destination: String) {
        if (source in failCopyFor) throw IOException("copia fallida de $source")
        copied += source to destination
        paths += destination
    }
}
