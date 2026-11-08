package app.luxion.shogunai.infrastructure

import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileAppInstanceLockTest {

    private val lockFile = createTempDirectory("app-lock-test").resolve("app.lock")

    @Test
    fun `is not held before anything acquires it`() {
        assertFalse(FileAppInstanceLock(lockFile).isHeldByAnyInstance())
    }

    @Test
    fun `is held while an instance has acquired it`() {
        val running = FileAppInstanceLock(lockFile)

        assertTrue(running.acquire())

        assertTrue(FileAppInstanceLock(lockFile).isHeldByAnyInstance())
        running.release()
    }

    @Test
    fun `a second instance can't acquire it while the first holds it`() {
        val first = FileAppInstanceLock(lockFile)
        first.acquire()

        assertFalse(FileAppInstanceLock(lockFile).acquire())
        first.release()
    }

    @Test
    fun `is free again after the holder releases it`() {
        val running = FileAppInstanceLock(lockFile)
        running.acquire()

        running.release()

        assertFalse(FileAppInstanceLock(lockFile).isHeldByAnyInstance())
        assertTrue(FileAppInstanceLock(lockFile).acquire())
    }

    @Test
    fun `a lock the file system refuses is neither acquired nor held`() {
        val refusing = FileAppInstanceLock(lockFile) { throw IOException("locks unsupported") }

        assertFalse(refusing.acquire())
        assertFalse(refusing.isHeldByAnyInstance())
    }

    @Test
    fun `an instance that holds the lock keeps it and releasing twice is harmless`() {
        val lock = FileAppInstanceLock(lockFile)

        assertTrue(lock.acquire())
        assertTrue(lock.acquire())
        assertTrue(lock.isHeldByAnyInstance())
        lock.release()
        lock.release()
        assertFalse(lock.isHeldByAnyInstance())
    }

    @Test
    fun `a lock file that can't be opened is neither acquired nor held`() {
        val blocked = lockFile.parent.resolve("file")
        blocked.toFile().writeText("x")
        val lock = FileAppInstanceLock(blocked.resolve("app.lock"))

        assertFalse(lock.acquire())
        assertFalse(lock.isHeldByAnyInstance())
    }

    @Test
    fun `a lock another process holds is held by another instance`() {
        assertTrue(FileAppInstanceLock(lockFile) { null }.isHeldByAnyInstance())
    }
}
