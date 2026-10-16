package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.FileManager
import java.io.IOException

class FakeFileManager(
    existing: Collection<String> = emptyList(),
    private val failCopyFor: Set<String> = emptySet(),
    private val sizes: Map<String, Long> = emptyMap(),
    private val directories: Set<String> = emptySet(),
    private val properties: Map<String, Map<String, String>> = emptyMap(),
) : FileManager {

    override fun readProperties(path: String): Map<String, String> = properties[path].orEmpty()

    val writtenTexts = mutableMapOf<String, String>()
    var failWrites = false

    override fun writeText(path: String, text: String) {
        if (failWrites) throw IOException("escritura fallida de $path")
        writtenTexts[path] = text
        paths += path
    }

    val stamps = mutableMapOf<String, Long>()

    override fun isDirectory(path: String): Boolean = path in directories

    override fun modificationStamps(path: String): List<Pair<String, Long>> =
        stamps.filterKeys { it == path || it.startsWith("$path/") }.toList()

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
