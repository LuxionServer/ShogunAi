package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.DerivedDataSource
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.model.DerivedDataFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class MacDerivedDataSource(
    private val executor: ShellCommandExecutor,
    private val fileManager: FileManager,
    private val home: String,
    private val osName: String = System.getProperty("os.name").orEmpty(),
    private val volumesRoot: String = "/Volumes",
) : DerivedDataSource {

    override fun isSupported(): Boolean = osName.startsWith("Mac", ignoreCase = true)

    override suspend fun folders(): List<DerivedDataFolder> {
        val locationPath = Path.of(location())
        return withContext(Dispatchers.IO) {
            if (!fileManager.exists(locationPath.toString())) return@withContext emptyList()
            locationPath.toFile().listFiles()
                ?.filter { it.isDirectory }
                ?.mapNotNull { folder ->
                    val entry = plistEntry(folder.toPath().resolve(INFO_PLIST)) ?: return@mapNotNull null
                    DerivedDataFolder(
                        path = folder.toString(),
                        workspacePath = realPathOrRecorded(entry.workspacePath),
                        sizeBytes = null,
                        lastAccessed = entry.lastAccessed,
                    )
                }
                .orEmpty()
        }
    }

    override suspend fun size(folder: DerivedDataFolder): Long = measure(folder.path)

    override suspend fun compilationCacheBytes(): Long? {
        val cache = defaults(CUSTOM_COMPILATION_CACHE_LOCATION_KEY)?.let(::expandHome)
            ?: Path.of(location(), COMPILATION_CACHE_NAME).toString()
        return if (fileManager.exists(cache)) measure(cache) else null
    }

    override suspend fun freeBytes(): Long? {
        val location = Path.of(location()).toAbsolutePath()
        return withContext(Dispatchers.IO) {
            val existing = generateSequence(location) { it.parent }.firstOrNull { Files.exists(it) } ?: return@withContext null
            try {
                Files.getFileStore(existing).usableSpace.takeIf { it >= 0 }
            } catch (error: IOException) {
                null
            }
        }
    }

    private suspend fun measure(path: String): Long = withContext(Dispatchers.IO) {
        val job = coroutineContext.job
        var total = 0L
        Files.walkFileTree(
            Path.of(path),
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
                    if (job.isActive) FileVisitResult.CONTINUE else FileVisitResult.TERMINATE

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (attrs.isRegularFile) total += attrs.size()
                    return if (job.isActive) FileVisitResult.CONTINUE else FileVisitResult.TERMINATE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
            },
        )
        job.ensureActive()
        total
    }

    override fun projectExists(workspacePath: String): Boolean = fileManager.exists(workspacePath)

    override fun isOnUnavailableVolume(workspacePath: String): Boolean {
        val root = Path.of(volumesRoot).normalize()
        val path = Path.of(workspacePath).normalize()
        if (!path.startsWith(root)) return false
        val relative = root.relativize(path)
        if (relative.nameCount == 0) return false
        return !fileManager.exists(root.resolve(relative.getName(0)).toString())
    }

    override suspend fun delete(folder: DerivedDataFolder) {
        val child = Path.of(folder.path).normalize()
        if (child.parent != Path.of(location()).normalize()) {
            throw IllegalStateException("Refusing to delete ${folder.path}: not a direct child of the DerivedData location")
        }
        if (!Files.isRegularFile(child.resolve(INFO_PLIST))) {
            throw IllegalStateException("Refusing to delete ${folder.path}: ${child.resolve(INFO_PLIST).fileName} is missing")
        }
        deleteRecursively(child)
    }

    private suspend fun location(): String {
        defaults(DERIVED_DATA_PATH_OVERRIDE_KEY)?.let { return expandHome(it) }
        defaults(CUSTOM_DERIVED_DATA_LOCATION_KEY)?.let { return expandHome(it) }
        return Path.of(home, DEFAULT_DERIVED_DATA_RELATIVE).toString()
    }

    private suspend fun defaults(key: String): String? {
        val command = listOf("defaults", "read", XCODE_DOMAIN, key)
        val result = executor.execute(command)
        if (!result.isSuccess) return null
        return result.stdout.trim().ifBlank { null }
    }

    private fun realPathOrRecorded(workspacePath: String): String = try {
        Path.of(workspacePath).toRealPath().toString()
    } catch (error: Exception) {
        workspacePath
    }

    private fun expandHome(path: String): String =
        if (path == "~") home else if (path.startsWith("~/")) home + path.drop(1) else path

    private fun plistEntry(plist: Path): PlistEntry? {
        if (!Files.isRegularFile(plist)) return null
        val bytes = try {
            Files.readAllBytes(plist)
        } catch (error: IOException) {
            return null
        }
        if (bytes.size >= BPLIST_MAGIC.size && BPLIST_MAGIC.indices.all { bytes[it] == BPLIST_MAGIC[it] }) return null
        val document = parseXml(bytes)
        if (document == null) return null
        val keys = document.getElementsByTagName("key")
        var workspacePath: String? = null
        var lastAccessed: Instant? = null
        for (index in 0 until keys.length) {
            val key = keys.item(index) as Element
            val value = key.nextSiblingElement() ?: continue
            when (key.textContent.trim()) {
                KEY_WORKSPACE_PATH -> workspacePath = value.textContent.trim()
                KEY_LAST_ACCESSED_DATE -> lastAccessed = parseInstant(value.textContent.trim())
            }
        }
        val path = workspacePath?.takeIf { it.isNotEmpty() } ?: return null
        return PlistEntry(path, lastAccessed)
    }

    private fun parseXml(bytes: ByteArray): Document? = try {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isXIncludeAware = false
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        factory.setFeature(LOAD_EXTERNAL_DTD_FEATURE, false)
        factory.setFeature(EXTERNAL_GENERAL_ENTITIES_FEATURE, false)
        factory.setFeature(EXTERNAL_PARAMETER_ENTITIES_FEATURE, false)
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        factory.newDocumentBuilder().parse(bytes.inputStream())
    } catch (error: Exception) {
        null
    }

    private fun parseInstant(text: String): Instant? = try {
        Instant.parse(text)
    } catch (error: DateTimeParseException) {
        null
    }

    private fun deleteRecursively(root: Path) {
        Files.walkFileTree(
            root,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.deleteIfExists(file)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                    throw IOException("Couldn't delete $file", exc)
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    Files.deleteIfExists(dir)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }

    private companion object {
        const val XCODE_DOMAIN = "com.apple.dt.Xcode"
        const val DERIVED_DATA_PATH_OVERRIDE_KEY = "IDEDerivedDataPathOverride"
        const val CUSTOM_DERIVED_DATA_LOCATION_KEY = "IDECustomDerivedDataLocation"
        const val CUSTOM_COMPILATION_CACHE_LOCATION_KEY = "IDECustomCompilationCacheLocation"
        const val INFO_PLIST = "info.plist"
        const val COMPILATION_CACHE_NAME = "CompilationCache.noindex"
        const val DEFAULT_DERIVED_DATA_RELATIVE = "Library/Developer/Xcode/DerivedData"
        const val KEY_WORKSPACE_PATH = "WorkspacePath"
        const val KEY_LAST_ACCESSED_DATE = "LastAccessedDate"
        const val LOAD_EXTERNAL_DTD_FEATURE = "http://apache.org/xml/features/nonvalidating/load-external-dtd"
        const val EXTERNAL_GENERAL_ENTITIES_FEATURE = "http://xml.org/sax/features/external-general-entities"
        const val EXTERNAL_PARAMETER_ENTITIES_FEATURE = "http://xml.org/sax/features/external-parameter-entities"
        val BPLIST_MAGIC = "bplist00".toByteArray(Charsets.ISO_8859_1)
    }
}

internal data class PlistEntry(val workspacePath: String, val lastAccessed: Instant?)

private fun Node.nextSiblingElement(): Element? {
    var current = nextSibling
    while (current != null && current.nodeType != Node.ELEMENT_NODE) {
        current = current.nextSibling
    }
    return current as? Element
}
