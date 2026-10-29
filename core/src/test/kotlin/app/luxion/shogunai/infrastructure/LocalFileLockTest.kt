package app.luxion.shogunai.infrastructure

import java.io.IOException
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `runs the block unlocked when the lock file can't be created or locked`() {
        val blocker = root.resolve("file").also { it.writeText("not a folder") }

        assertEquals("unlocked", LocalFileLock.withLock(blocker.resolve("settings.json")) { "unlocked" })
        assertEquals("unlocked", LocalFileLock.withLock(root.resolve("projects.json"), { throw IOException("locks unsupported") }) { "unlocked" })
    }
}
