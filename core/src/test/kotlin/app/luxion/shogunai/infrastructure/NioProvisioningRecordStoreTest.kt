package app.luxion.shogunai.infrastructure

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.IOException

class NioProvisioningRecordStoreTest {

    private val root = createTempDirectory("provisioning-record-test")
    private val adminDirectory = root.resolve("main-repo/.git/worktrees/TASK-123").createDirectories()
    private val worktree = root.resolve("TASK-123").createDirectories().also {
        it.resolve(".git").writeText("gitdir: ${adminDirectory}\n")
    }
    private val store = NioProvisioningRecordStore()

    @AfterTest
    fun tearDown() {
        root.toFile().deleteRecursively()
    }

    @Test
    fun `writes the record in the worktree's admin folder and reads it back`() {
        store.write(worktree.toString(), mapOf("keys" to listOf("a.pem", "ci/b.pem")))

        assertTrue(adminDirectory.resolve("shogunai-provisioning.json").exists())
        assertFalse(worktree.resolve("shogunai-provisioning.json").exists())
        assertEquals(mapOf("keys" to listOf("a.pem", "ci/b.pem")), store.read(worktree.toString()))
    }

    @Test
    fun `resolves a relative gitdir`() {
        worktree.resolve(".git").writeText("gitdir: ../main-repo/.git/worktrees/TASK-123\n")

        store.write(worktree.toString(), mapOf("keys" to listOf("a.pem")))

        assertEquals(mapOf("keys" to listOf("a.pem")), store.read(worktree.toString()))
    }

    @Test
    fun `an empty record removes the file`() {
        store.write(worktree.toString(), mapOf("keys" to listOf("a.pem")))

        store.write(worktree.toString(), emptyMap())

        assertFalse(adminDirectory.resolve("shogunai-provisioning.json").exists())
        assertEquals(emptyMap(), store.read(worktree.toString()))
    }

    @Test
    fun `a missing or unreadable record is empty`() {
        assertEquals(emptyMap(), store.read(worktree.toString()))
        Files.writeString(adminDirectory.resolve("shogunai-provisioning.json"), "{ not json")

        assertEquals(emptyMap(), store.read(worktree.toString()))
        assertEquals(emptyMap(), store.read(root.resolve("missing").toString()))
    }

    @Test
    fun `writing for a folder without a Git admin folder fails`() {
        assertFailsWith<IOException> { store.write(root.resolve("missing").toString(), mapOf("keys" to listOf("a.pem"))) }
    }

    @Test
    fun `a record without local folders or a git file without gitdir reads as empty`() {
        adminDirectory.resolve("shogunai-provisioning.json").writeText("""{ "other": {} }""")
        assertEquals(emptyMap(), store.read(worktree.toString()))

        worktree.resolve(".git").writeText("not a pointer\n")
        assertEquals(emptyMap(), store.read(worktree.toString()))
    }

    @Test
    fun `records of the wrong shape read as empty and clearing a folder without a Git admin folder does nothing`() {
        val record = adminDirectory.resolve("shogunai-provisioning.json")
        listOf("[1]", "{}", """{"localFolders":[]}""").forEach { text ->
            record.writeText(text)
            assertEquals(emptyMap(), store.read(worktree.toString()), text)
        }
        record.writeText("""{"localFolders":{"keys":"x","certs":["a.pem",1,{}]}}""")
        assertEquals(mapOf("certs" to listOf("a.pem")), store.read(worktree.toString()))

        store.write(root.toString(), emptyMap())
        val emptyGitdir = root.resolve("EMPTY").createDirectories().also { it.resolve(".git").writeText("gitdir: \n") }
        assertEquals(emptyMap(), store.read(emptyGitdir.toString()))
        val noAdmin = root.resolve("NO-ADMIN").createDirectories().also { it.resolve(".git").writeText("gitdir: ../missing\n") }
        assertEquals(emptyMap(), store.read(noAdmin.toString()))
    }
}
