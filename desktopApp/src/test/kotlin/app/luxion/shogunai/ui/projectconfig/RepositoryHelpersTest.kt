package app.luxion.shogunai.ui.projectconfig

import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.FakeRepositoryProvisioningSource
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepositoryHelpersTest {

    @Test
    fun `a repository file that can't be read from disk reads as no provisioning`() {
        val source = object : RepositoryProvisioningSource by FakeRepositoryProvisioningSource() {
            override fun read(repositoryPath: String): RepositoryProvisioning = throw IOException("permission denied")
        }

        assertEquals(RepositoryProvisioningState.None, source.stateFor("/home/dev/projects/main-repo"))
    }

    @Test
    fun `entries that climb out of the repository are not inside it`() {
        assertFalse(isInsideRepository(".."))
        assertFalse(isInsideRepository("../shared/keys"))
        assertFalse(isInsideRepository("/etc/keys"))
        assertFalse(isInsideRepository("\\etc\\keys"))
        assertTrue(isInsideRepository("..keys"))
    }

    @Test
    fun `an entry made only of separators is kept as it is`() {
        assertEquals("/", normalizeSecretFile("/home/dev/projects/main-repo", "/"))
    }

    @Test
    fun `a blank worktrees root has no offset from the repository`() {
        assertNull(worktreesRootOffset("/home/dev/projects/main-repo", ""))
        assertNull(worktreesRootOffset("", "/home/dev/projects"))
    }
}
