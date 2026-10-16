package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.FileManager
import java.io.IOException
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

    override fun isDirectory(path: String): Boolean = Files.isDirectory(Path.of(path))

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
