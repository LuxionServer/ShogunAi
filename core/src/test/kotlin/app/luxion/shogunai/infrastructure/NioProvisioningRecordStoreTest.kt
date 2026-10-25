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
}
