package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.DeviceWaitQueue
import app.luxion.shogunai.domain.io.ProcessLiveness
import app.luxion.shogunai.domain.model.ProcessIdentity
import app.luxion.shogunai.domain.model.WaitQueueName
import app.luxion.shogunai.domain.model.WaitTicket
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.readText

class NioDeviceWaitQueue(
    private val machineDirectory: Path,
) : DeviceWaitQueue {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    override fun read(queue: WaitQueueName): List<WaitTicket> = readAll()[queue.key].orEmpty().map { it.toTicket() }

    override fun write(queue: WaitQueueName, tickets: List<WaitTicket>) {
        val all = readAll() - queue.key
        val updated = if (tickets.isEmpty()) all else all + (queue.key to tickets.map { it.toEntry() })
        val file = queueFile()
        if (updated.isEmpty()) {
            Files.deleteIfExists(file)
            return
        }
        Files.createDirectories(machineDirectory)
        val tempFile = Files.createTempFile(machineDirectory, ".$QUEUE_FILE_NAME", ".tmp")
        Files.writeString(tempFile, json.encodeToString(QueueFile.serializer(), QueueFile(updated)) + "\n")
        Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun readAll(): Map<String, List<TicketEntry>> {
        val file = queueFile()
        if (!file.exists()) return emptyMap()
        return try {
            json.decodeFromString(QueueFile.serializer(), file.readText()).queues
        } catch (_: SerializationException) {
            emptyMap()
        } catch (_: IllegalArgumentException) {
            emptyMap()
        }
    }

    private fun queueFile(): Path = machineDirectory.resolve(QUEUE_FILE_NAME)

    @Serializable
    private data class QueueFile(val queues: Map<String, List<TicketEntry>> = emptyMap())

    @Serializable
    private data class TicketEntry(
        val id: String,
        val adminDirectory: String,
        val worktreePath: String,
        val pid: Long,
        val processStartedAt: Long? = null,
        val since: Long,
    ) {
        fun toTicket() = WaitTicket(id, adminDirectory, worktreePath, ProcessIdentity(pid, processStartedAt), since)
    }

    private fun WaitTicket.toEntry() = TicketEntry(id, adminDirectory, worktreePath, process.pid, process.startedAt, since)

    companion object {
        const val QUEUE_FILE_NAME = "device-queue.json"
    }
}

class ProcessHandleLiveness : ProcessLiveness {

    override fun current(): ProcessIdentity = identityOf(ProcessHandle.current())

    override fun isAlive(process: ProcessIdentity): Boolean {
        val handle = ProcessHandle.of(process.pid).orElse(null) ?: return false
        if (!handle.isAlive) return false
        val startedAt = process.startedAt ?: return true
        val actual = handle.info().startInstant().orElse(null)?.toEpochMilli() ?: return true
        return actual == startedAt
    }

    private fun identityOf(handle: ProcessHandle) =
        ProcessIdentity(handle.pid(), handle.info().startInstant().orElse(null)?.toEpochMilli())
}
