package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeOrcaClient
import app.luxion.shogunai.domain.io.OrcaWorkspaceStore
import app.luxion.shogunai.domain.model.ObservedPort
import app.luxion.shogunai.domain.model.OrcaPort
import app.luxion.shogunai.domain.model.OrcaWorktree
import app.luxion.shogunai.domain.model.ProjectConfig
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.WorktreeOrigin
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OrcaWorktreeReaderTest {

    private val config = ProjectConfig(
        baseRepositoryPath = "/home/dev/projects/main-repo",
        worktreesRoot = "/home/dev/projects/worktrees",
        secretFiles = emptyList(),
    )
    private val shogunai = Worktree(path = "/home/dev/projects/worktrees/TASK-1", branch = "feature/TASK-1")
    private val created = Worktree(path = "/home/dev/workspaces/main-repo/TASK-2", branch = "user/TASK-2")
    private val inDefault = Worktree(path = "/home/dev/orca/workspaces/main-repo/TASK-3", branch = "user/TASK-3")
    private val elsewhere = Worktree(path = "/home/dev/elsewhere/TASK-4", branch = "feature/TASK-4")

    private class MemoryStore(var root: String? = null) : OrcaWorkspaceStore {
        override fun workspacesRoot(): String? = root

        override fun remember(workspacesRoot: String) {
            root = workspacesRoot
        }
    }

    private var clock = 1_000L

    private fun reader(orca: FakeOrcaClient, store: MemoryStore = MemoryStore(), canonical: (String) -> String = { it }) =
        OrcaWorktreeReader(orca, store, userHome = "/home/dev", now = { clock }, canonical = canonical)

    private fun origin(worktree: Worktree, reader: OrcaWorktreeReader, herdrRoot: String? = null) =
        classifyOrigin(worktree, config, herdrRoot, reader.origins())

    @Test
    fun `a worktree Orca created is classified as Orca's`() = runTest {
        val reader = reader(FakeOrcaClient(psRows = listOf(OrcaWorktree(created.path, createdByOrca = true))))

        reader.read()

        assertEquals(WorktreeOrigin.ORCA, origin(created, reader))
    }

    @Test
    fun `a ShogunAi worktree shown in Orca stays ShogunAi's`() = runTest {
        val reader = reader(FakeOrcaClient(psRows = listOf(OrcaWorktree(shogunai.path, createdByOrca = false))))

        reader.read()

        assertEquals(WorktreeOrigin.SHOGUNAI, origin(shogunai, reader))
    }

    @Test
    fun `the folder Orca creates worktrees in is remembered`() = runTest {
        val store = MemoryStore()

        reader(FakeOrcaClient(psRows = listOf(OrcaWorktree(created.path, createdByOrca = true))), store).read()

        assertEquals("/home/dev/workspaces", store.root)
    }

    @Test
    fun `while Orca can't be asked the remembered folder decides`() = runTest {
        val reader = reader(FakeOrcaClient(running = false), MemoryStore("/home/dev/workspaces"))

        assertNull(reader.read())

        assertEquals(WorktreeOrigin.ORCA, origin(created, reader))
        assertEquals(WorktreeOrigin.OTHER, origin(inDefault, reader))
        assertEquals(WorktreeOrigin.SHOGUNAI, origin(shogunai, reader))
    }

    @Test
    fun `without a remembered folder Orca's default folder decides`() = runTest {
        val reader = reader(FakeOrcaClient(installed = false))

        reader.read()

        assertEquals(WorktreeOrigin.ORCA, origin(inDefault, reader))
        assertEquals(WorktreeOrigin.OTHER, origin(elsewhere, reader))
    }

    @Test
    fun `Claude Code and Herdr come before Orca`() = runTest {
        val claude = Worktree(path = "/home/dev/orca/workspaces/main-repo/.claude/worktrees/x", branch = "x")
        val reader = reader(FakeOrcaClient(installed = false))

        assertEquals(WorktreeOrigin.CLAUDE_CODE, origin(claude, reader))
        assertEquals(WorktreeOrigin.HERDR, origin(inDefault, reader, herdrRoot = "/home/dev/orca"))
    }

    @Test
    fun `ports Orca sees are given per worktree with process and address`() = runTest {
        val orca = FakeOrcaClient(
            listeningPorts = listOf(
                OrcaPort(10011, shogunai.path, "java"),
                OrcaPort(8765, shogunai.path, "Python", "http://127.0.0.1:8765"),
                OrcaPort(10004, created.path, "java"),
            ),
        )
        val reader = reader(orca)

        reader.read()

        assertEquals(
            mapOf(
                shogunai.path to listOf(ObservedPort(8765, "Python", "http://127.0.0.1:8765"), ObservedPort(10011, "java")),
                elsewhere.path to emptyList(),
            ),
            reader.ports(listOf(shogunai, elsewhere)),
        )
        assertEquals(1, orca.portReads)
    }

    @Test
    fun `a port seen on IPv4 and IPv6 counts once`() = runTest {
        val reader = reader(FakeOrcaClient(listeningPorts = listOf(OrcaPort(5580, shogunai.path, "qemu"), OrcaPort(5580, shogunai.path, "qemu"))))

        reader.read()

        assertEquals(listOf(ObservedPort(5580, "qemu")), reader.ports(listOf(shogunai))?.get(shogunai.path))
    }

    @Test
    fun `ports are matched by canonical path`() = runTest {
        val tmp = Worktree(path = "/tmp/worktrees/TASK-5", branch = "feature/TASK-5")
        val orca = FakeOrcaClient(listeningPorts = listOf(OrcaPort(8765, "/private/tmp/worktrees/TASK-5")))
        val reader = reader(orca, canonical = { it.removePrefix("/private") })

        reader.read()

        assertEquals(listOf(ObservedPort(8765)), reader.ports(listOf(tmp))?.get(tmp.path))
    }

    @Test
    fun `without a running Orca there are no ports and Orca isn't started`() = runTest {
        val orca = FakeOrcaClient(running = false, listeningPorts = listOf(OrcaPort(8765, shogunai.path)))
        val reader = reader(orca)

        reader.read()

        assertNull(reader.ports(listOf(shogunai)))
        assertEquals(0, orca.portReads)
        assertEquals(0, orca.starts)
    }

    @Test
    fun `without Orca installed nothing runs`() = runTest {
        val orca = FakeOrcaClient(installed = false)
        val reader = reader(orca)

        reader.read()

        assertNull(reader.ports(listOf(shogunai)))
        assertEquals(0, orca.portReads)
    }

    @Test
    fun `a failed ports read keeps the agents and waits 30 seconds before asking again`() = runTest {
        val rows = listOf(OrcaWorktree(shogunai.path, createdByOrca = false))
        val orca = FakeOrcaClient(psRows = rows, portsFailure = IllegalStateException("ports failed"))
        val reader = reader(orca)

        assertEquals(rows, reader.read())
        assertNull(reader.ports(listOf(shogunai)))

        orca.portsFailure = null
        clock += 29_000L
        reader.read()
        assertNull(reader.ports(listOf(shogunai)))
        assertEquals(1, orca.portReads)

        clock += 1_000L
        reader.read()
        assertEquals(emptyList(), reader.ports(listOf(shogunai))?.get(shogunai.path))
        assertEquals(2, orca.portReads)
        assertEquals(3, orca.psReads)
    }
}
