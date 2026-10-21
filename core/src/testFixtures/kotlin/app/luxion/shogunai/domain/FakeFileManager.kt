package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.SymbolicLinksNotSupported
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

    val unreadableTexts = mutableSetOf<String>()

    override fun readText(path: String): String? = if (path in unreadableTexts) null else writtenTexts[path]

    val unreadable = mutableSetOf<String>()

    override fun listNames(path: String): List<String>? {
        if (path in unreadable) return null
        val prefix = "${path.trimEnd('/')}/"
        val children = (paths + directories).filter { it.startsWith(prefix) }.map { it.removePrefix(prefix).substringBefore('/') }
        if (children.isEmpty() && path !in directories) return null
        return children.distinct().sorted()
    }

    val modifiedAt = mutableMapOf<String, Long>()

    override fun lastModifiedMillis(path: String): Long? = modifiedAt[path]

    fun delete(path: String) {
        paths -= path
        writtenTexts.remove(path)
    }

    val stamps = mutableMapOf<String, Long>()

    override fun isDirectory(path: String): Boolean = path in directories

    val directoryFiles = mutableMapOf<String, List<String>>()

    override fun listFiles(directory: String): List<String> = directoryFiles[directory].orEmpty()

    override fun modificationStamps(path: String): List<Pair<String, Long>> =
        stamps.filterKeys { it == path || it.startsWith("$path/") }.toList()

    val symbolicLinks = mutableMapOf<String, String>()
    val contents = mutableMapOf<String, String>()
    var symbolicLinksSupported = true
    var failSymbolicLinksFor: Set<String> = emptySet()

    override fun createSymbolicLink(target: String, link: String) {
        if (!symbolicLinksSupported) throw SymbolicLinksNotSupported()
        if (target in failSymbolicLinksFor) throw IOException("enlace fallido de $target")
        symbolicLinks[link] = target
        paths += link
    }

    override fun isSymbolicLinkTo(link: String, target: String): Boolean = symbolicLinks[link] == target

    override fun hasSameContent(first: String, second: String): Boolean =
        contents[first] != null && contents[first] == contents[second]

    private val paths = existing.toMutableSet()
    val copied = mutableListOf<Pair<String, String>>()
    val sizeRequests = mutableListOf<Pair<String, Set<String>>>()

    override fun directorySize(path: String, excluding: Set<String>): Long {
        sizeRequests += path to excluding
        return sizes[path] ?: 0L
    }

    override fun exists(path: String): Boolean = path in paths

    var outsidePaths: Set<String> = emptySet()

    override fun isInside(root: String, path: String): Boolean = path !in outsidePaths

    override fun copy(source: String, destination: String) {
        if (source in failCopyFor) throw IOException("copia fallida de $source")
        copied += source to destination
        paths += destination
    }
}
