package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.DerivedDataSource
import app.luxion.shogunai.domain.model.DerivedDataFolder
import java.io.IOException

class FakeDerivedDataSource(
    var supported: Boolean = true,
    var existing: List<DerivedDataFolder> = emptyList(),
    var existingProjects: Set<String> = emptySet(),
    var unavailableVolumes: Set<String> = emptySet(),
    var cacheBytes: Long? = null,
    var failDeletionFor: Set<String> = emptySet(),
    var free: Long? = null,
) : DerivedDataSource {

    val sizes = mutableMapOf<String, Long>()
    val packageSizes = mutableMapOf<String, Long>()
    val packagesMeasured = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    val measured = mutableListOf<String>()
    var beforeDelete: () -> Unit = {}
    var onFolders: (Int) -> Unit = {}
    var onSize: suspend (DerivedDataFolder) -> Unit = {}
    var failSizeFor: Set<String> = emptySet()
    private var folderCalls = 0

    override fun isSupported(): Boolean = supported

    override suspend fun folders(): List<DerivedDataFolder> {
        onFolders(++folderCalls)
        return existing
    }

    override suspend fun size(folder: DerivedDataFolder): Long {
        measured += folder.path
        onSize(folder)
        if (folder.path in failSizeFor) throw IOException("can't read ${folder.path}")
        return folder.sizeBytes ?: sizes[folder.path] ?: 0L
    }

    override suspend fun packagesSize(folder: DerivedDataFolder): Long {
        packagesMeasured += folder.path
        return packageSizes[folder.path] ?: 0L
    }

    override suspend fun compilationCacheBytes(): Long? = cacheBytes

    override suspend fun freeBytes(): Long? = free

    override fun projectExists(workspacePath: String): Boolean = workspacePath in existingProjects

    override fun isOnUnavailableVolume(workspacePath: String): Boolean =
        unavailableVolumes.any { workspacePath.startsWith("$it/") }

    override suspend fun delete(folder: DerivedDataFolder) {
        beforeDelete()
        if (folder.path in failDeletionFor) throw IOException("can't delete ${folder.path}")
        deleted += folder.path
        existing = existing.filterNot { it.path == folder.path }
    }

    companion object {
        fun derivedData(name: String, workspacePath: String, sizeBytes: Long? = 1_000_000_000L) =
            DerivedDataFolder("/home/dev/Library/Developer/Xcode/DerivedData/$name", workspacePath, sizeBytes, null)
    }
}
