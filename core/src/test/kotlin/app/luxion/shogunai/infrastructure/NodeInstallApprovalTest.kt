package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.NodeProvider
import app.luxion.shogunai.domain.model.RepositoryApprovalSubject
import app.luxion.shogunai.domain.model.RepositoryProviders
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.approvalSubject
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NodeInstallApprovalTest {

    private val home: Path = Files.createTempDirectory("trust-home")
    private val trust = JsonRepositoryTrust(home.resolve(".shogunai").resolve("trust.json"))
    private val repository = home.resolve("main-repo").also { it.createDirectories() }.toString()
    private val build = RepositoryApprovalSubject(commands = listOf(listOf("npm", "run", "build")))

    @AfterTest
    fun cleanUp() {
        home.toFile().deleteRecursively()
    }

    @Test
    fun `declaring the node provider puts the install in the approval`() {
        val subject = RepositoryProvisioning(providers = RepositoryProviders(node = NodeProvider)).approvalSubject

        assertTrue(subject.nodeInstall)
        assertFalse(subject.isEmpty)
        assertFalse(RepositoryProvisioning().approvalSubject.nodeInstall)
    }

    @Test
    fun `a repository with only the provider needs an approval`() {
        val onlyNode = RepositoryApprovalSubject(nodeInstall = true)

        assertFalse(trust.isApproved(repository, onlyNode))
        trust.approve(repository, onlyNode)
        assertTrue(trust.isApproved(repository, onlyNode))
        assertTrue(home.resolve(".shogunai/trust.json").readText().contains("\"nodeInstall\": true"))
    }

    @Test
    fun `a pull that declares the provider invalidates the earlier approval`() {
        trust.approve(repository, build)

        assertFalse(trust.isApproved(repository, build.copy(nodeInstall = true)))
    }

    @Test
    fun `approvals without the provider keep their digest`() {
        trust.approve(repository, build)

        val digest = MessageDigest.getInstance("SHA-256").digest("""[["npm","run","build"]]""".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertTrue(home.resolve(".shogunai/trust.json").readText().contains(digest))
    }
}
