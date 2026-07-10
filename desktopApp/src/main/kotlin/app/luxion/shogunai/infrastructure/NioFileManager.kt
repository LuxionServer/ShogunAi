package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.FileManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Implementación de [FileManager] sobre `java.nio.file`.
 * Funciona igual en macOS y Linux.
 */
class NioFileManager : FileManager {

    override fun exists(path: String): Boolean = Files.exists(Path.of(path))

    override fun copy(source: String, destination: String) {
        Files.copy(
            Path.of(source),
            Path.of(destination),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.COPY_ATTRIBUTES,
        )
    }
}
