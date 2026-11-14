package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.BuildTurnStore
import app.luxion.shogunai.domain.model.BuildSlot
import app.luxion.shogunai.domain.model.ProcessIdentity
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.readText

class NioBuildTurnStore(
    private val machineDirectory: Path,
) : BuildTurnStore {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    override fun read(): List<BuildSlot> {
        val file = slotsFile()
        if (!file.exists()) return emptyList()
        return try {
            json.decodeFromString(SlotsFile.serializer(), file.readText()).slots.map { it.toSlot() }
        } catch (_: SerializationException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    override fun write(slots: List<BuildSlot>) {
        val file = slotsFile()
        if (slots.isEmpty()) {
            Files.deleteIfExists(file)
            return
        }
        Files.createDirectories(machineDirectory)
        val tempFile = Files.createTempFile(machineDirectory, ".$SLOTS_FILE_NAME", ".tmp")
        Files.writeString(tempFile, json.encodeToString(SlotsFile.serializer(), SlotsFile(slots.map { it.toEntry() })) + "\n")
        Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun slotsFile(): Path = machineDirectory.resolve(SLOTS_FILE_NAME)

    @Serializable
    internal data class SlotsFile(val slots: List<SlotEntry>)

    @Serializable
    internal data class SlotEntry(
        val id: String,
        val adminDirectory: String,
        val worktreePath: String,
        val command: String,
        val since: Long,
        val pid: Long,
        val processStartedAt: Long?,
    ) {
        fun toSlot() = BuildSlot(id, adminDirectory, worktreePath, command, since, ProcessIdentity(pid, processStartedAt))
    }

    private fun BuildSlot.toEntry() = SlotEntry(id, adminDirectory, worktreePath, command, since, process.pid, process.startedAt)

    companion object {
        const val SLOTS_FILE_NAME = "build-turns.json"
    }
}
