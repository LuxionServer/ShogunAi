package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.EnvironmentStateStore
import app.luxion.shogunai.domain.io.PortProbe
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
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

class NioEnvironmentStateStore : EnvironmentStateStore {

    private val json = Json { prettyPrint = true }

    override fun readPorts(adminDirectory: String): Map<String, Int> {
        val ports = readState(stateFile(Path.of(adminDirectory)))[PORTS_FIELD] as? JsonObject ?: return emptyMap()
        return ports.mapNotNull { (key, value) -> (value as? JsonPrimitive)?.intOrNull?.let { key to it } }.toMap()
    }

    override fun writePorts(adminDirectory: String, ports: Map<String, Int>) {
        val file = stateFile(Path.of(adminDirectory))
        val state = readState(file)
        val updated = if (ports.isEmpty()) {
            JsonObject(state - PORTS_FIELD)
        } else {
            JsonObject(state + (PORTS_FIELD to JsonObject(ports.mapValues { JsonPrimitive(it.value) })))
        }
        if (updated.isEmpty()) {
            Files.deleteIfExists(file)
            return
        }
        val tempFile = Files.createTempFile(file.parent, ".shogunai-env", ".json.tmp")
        Files.writeString(tempFile, json.encodeToString(JsonElement.serializer(), updated) + "\n")
        Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    override fun reservedPortsOfRepository(commonDirectory: String): Set<Int> {
        val common = Path.of(commonDirectory)
        val linked = common.resolve("worktrees")
            .takeIf { it.isDirectory() }
            ?.listDirectoryEntries()
            ?.filter { it.isDirectory() }
            .orEmpty()
        return (listOf(common) + linked).flatMap { readPorts(it.toString()).values }.toSet()
    }

    override fun <T> withLock(commonDirectory: String, block: () -> T): T {
        if (processLock.isHeldByCurrentThread) return block()
        return processLock.withLock { withFileLock(commonDirectory, block) }
    }

    private fun <T> withFileLock(commonDirectory: String, block: () -> T): T =
        FileChannel.open(
            Path.of(commonDirectory).resolve(LOCK_FILE_NAME),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
        ).use { channel ->
            channel.lock().use { block() }
        }

    private fun stateFile(adminDirectory: Path): Path = adminDirectory.resolve(STATE_FILE_NAME)

    private fun readState(file: Path): JsonObject {
        if (!file.exists()) return JsonObject(emptyMap())
        return try {
            Json.parseToJsonElement(file.readText()) as? JsonObject ?: JsonObject(emptyMap())
        } catch (error: SerializationException) {
            JsonObject(emptyMap())
        }
    }

    companion object {
        const val STATE_FILE_NAME = "shogunai-env.json"
        const val LOCK_FILE_NAME = "shogunai-env.lock"
        private const val PORTS_FIELD = "ports"
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
