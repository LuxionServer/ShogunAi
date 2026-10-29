package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.DesktopTurnStore
import app.luxion.shogunai.domain.model.DesktopTurn
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.readText

class NioDesktopTurnStore(
    private val machineDirectory: Path,
) : DesktopTurnStore {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    override fun read(): DesktopTurn? {
        val file = turnFile()
        if (!file.exists()) return null
        return try {
            json.decodeFromString(TurnEntry.serializer(), file.readText()).toTurn()
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override fun write(turn: DesktopTurn?) {
        val file = turnFile()
        if (turn == null) {
            Files.deleteIfExists(file)
            return
        }
        Files.createDirectories(machineDirectory)
        val tempFile = Files.createTempFile(machineDirectory, ".$TURN_FILE_NAME", ".tmp")
        Files.writeString(tempFile, json.encodeToString(TurnEntry.serializer(), turn.toEntry()) + "\n")
        Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun turnFile(): Path = machineDirectory.resolve(TURN_FILE_NAME)

    @Serializable
    private data class TurnEntry(
        val adminDirectory: String,
        val worktreePath: String,
        val acquiredAt: Long,
        val expiresAt: Long,
    ) {
        fun toTurn() = DesktopTurn(adminDirectory, worktreePath, acquiredAt, expiresAt)
    }

    private fun DesktopTurn.toEntry() = TurnEntry(adminDirectory, worktreePath, acquiredAt, expiresAt)

    companion object {
        const val TURN_FILE_NAME = "desktop-turn.json"
    }
}
