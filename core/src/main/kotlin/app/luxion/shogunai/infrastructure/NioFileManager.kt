package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.DirectoryListing
import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.SymbolicLinksNotSupported
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.Properties

class NioFileManager : FileManager {

    override fun exists(path: String): Boolean = Files.exists(Path.of(path))

    override fun isInside(root: String, path: String): Boolean = runCatching {
        val realRoot = Path.of(root).toRealPath()
        var existing = Path.of(path).toAbsolutePath().normalize()
        while (!Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) existing = existing.parent ?: return false
        existing.toRealPath().startsWith(realRoot)
    }.getOrDefault(false)

    override fun copy(source: String, destination: String) {
        val destinationPath = Path.of(destination)
        destinationPath.parent?.let { Files.createDirectories(it) }
        Files.copy(
            Path.of(source),
            destinationPath,
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.COPY_ATTRIBUTES,
        )
    }

    override fun createSymbolicLink(target: String, link: String) {
        val linkPath = Path.of(link)
        linkPath.parent?.let { Files.createDirectories(it) }
        Files.deleteIfExists(linkPath)
        try {
            Files.createSymbolicLink(linkPath, Path.of(target))
        } catch (error: UnsupportedOperationException) {
            throw SymbolicLinksNotSupported(error)
        } catch (error: FileSystemException) {
            if (error.javaClass == FileSystemException::class.java && error.reason?.contains("privilege") == true) {
                throw SymbolicLinksNotSupported(error)
            }
            throw error
        }
    }

    override fun isSymbolicLinkTo(link: String, target: String): Boolean {
        val linkPath = Path.of(link)
        if (!Files.isSymbolicLink(linkPath)) return false
        return runCatching { Files.isSameFile(linkPath, Path.of(target)) }.getOrDefault(false)
    }

    override fun hasSameContent(first: String, second: String): Boolean =
        runCatching { Files.mismatch(Path.of(first), Path.of(second)) == -1L }.getOrDefault(false)

    override fun deleteRecursively(path: String) {
        val root = Path.of(path)
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return
        Files.walkFileTree(
            root,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    deleteEvenIfReadOnly(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    if (exc != null) throw exc
                    deleteEvenIfReadOnly(dir)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }

    private fun deleteEvenIfReadOnly(path: Path) {
        try {
            Files.delete(path)
        } catch (error: AccessDeniedException) {
            if (!path.toFile().setWritable(true)) throw error
            Files.delete(path)
        }
    }

    override fun isDirectory(path: String): Boolean = Files.isDirectory(Path.of(path))

    override fun listTree(directory: String): DirectoryListing {
        val root = Path.of(directory)
        if (!Files.isDirectory(root)) return DirectoryListing()
        val files = mutableListOf<String>()
        val skippedLinks = mutableListOf<String>()
        Files.walkFileTree(
            root,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val relative = root.relativize(file).joinToString("/")
                    when {
                        attrs.isSymbolicLink -> skippedLinks += relative
                        attrs.isRegularFile -> files += relative
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
            },
        )
        return DirectoryListing(files, skippedLinks)
    }

    override fun isSymbolicLink(path: String): Boolean = Files.isSymbolicLink(Path.of(path))

    override fun deleteFile(path: String) {
        Files.deleteIfExists(Path.of(path))
    }

    override fun writeText(path: String, text: String) {
        val file = Path.of(path)
        val parent = file.toAbsolutePath().parent
        Files.createDirectories(parent)
        val tempFile = Files.createTempFile(parent, ".${file.fileName}", ".tmp")
        Files.writeString(tempFile, text)
        Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    override fun readText(path: String): String? = runCatching {
        val file = Path.of(path)
        if (Files.isRegularFile(file)) Files.readString(file) else null
    }.getOrNull()

    override fun lastModifiedMillis(path: String): Long? = runCatching {
        Files.getLastModifiedTime(Path.of(path)).toMillis()
    }.getOrNull()

    override fun listNames(path: String): List<String>? = runCatching {
        Files.newDirectoryStream(Path.of(path)).use { entries -> entries.map { it.fileName.toString() }.sorted() }
    }.getOrNull()

    override fun readProperties(path: String): Map<String, String> {
        val file = Path.of(path)
        if (!Files.isRegularFile(file)) return emptyMap()
        val properties = Properties()
        try {
            Files.newBufferedReader(file).use(properties::load)
        } catch (error: IOException) {
            return emptyMap()
        }
        return properties.stringPropertyNames().associateWith(properties::getProperty)
    }

    override fun modificationStamps(path: String): List<Pair<String, Long>> {
        val root = Path.of(path)
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return emptyList()
        val stamps = mutableListOf<Pair<String, Long>>()
        Files.walkFileTree(
            root,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    stamps += dir.toString() to attrs.lastModifiedTime().toMillis()
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    stamps += file.toString() to attrs.lastModifiedTime().toMillis()
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
            },
        )
        return stamps
    }

    override fun directorySize(path: String, excluding: Set<String>): Long {
        val root = Path.of(path).toAbsolutePath().normalize()
        val excluded = excluding.map { Path.of(it).toAbsolutePath().normalize() }.toSet()
        var total = 0L
        Files.walkFileTree(
            root,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
                    if (dir != root && dir in excluded) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (attrs.isRegularFile) total += attrs.size()
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
            },
        )
        return total
    }
}
