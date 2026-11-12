package app.luxion.shogunai.infrastructure

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions

internal fun writeAtomically(target: Path, text: String, privateFile: Boolean = false) {
    Files.createDirectories(target.parent)
    val tempFile = Files.createTempFile(target.parent, target.fileName.toString(), ".tmp")
    try {
        if (privateFile) runCatching { Files.setPosixFilePermissions(tempFile, PosixFilePermissions.fromString("rw-------")) }
        Files.writeString(tempFile, if (text.endsWith("\n")) text else text + "\n")
        Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch (error: IOException) {
        Files.deleteIfExists(tempFile)
        throw error
    }
}
