package app.luxion.shogunai.infrastructure

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
}
