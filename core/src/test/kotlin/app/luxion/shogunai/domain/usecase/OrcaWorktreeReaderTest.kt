package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.FakeOrcaClient
import app.luxion.shogunai.domain.io.OrcaWorkspaceStore
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

    private fun reader(orca: FakeOrcaClient, store: MemoryStore = MemoryStore()) =
        OrcaWorktreeReader(orca, store, userHome = "/home/dev", now = { 1_000L }, canonical = { it })

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
}
