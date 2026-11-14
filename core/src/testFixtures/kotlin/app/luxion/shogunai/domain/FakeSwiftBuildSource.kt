package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.SwiftBuildSource
import app.luxion.shogunai.domain.model.DerivedDataFolder
import app.luxion.shogunai.domain.model.DerivedDataKind
import java.io.IOException
import java.time.Instant

class FakeSwiftBuildSource(vararg builds: DerivedDataFolder) : SwiftBuildSource {

    val folders = builds.associateBy { it.workspacePath }.toMutableMap()
    val sizes = mutableMapOf<String, Long>()
    val packageSizes = mutableMapOf<String, Long>()
    val measured = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    var failDeletionFor: Set<String> = emptySet()
    var failSizeFor: Set<String> = emptySet()
    var afterSize: (DerivedDataFolder) -> Unit = {}

    override fun folderOf(worktreePath: String): DerivedDataFolder? = folders[worktreePath]

    override suspend fun size(folder: DerivedDataFolder): Long {
        measured += folder.path
        if (folder.path in failSizeFor) throw IOException("can't read ${folder.path}")
        afterSize(folder)
        return sizes[folder.path] ?: 0L
    }

    override suspend fun packagesSize(folder: DerivedDataFolder): Long = packageSizes[folder.path] ?: 0L

    override suspend fun delete(folder: DerivedDataFolder) {
        if (folder.path in failDeletionFor) throw IOException("can't delete ${folder.path}")
        deleted += folder.path
        folders.remove(folder.workspacePath)
    }

    companion object {
        fun swiftBuild(worktreePath: String, lastAccessed: Instant?, sizeBytes: Long? = null) =
            DerivedDataFolder("$worktreePath/.build", worktreePath, sizeBytes, lastAccessed, kind = DerivedDataKind.SWIFT_BUILD)
    }
}
