package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.model.WorktreeError
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

internal object LocalFileLock {

    private val threadLocks = ConcurrentHashMap<Path, ReentrantLock>()

    fun <T> withLock(target: Path, lock: (FileChannel) -> FileLock = FileChannel::lock, block: () -> T): T {
        val key = target.toAbsolutePath().normalize()
        val threadLock = threadLocks.computeIfAbsent(key) { ReentrantLock() }
        threadLock.lock()
        try {
            if (threadLock.holdCount > 1) return block()
            return withProcessLock(key, lock, block)
        } finally {
            threadLock.unlock()
        }
    }

    private fun <T> withProcessLock(target: Path, lock: (FileChannel) -> FileLock, block: () -> T): T {
        val lockFile = target.resolveSibling(target.fileName.toString() + ".lock")
        val channel = lockingFailure(target, lockFile) {
            Files.createDirectories(target.parent)
            FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        }
        return channel.use {
            val held = lockingFailure(target, lockFile) { lock(it) }
            try {
                block()
            } finally {
                held.release()
            }
        }
    }

    private fun <T> lockingFailure(target: Path, lockFile: Path, action: () -> T): T = try {
        action()
    } catch (error: IOException) {
        throw WorktreeError.LocalFileUnreadable(target.toString(), "can't lock $lockFile: ${error.message}")
    }
}
