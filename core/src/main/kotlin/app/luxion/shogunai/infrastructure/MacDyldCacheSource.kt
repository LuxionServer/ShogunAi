package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.executor.CommandTimeouts
import app.luxion.shogunai.domain.executor.ShellCommandExecutor
import app.luxion.shogunai.domain.io.DyldCacheSource
import app.luxion.shogunai.domain.model.DyldCacheFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.coroutineContext

class MacDyldCacheSource(
    private val executor: ShellCommandExecutor,
    private val osName: String = System.getProperty("os.name", ""),
    private val root: String = DYLD_CACHES_ROOT,
) : DyldCacheSource {

    private val isMac: Boolean get() = osName.startsWith("Mac", ignoreCase = true)

    override suspend fun folders(): List<DyldCacheFolder> {
        if (!isMac) return emptyList()
        return withContext(Dispatchers.IO) {
            val children = Path.of(root).toFile().listFiles() ?: return@withContext emptyList()
            children.filter { it.isDirectory }.sortedBy { it.name }.map { folder ->
                DyldCacheFolder(folder.name, folder.toString(), measure(folder.toPath()))
            }
        }
    }

    override suspend fun currentBuild(): String? {
        if (!isMac) return null
        val result = executor.executeWithin(listOf("sw_vers", "-buildVersion"), CommandTimeouts.LISTING)
        return if (result.isSuccess) result.stdout.trim().ifBlank { null } else null
    }

    private suspend fun measure(folder: Path): Long? {
        if (!Files.isReadable(folder)) return null
        val job = coroutineContext.job
        val visitor = SizeVisitor { job.isActive }
        Files.walkFileTree(folder, visitor)
        job.ensureActive()
        return visitor.total
    }

    private companion object {
        const val DYLD_CACHES_ROOT = "/Library/Developer/CoreSimulator/Caches/dyld"
    }
}
