package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.CustomPoolLease
import app.luxion.shogunai.domain.model.CustomPoolLeases
import app.luxion.shogunai.domain.model.CustomPoolLoss
import app.luxion.shogunai.domain.model.ProcessIdentity
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

class NioCustomPoolLeaseStoreTest {

    private val machine: Path = Files.createTempDirectory("pool-leases")
    private val store = NioCustomPoolLeaseStore(machine.resolve(".shogunai"))
    private val file = machine.resolve(".shogunai").resolve(NioCustomPoolLeaseStore.LEASE_FILE_NAME)

    private val lease = CustomPoolLease(
        "/home/dev/projects/main-repo/.git",
        "/home/dev/projects/main-repo",
        "db",
        2,
        "/home/dev/projects/main-repo/.git/worktrees/TASK-1",
        "/home/dev/projects/worktrees/TASK-1",
        10L,
        20L,
    )
    private val loss = CustomPoolLoss("/home/dev/projects/main-repo/.git", "db", 1, "/home/dev/projects/main-repo/.git/worktrees/TASK-2", "/home/dev/projects/worktrees/TASK-2", 30L, "main-repo · TASK-1")

    @AfterTest
    fun cleanUp() {
        machine.toFile().deleteRecursively()
    }

    @Test
    fun `leases and losses survive a round trip, with the process of a running command`() {
        val leases = CustomPoolLeases(listOf(lease, lease.copy(slot = 3, runningBy = ProcessIdentity(42, 7)), lease.copy(slot = 4, runningBy = ProcessIdentity(43, null))), listOf(loss))

        store.write(leases)

        assertEquals(leases, NioCustomPoolLeaseStore(machine.resolve(".shogunai")).read())
    }

    @Test
    fun `writing nothing deletes the file`() {
        store.write(CustomPoolLeases(listOf(lease)))
        store.write(CustomPoolLeases(losses = listOf(loss)))
        assertTrue(file.exists())

        store.write(CustomPoolLeases())

        assertFalse(file.exists())
        store.write(CustomPoolLeases())
    }

    @Test
    fun `a missing or unreadable file reads as empty`() {
        assertEquals(CustomPoolLeases(), store.read())
        file.parent.createDirectories()
        file.writeText("{ not json")
        assertEquals(CustomPoolLeases(), store.read())
        listOf(
            """{ "leases": [{ "slot": "x" }], "losses": [] }""",
            """{ "leases": [] }""",
            """{ "leases": [{ "repository": "/r/.git" }], "losses": [] }""",
            """{ "leases": [], "losses": [{ "repository": "/r/.git" }] }""",
        ).forEach { content ->
            file.writeText(content)
            assertEquals(CustomPoolLeases(), store.read(), content)
        }
    }
}
