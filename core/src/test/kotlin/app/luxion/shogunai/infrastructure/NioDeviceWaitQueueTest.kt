package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.ProcessIdentity
import app.luxion.shogunai.domain.model.ResourceKind
import app.luxion.shogunai.domain.model.WaitTicket
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

class NioDeviceWaitQueueTest {

    private val machine: Path = Files.createTempDirectory("device-queue")
    private val queue = NioDeviceWaitQueue(machine)

    private fun ticket(id: String, since: Long) =
        WaitTicket(id, "/home/dev/projects/main-repo/.git/worktrees/$id", "/home/dev/projects/worktrees/$id", ProcessIdentity(42, 7), since)

    @AfterTest
    fun cleanUp() {
        machine.toFile().deleteRecursively()
    }

    @Test
    fun `keeps each kind's tickets in the order written`() {
        queue.write(ResourceKind.EMULATOR, listOf(ticket("TASK-2", 1), ticket("TASK-3", 2)))
        queue.write(ResourceKind.IOS_SIMULATOR, listOf(ticket("TASK-4", 3)))

        assertEquals(listOf("TASK-2", "TASK-3"), queue.read(ResourceKind.EMULATOR).map { it.id })
        assertEquals(listOf(ticket("TASK-4", 3)), queue.read(ResourceKind.IOS_SIMULATOR))
    }

    @Test
    fun `removing the last ticket deletes the file`() {
        queue.write(ResourceKind.EMULATOR, listOf(ticket("TASK-2", 1)))

        queue.write(ResourceKind.EMULATOR, emptyList())

        assertTrue(queue.read(ResourceKind.EMULATOR).isEmpty())
        assertFalse(machine.resolve(NioDeviceWaitQueue.QUEUE_FILE_NAME).exists())
    }

    @Test
    fun `a missing or unreadable file reads as an empty queue`() {
        assertTrue(queue.read(ResourceKind.EMULATOR).isEmpty())

        machine.createDirectories().resolve(NioDeviceWaitQueue.QUEUE_FILE_NAME).writeText("not json")

        assertTrue(queue.read(ResourceKind.EMULATOR).isEmpty())
        queue.write(ResourceKind.EMULATOR, listOf(ticket("TASK-2", 1)))
        assertEquals(1, queue.read(ResourceKind.EMULATOR).size)
    }

    @Test
    fun `the current process is alive and a reused pid with another start time is not`() {
        val liveness = ProcessHandleLiveness()
        val current = liveness.current()

        assertTrue(liveness.isAlive(current))
        assertTrue(liveness.isAlive(current.copy(startedAt = null)))
        current.startedAt?.let { startedAt -> assertFalse(liveness.isAlive(current.copy(startedAt = startedAt - 1))) }
    }

    @Test
    fun `a finished process is not alive`() {
        val process = ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-version").redirectErrorStream(true).start()
        val identity = ProcessIdentity(process.pid(), null)
        process.inputStream.readAllBytes()
        process.waitFor()

        assertFalse(ProcessHandleLiveness().isAlive(identity))
    }
}
