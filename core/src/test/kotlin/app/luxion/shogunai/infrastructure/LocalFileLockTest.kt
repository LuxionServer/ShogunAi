package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.WorktreeError
import java.io.IOException
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.assertFailsWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalFileLockTest {

    private val root = createTempDirectory("local-file-lock-test")

    @AfterTest
    fun tearDown() {
        root.toFile().deleteRecursively()
    }

    @Test
    fun `runs the block holding the lock file next to the target`() {
        val target = root.resolve("settings.json")

        assertEquals("done", LocalFileLock.withLock(target) { "done" })
        assertEquals(true, Files.exists(root.resolve("settings.json.lock")))
    }

    @Test
    fun `fails naming the file instead of running unlocked when the lock file can't be created or locked`() {
        val blocker = root.resolve("file").also { it.writeText("not a folder") }
        var ran = false

        val uncreatable = assertFailsWith<WorktreeError.LocalFileUnreadable> {
            LocalFileLock.withLock(blocker.resolve("settings.json")) { ran = true }
        }
        val unsupported = assertFailsWith<WorktreeError.LocalFileUnreadable> {
            LocalFileLock.withLock(root.resolve("projects.json"), { throw IOException("locks unsupported") }) { ran = true }
        }

        assertFalse(ran)
        assertEquals(blocker.resolve("settings.json").toString(), uncreatable.path)
        assertTrue(uncreatable.reason!!.startsWith("can't lock ${blocker.resolve("settings.json.lock")}"))
        assertEquals("can't lock ${root.resolve("projects.json.lock")}: locks unsupported", unsupported.reason)
    }

    @Test
    fun `a nested lock on the same file runs at once and a failing block still releases it`() {
        val target = root.resolve("settings.json")

        assertEquals("inner", LocalFileLock.withLock(target) { LocalFileLock.withLock(target) { "inner" } })
        assertFailsWith<IllegalStateException> { LocalFileLock.withLock(target) { error("boom") } }
        assertEquals("again", LocalFileLock.withLock(target) { "again" })
    }
}
