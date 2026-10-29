package app.luxion.shogunai.infrastructure

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JsonOrcaWorkspaceStoreTest {

    private val folder = Files.createTempDirectory("orca-store")
    private val file = folder.resolve(".shogunai/orca.json")

    @Test
    fun `nothing is remembered at first`() {
        assertNull(JsonOrcaWorkspaceStore(file).workspacesRoot())
    }

    @Test
    fun `a remembered folder is read back by another instance`() {
        JsonOrcaWorkspaceStore(file).remember("/home/dev/orca/workspaces")

        assertEquals("/home/dev/orca/workspaces", JsonOrcaWorkspaceStore(file).workspacesRoot())
    }

    @Test
    fun `an unreadable file remembers nothing`() {
        Files.createDirectories(file.parent)
        file.writeText("{not json")

        assertNull(JsonOrcaWorkspaceStore(file).workspacesRoot())
    }

    @Test
    fun `board statuses and the workspaces folder survive each other's writes`() {
        JsonOrcaWorkspaceStore(file).remember("/home/dev/orca/workspaces")
        JsonOrcaWorkspaceStore(file).rememberBoardStatuses(mapOf("/projects/worktrees/TASK-1" to "in-review"))
        JsonOrcaWorkspaceStore(file).remember("/home/dev/orca/other")

        val store = JsonOrcaWorkspaceStore(file)
        assertEquals("/home/dev/orca/other", store.workspacesRoot())
        assertEquals(mapOf("/projects/worktrees/TASK-1" to "in-review"), store.boardStatuses())
    }

    @Test
    fun `a file written before board statuses reads with none`() {
        Files.createDirectories(file.parent)
        file.writeText("""{"workspacesRoot": "/home/dev/orca/workspaces"}""")

        assertEquals(emptyMap(), JsonOrcaWorkspaceStore(file).boardStatuses())
        assertEquals("/home/dev/orca/workspaces", JsonOrcaWorkspaceStore(file).workspacesRoot())
    }
}
