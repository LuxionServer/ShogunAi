package app.luxion.shogunai.infrastructure

import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

internal object LocalFileLock {

    private val threadLocks = ConcurrentHashMap<Path, ReentrantLock>()

    fun <T> withLock(target: Path, block: () -> T): T {
        val key = target.toAbsolutePath().normalize()
        val threadLock = threadLocks.computeIfAbsent(key) { ReentrantLock() }
        threadLock.lock()
        try {
            if (threadLock.holdCount > 1) return block()
            return withProcessLock(key, block)
        } finally {
            threadLock.unlock()
        }
    }

    private fun <T> withProcessLock(target: Path, block: () -> T): T {
        val channel = try {
            Files.createDirectories(target.parent)
            FileChannel.open(
                target.resolveSibling(target.fileName.toString() + ".lock"),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            )
        } catch (_: IOException) {
            return block()
        }
        return channel.use {
            val held = try {
                it.lock()
            } catch (_: IOException) {
                null
            }
            try {
                block()
            } finally {
                held?.release()
            }
        }
    }
}
