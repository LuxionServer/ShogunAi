package app.luxion.shogunai.infrastructure

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal fun keepUnreadableCopy(path: Path): Path {
    val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())
    var kept = path.resolveSibling("${path.fileName}.unreadable-$stamp")
    var attempt = 1
    while (Files.exists(kept)) kept = path.resolveSibling("${path.fileName}.unreadable-$stamp-${attempt++}")
    return Files.move(path, kept)
}
