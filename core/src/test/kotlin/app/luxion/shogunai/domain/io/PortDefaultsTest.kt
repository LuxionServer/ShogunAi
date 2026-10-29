package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.FakeFileManager
import app.luxion.shogunai.domain.FakeHerdrClient
import app.luxion.shogunai.domain.FakeOrcaClient
import app.luxion.shogunai.domain.model.RepositoryApprovalSubject
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortDefaultsTest {

    @Test
    fun `without GitHub there are no pull requests or issues`() = runTest {
        assertNull(GitHubClient.NONE.pullRequestFor("/projects/main-repo", "feature/TASK-123"))
        assertFalse(GitHubClient.NONE.issueExists("/projects/main-repo", 123))
    }

    @Test
    fun `without an Orca store nothing is remembered`() {
        OrcaWorkspaceStore.NONE.remember("/projects/orca")
        OrcaWorkspaceStore.NONE.rememberBoardStatuses(mapOf("TASK-123" to "done"))

        assertNull(OrcaWorkspaceStore.NONE.workspacesRoot())
        assertEquals(emptyMap(), OrcaWorkspaceStore.NONE.boardStatuses())
    }

    @Test
    fun `herdr counts as installed unless told otherwise`() {
        assertTrue(FakeHerdrClient().isInstalled())
        assertNull(FakeHerdrClient().location())
    }

    @Test
    fun `orca without a location isn't installed`() {
        val orca = object : OrcaClient by FakeOrcaClient() {
            override fun isInstalled(): Boolean = super<OrcaClient>.isInstalled()
        }

        assertNull(orca.location())
        assertFalse(orca.isInstalled())
    }

    @Test
    fun `directory size excludes nothing by default`() {
        val files = FakeFileManager(sizes = mapOf("/projects/main-repo" to 10L))

        assertEquals(10L, files.directorySize("/projects/main-repo"))
        assertEquals(listOf("/projects/main-repo" to emptySet<String>()), files.sizeRequests)
    }

    @Test
    fun `without trust nothing but an empty subject is approved and revoking changes nothing`() {
        RepositoryTrust.NONE.revoke("/projects/main-repo")

        assertTrue(RepositoryTrust.NONE.isApproved("/projects/main-repo", RepositoryApprovalSubject(emptyList(), emptyList())))
    }
}
