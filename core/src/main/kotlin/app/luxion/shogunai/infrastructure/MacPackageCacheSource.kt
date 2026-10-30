package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.PackageCacheSource
import app.luxion.shogunai.domain.model.PackageCacheSizes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.nio.file.Path

class MacPackageCacheSource(
    private val fileManager: FileManager,
    private val home: String,
    private val osName: String = System.getProperty("os.name", ""),
) : PackageCacheSource {

    override suspend fun sizes(): PackageCacheSizes {
        if (!osName.startsWith("Mac", ignoreCase = true)) return PackageCacheSizes()
        return coroutineScope {
            val swiftPm = async { measure(SWIFT_PM_CACHE) }
            val cocoaPods = async { measure(COCOA_PODS_CACHE) }
            PackageCacheSizes(swiftPmBytes = swiftPm.await(), cocoaPodsBytes = cocoaPods.await())
        }
    }

    private suspend fun measure(relative: String): Long? = withContext(Dispatchers.IO) {
        val path = Path.of(home, relative).toString()
        if (fileManager.isDirectory(path)) runCatching { fileManager.directorySize(path) }.getOrNull() else null
    }

    private companion object {
        const val SWIFT_PM_CACHE = "Library/Caches/org.swift.swiftpm"
        const val COCOA_PODS_CACHE = "Library/Caches/CocoaPods"
    }
}
