package app.luxion.shogunai.infrastructure

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NioEnvironmentStateStoreTest {

    private val root: Path = Files.createTempDirectory("environment-state")
    private val machine: Path = root.resolve("machine")
    private val common: Path = root.resolve("main-repo/.git").createDirectories()
    private val linked: Path = common.resolve("worktrees/TASK-123").createDirectories()
    private val otherCommon: Path = root.resolve("other-repo/.git").createDirectories()
    private val otherLinked: Path = otherCommon.resolve("worktrees/TASK-9").createDirectories()
    private val store = NioEnvironmentStateStore(machine)

    @AfterTest
    fun cleanUp() {
        root.toFile().deleteRecursively()
    }

    @Test
    fun `writes and reads a worktree's ports keeping other fields`() {
        linked.resolve(NioEnvironmentStateStore.STATE_FILE_NAME).writeText("""{ "note": "kept" }""")

        store.writePorts(linked.toString(), mapOf("PORT" to 20000, "port:api" to 20001))

        assertEquals(mapOf("PORT" to 20000, "port:api" to 20001), store.readPorts(linked.toString()))
        assertTrue(linked.resolve(NioEnvironmentStateStore.STATE_FILE_NAME).readText().contains("kept"))
    }

    @Test
    fun `removing every port deletes a file left empty and unindexes it`() {
        store.writePorts(linked.toString(), mapOf("PORT" to 20000))

        store.writePorts(linked.toString(), emptyMap())

        assertFalse(linked.resolve(NioEnvironmentStateStore.STATE_FILE_NAME).exists())
        assertFalse(machine.resolve(NioEnvironmentStateStore.INDEX_FILE_NAME).readText().contains("TASK-123"))
    }

    @Test
    fun `reservations count across repositories on the machine`() {
        store.writePorts(common.toString(), mapOf("PORT" to 20000))
        store.writePorts(linked.toString(), mapOf("PORT" to 20001))
        store.writePorts(otherLinked.toString(), mapOf("PORT" to 20002, "emulator" to 5554))

        assertEquals(setOf(20000, 20001, 20002, 5554), store.reservedPortsOnMachine(common.toString()))
    }

    @Test
    fun `a removed worktree of another repository frees its ports and leaves the index`() {
        store.writePorts(otherLinked.toString(), mapOf("PORT" to 20002))

        otherLinked.toFile().deleteRecursively()

        assertEquals(emptySet(), store.reservedPortsOnMachine(common.toString()))
        assertFalse(machine.resolve(NioEnvironmentStateStore.INDEX_FILE_NAME).readText().contains("TASK-9"))
    }

    @Test
    fun `the current repository counts even before it's indexed`() {
        linked.resolve(NioEnvironmentStateStore.STATE_FILE_NAME).writeText("""{ "ports": { "PORT": 20007 } }""")

        assertEquals(setOf(20007), store.reservedPortsOnMachine(common.toString()))
    }

    @Test
    fun `keeps resources next to ports and indexes worktrees that only have resources`() {
        store.writePorts(linked.toString(), mapOf("PORT" to 20000))
        store.writeResources(linked.toString(), mapOf("ios-simulator" to "UDID-1"))
        store.writeResources(otherLinked.toString(), mapOf("ios-simulator" to "UDID-2"))

        assertEquals(mapOf("PORT" to 20000), store.readPorts(linked.toString()))
        assertEquals(mapOf("ios-simulator" to "UDID-1"), store.readResources(linked.toString()))
        assertEquals(setOf("UDID-1", "UDID-2"), store.recordedResourcesOnMachine(common.toString(), "ios-simulator"))

        store.writePorts(linked.toString(), emptyMap())
        assertEquals(mapOf("ios-simulator" to "UDID-1"), store.readResources(linked.toString()))
        assertTrue(machine.resolve(NioEnvironmentStateStore.INDEX_FILE_NAME).readText().contains("TASK-123"))

        otherLinked.toFile().deleteRecursively()
        assertEquals(setOf("UDID-1"), store.recordedResourcesOnMachine(common.toString(), "ios-simulator"))
    }

    @Test
    fun `old state files without resources still load`() {
        linked.resolve(NioEnvironmentStateStore.STATE_FILE_NAME).writeText("""{ "ports": { "PORT": 20001 } }""")

        assertEquals(emptyMap(), store.readResources(linked.toString()))
        assertEquals(mapOf("PORT" to 20001), store.readPorts(linked.toString()))
    }

    @Test
    fun `an unreadable state file counts as no reservations`() {
        linked.resolve(NioEnvironmentStateStore.STATE_FILE_NAME).writeText("{ not json")

        assertEquals(emptyMap(), store.readPorts(linked.toString()))
    }

    @Test
    fun `runs a block under the machine lock, also nested`() {
        val result = store.withLock { store.withLock { 42 } }

        assertEquals(42, result)
        assertTrue(machine.resolve(NioEnvironmentStateStore.LOCK_FILE_NAME).exists())
    }

    @Test
    fun `a port with a listener isn't free`() {
        ServerSocket().use { socket ->
            socket.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))

            assertFalse(SocketPortProbe().isFree(socket.localPort))
        }
    }

    @Test
    fun `writes and reads last use times`() {
        store.writeLastUsed(linked.toString(), mapOf("emulator" to 1_000L))

        assertEquals(mapOf("emulator" to 1_000L), store.readLastUsed(linked.toString()))
    }

    @Test
    fun `releasing a key keeps the other keys and updates the index`() {
        val admin = linked.toString()
        store.writePorts(admin, mapOf("emulator" to 5554, "PORT" to 20000))
        store.writeResources(admin, mapOf("ios-simulator" to "UDID-1"))
        store.writeLastUsed(admin, mapOf("emulator" to 1L, "ios-simulator" to 2L))

        store.release(admin, "emulator")

        assertEquals(mapOf("PORT" to 20000), store.readPorts(admin))
        assertEquals(mapOf("ios-simulator" to "UDID-1"), store.readResources(admin))
        assertEquals(mapOf("ios-simulator" to 2L), store.readLastUsed(admin))

        store.release(admin, "ios-simulator")
        store.writePorts(admin, emptyMap())

        assertFalse(linked.resolve(NioEnvironmentStateStore.STATE_FILE_NAME).exists())
    }

    @Test
    fun `reservations on the machine list every worktree with its ports, resources and last use`() {
        store.writePorts(linked.toString(), mapOf("emulator" to 5554))
        store.writeLastUsed(linked.toString(), mapOf("emulator" to 7L))
        store.writeResources(otherLinked.toString(), mapOf("ios-simulator" to "UDID-1"))

        val reservations = store.reservationsOnMachine(common.toString()).associateBy { it.adminDirectory }

        assertEquals(mapOf("emulator" to 5554), reservations.getValue(linked.toString()).ports)
        assertEquals(mapOf("emulator" to 7L), reservations.getValue(linked.toString()).lastUsed)
        assertEquals(mapOf("ios-simulator" to "UDID-1"), reservations.getValue(otherLinked.toString()).resources)
    }
}
