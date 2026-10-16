package app.luxion.shogunai.infrastructure

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NioEnvironmentStateStoreTest {

    private val common: Path = Files.createTempDirectory("main-repo-git")
    private val linked: Path = common.resolve("worktrees/TASK-123").createDirectories()
    private val store = NioEnvironmentStateStore()

    @AfterTest
    fun cleanUp() {
        common.toFile().deleteRecursively()
    }

    @Test
    fun `writes and reads a worktree's ports keeping other fields`() {
        linked.resolve(NioEnvironmentStateStore.STATE_FILE_NAME).writeText("""{ "emulator": "emulator-5556" }""")

        store.writePorts(linked.toString(), mapOf("PORT" to 20000, "port:api" to 20001))

        assertEquals(mapOf("PORT" to 20000, "port:api" to 20001), store.readPorts(linked.toString()))
        assertTrue(Files.readString(linked.resolve(NioEnvironmentStateStore.STATE_FILE_NAME)).contains("emulator-5556"))
    }

    @Test
    fun `removing every port deletes a file left empty`() {
        store.writePorts(linked.toString(), mapOf("PORT" to 20000))

        store.writePorts(linked.toString(), emptyMap())

        assertFalse(linked.resolve(NioEnvironmentStateStore.STATE_FILE_NAME).exists())
    }

    @Test
    fun `lists the reservations of the main and every linked worktree`() {
        store.writePorts(common.toString(), mapOf("PORT" to 20000))
        store.writePorts(linked.toString(), mapOf("PORT" to 20001))

        assertEquals(setOf(20000, 20001), store.reservedPortsOfRepository(common.toString()))
    }

    @Test
    fun `an unreadable state file counts as no reservations`() {
        linked.resolve(NioEnvironmentStateStore.STATE_FILE_NAME).writeText("{ not json")

        assertEquals(emptyMap(), store.readPorts(linked.toString()))
    }

    @Test
    fun `runs a block under the repository lock`() {
        val result = store.withLock(common.toString()) { store.withLock(common.toString()) { 42 } }

        assertEquals(42, result)
        assertTrue(common.resolve(NioEnvironmentStateStore.LOCK_FILE_NAME).exists())
    }

    @Test
    fun `a port with a listener isn't free`() {
        ServerSocket().use { socket ->
            socket.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))

            assertFalse(SocketPortProbe().isFree(socket.localPort))
        }
    }
}
