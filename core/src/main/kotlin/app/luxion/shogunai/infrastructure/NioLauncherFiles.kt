package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.LauncherFiles
import java.nio.file.Files
import java.nio.file.Path

class NioLauncherFiles : LauncherFiles {
    override fun exists(path: String): Boolean = Files.exists(Path.of(path))

    override fun isWritableFolder(folder: String): Boolean {
        val path = Path.of(folder)
        return Files.isDirectory(path) && Files.isWritable(path)
    }

    override fun writeText(path: String, text: String) {
        val file = Path.of(path)
        file.parent?.let { Files.createDirectories(it) }
        Files.writeString(file, text)
    }

    override fun readText(path: String): String? = runCatching { Files.readString(Path.of(path)) }.getOrNull()

    override fun linkTarget(link: String): String? {
        val path = Path.of(link)
        return if (Files.isSymbolicLink(path)) runCatching { Files.readSymbolicLink(path).toString() }.getOrNull() else null
    }

    override fun createLink(target: String, link: String) {
        val path = Path.of(link)
        path.parent?.let { Files.createDirectories(it) }
        Files.deleteIfExists(path)
        Files.createSymbolicLink(path, Path.of(target))
    }

    override fun delete(path: String) {
        Files.deleteIfExists(Path.of(path))
    }
}
