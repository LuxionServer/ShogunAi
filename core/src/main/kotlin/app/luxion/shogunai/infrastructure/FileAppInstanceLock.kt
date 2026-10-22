package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.AppInstanceLock
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class FileAppInstanceLock(
    private val lockFile: Path = Path.of(System.getProperty("user.home"), ".shogunai", "app.lock"),
) : AppInstanceLock {

    private var channel: FileChannel? = null
    private var held: FileLock? = null

    @Synchronized
    override fun acquire(): Boolean {
        if (held != null) return true
        val opened = open() ?: return false
        val lock = try {
            opened.tryLock()
        } catch (_: OverlappingFileLockException) {
            null
        } catch (_: IOException) {
            null
        }
        if (lock == null) {
            opened.close()
            return false
        }
        channel = opened
        held = lock
        return true
    }

    @Synchronized
    override fun isHeldByAnyInstance(): Boolean {
        if (held != null) return true
        val opened = open() ?: return false
        return opened.use {
            try {
                it.tryLock()?.also { probe -> probe.release() } == null
            } catch (_: OverlappingFileLockException) {
                true
            } catch (_: IOException) {
                false
            }
        }
    }

    @Synchronized
    override fun release() {
        runCatching { held?.release() }
        runCatching { channel?.close() }
        held = null
        channel = null
    }

    private fun open(): FileChannel? = try {
        Files.createDirectories(lockFile.parent)
        FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
    } catch (_: IOException) {
        null
    }
}
