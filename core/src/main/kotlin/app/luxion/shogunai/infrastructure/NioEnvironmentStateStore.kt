package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.EnvironmentStateStore
import app.luxion.shogunai.domain.io.PortProbe
import app.luxion.shogunai.domain.io.WorktreeReservations
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText

class NioEnvironmentStateStore(
    private val machineDirectory: Path = Path.of(System.getProperty("user.home"), ".shogunai"),
) : EnvironmentStateStore {

    private val json = Json { prettyPrint = true }

    override fun readPorts(adminDirectory: String): Map<String, Int> {
        val ports = readObject(stateFile(Path.of(adminDirectory)))[PORTS_FIELD] as? JsonObject ?: return emptyMap()
        return ports.mapNotNull { (key, value) -> (value as? JsonPrimitive)?.intOrNull?.let { key to it } }.toMap()
    }

    override fun writePorts(adminDirectory: String, ports: Map<String, Int>) =
        writeField(adminDirectory, PORTS_FIELD, ports.mapValues { JsonPrimitive(it.value) })

    override fun readResources(adminDirectory: String): Map<String, String> {
        val resources = readObject(stateFile(Path.of(adminDirectory)))[RESOURCES_FIELD] as? JsonObject ?: return emptyMap()
        return resources.mapNotNull { (key, value) ->
            (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { key to it }
        }.toMap()
    }

    override fun writeResources(adminDirectory: String, resources: Map<String, String>) =
        writeField(adminDirectory, RESOURCES_FIELD, resources.mapValues { JsonPrimitive(it.value) })

    override fun readLastUsed(adminDirectory: String): Map<String, Long> {
        val times = readObject(stateFile(Path.of(adminDirectory)))[LAST_USED_FIELD] as? JsonObject ?: return emptyMap()
        return times.mapNotNull { (key, value) -> (value as? JsonPrimitive)?.longOrNull?.let { key to it } }.toMap()
    }

    override fun writeLastUsed(adminDirectory: String, lastUsed: Map<String, Long>) =
        writeField(adminDirectory, LAST_USED_FIELD, lastUsed.mapValues { JsonPrimitive(it.value) })

    override fun reservationsOnMachine(commonDirectory: String): List<WorktreeReservations> =
        machineAdminDirectories(commonDirectory).map { admin ->
            WorktreeReservations(admin, readPorts(admin), readResources(admin), readLastUsed(admin))
        }

    override fun reservedPortsOnMachine(commonDirectory: String): Set<Int> =
        machineAdminDirectories(commonDirectory).flatMap { readPorts(it).values }.toSet()

    override fun recordedResourcesOnMachine(commonDirectory: String, key: String): Set<String> =
        machineAdminDirectories(commonDirectory).mapNotNull { readResources(it)[key] }.toSet()

    private fun writeField(adminDirectory: String, field: String, values: Map<String, JsonPrimitive>) {
        val file = stateFile(Path.of(adminDirectory))
        val state = readObject(file)
        val updated = if (values.isEmpty()) JsonObject(state - field) else JsonObject(state + (field to JsonObject(values)))
        if (updated.isEmpty()) Files.deleteIfExists(file) else writeAtomically(file, updated)
        val indexed = readIndex()
        val normalized = Path.of(adminDirectory).toAbsolutePath().normalize().toString()
        writeIndex(if (file.exists()) indexed + normalized else indexed - normalized)
    }

    private fun machineAdminDirectories(commonDirectory: String): List<String> {
        val indexed = readIndex()
        val live = indexed.filter { stateFile(Path.of(it)).exists() }.toSet()
        if (live != indexed) writeIndex(live)
        return (repositoryAdminDirectories(Path.of(commonDirectory)).map { it.toAbsolutePath().normalize().toString() } + live)
            .distinct()
    }

    override fun <T> withLock(block: () -> T): T {
        if (processLock.isHeldByCurrentThread) return block()
        return processLock.withLock {
            Files.createDirectories(machineDirectory)
            FileChannel.open(
                machineDirectory.resolve(LOCK_FILE_NAME),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            ).use { channel ->
                channel.lock().use { block() }
            }
        }
    }

    private fun repositoryAdminDirectories(common: Path): List<Path> {
        val linked = common.resolve("worktrees")
            .takeIf { it.isDirectory() }
            ?.listDirectoryEntries()
            ?.filter { it.isDirectory() }
            .orEmpty()
        return listOf(common) + linked
    }

    private fun stateFile(adminDirectory: Path): Path = adminDirectory.resolve(STATE_FILE_NAME)

    private fun indexFile(): Path = machineDirectory.resolve(INDEX_FILE_NAME)

    private fun readIndex(): Set<String> {
        val entries = readObject(indexFile())[WORKTREES_FIELD] as? JsonArray ?: return emptySet()
        return entries.mapNotNull { (it as? JsonPrimitive)?.takeIf { primitive -> primitive.isString }?.content }.toSet()
    }

    private fun writeIndex(entries: Set<String>) {
        if (entries == readIndex()) return
        Files.createDirectories(machineDirectory)
        writeAtomically(indexFile(), JsonObject(mapOf(WORKTREES_FIELD to JsonArray(entries.sorted().map(::JsonPrimitive)))))
    }

    private fun readObject(file: Path): JsonObject {
        if (!file.exists()) return JsonObject(emptyMap())
        return try {
            Json.parseToJsonElement(file.readText()) as? JsonObject ?: JsonObject(emptyMap())
        } catch (error: SerializationException) {
            JsonObject(emptyMap())
        }
    }

    private fun writeAtomically(file: Path, content: JsonObject) {
        val tempFile = Files.createTempFile(file.parent, ".${file.fileName}", ".tmp")
        Files.writeString(tempFile, json.encodeToString(JsonElement.serializer(), content) + "\n")
        Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        const val STATE_FILE_NAME = "shogunai-env.json"
        const val INDEX_FILE_NAME = "reservations.json"
        const val LOCK_FILE_NAME = "reservations.lock"
        private const val PORTS_FIELD = "ports"
        private const val RESOURCES_FIELD = "resources"
        private const val LAST_USED_FIELD = "lastUsed"
        private const val WORKTREES_FIELD = "worktrees"
        private val processLock = ReentrantLock()
    }
}

class SocketPortProbe : PortProbe {

    override fun isFree(port: Int): Boolean =
        canBind(InetSocketAddress(port)) && canBind(InetSocketAddress(InetAddress.getLoopbackAddress(), port))

    private fun canBind(address: InetSocketAddress): Boolean = try {
        ServerSocket().use { socket ->
            socket.reuseAddress = false
            socket.bind(address)
            true
        }
    } catch (error: IOException) {
        false
    }
}
