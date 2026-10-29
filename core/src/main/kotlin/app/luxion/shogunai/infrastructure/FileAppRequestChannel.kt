package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.AppRequestChannel
import app.luxion.shogunai.domain.model.AppRequest
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.io.path.deleteIfExists
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText

class FileAppRequestChannel(
    private val directory: Path,
) : AppRequestChannel {

    override fun send(request: AppRequest) {
        Files.createDirectories(directory)
        val name = "%013d-%s".format(request.createdAt, UUID.randomUUID())
        val temporary = Files.createTempFile(directory, name, ".tmp")
        Files.writeString(temporary, request.toJson())
        Files.move(temporary, directory.resolve("$name.json"), StandardCopyOption.ATOMIC_MOVE)
    }

    override fun receive(nowMillis: Long): List<AppRequest> {
        val files = try {
            Files.newDirectoryStream(directory, "*.json").use { stream -> stream.filter { it.extension == "json" }.sortedBy { it.name } }
        } catch (_: IOException) {
            return emptyList()
        }
        return files.mapNotNull { file ->
            val text = try {
                file.readText()
            } catch (_: IOException) {
                return@mapNotNull null
            }
            file.deleteIfExists()
            AppRequest.parse(text)?.takeIf { nowMillis - it.createdAt <= AppRequest.MAX_AGE_MILLIS }
        }
    }
}
