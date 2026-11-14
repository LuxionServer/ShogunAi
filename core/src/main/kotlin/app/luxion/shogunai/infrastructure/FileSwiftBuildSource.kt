package app.luxion.shogunai.infrastructure

import app.luxion.shogunai.domain.io.FileManager
import app.luxion.shogunai.domain.io.SwiftBuildSource
import app.luxion.shogunai.domain.model.DerivedDataFolder
import app.luxion.shogunai.domain.model.DerivedDataKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

class FileSwiftBuildSource(private val fileManager: FileManager) : SwiftBuildSource {

    override fun folderOf(worktreePath: String): DerivedDataFolder? {
        val worktree = worktreePath.trimEnd('/')
        val build = "$worktree/$BUILD_FOLDER"
        if (!isBuildFolder(build)) return null
        return DerivedDataFolder(build, worktree, sizeBytes = null, lastAccessed = lastModified(build), kind = DerivedDataKind.SWIFT_BUILD)
    }

    override suspend fun size(folder: DerivedDataFolder): Long = withContext(Dispatchers.IO) {
        fileManager.directorySize(folder.path)
    }

    override suspend fun packagesSize(folder: DerivedDataFolder): Long = withContext(Dispatchers.IO) {
        PACKAGE_FOLDERS.map { "${folder.path}/$it" }.filter(fileManager::isDirectory).sumOf { fileManager.directorySize(it) }
    }

    override suspend fun delete(folder: DerivedDataFolder) {
        if (!folder.path.endsWith("/$BUILD_FOLDER") || !isBuildFolder(folder.path)) {
            throw IllegalStateException("Refusing to delete ${folder.path}: not the .build folder of a Swift package")
        }
        withContext(Dispatchers.IO) { fileManager.deleteRecursively(folder.path) }
    }

    private fun isBuildFolder(build: String): Boolean {
        val worktree = build.substringBeforeLast('/')
        return fileManager.exists("$worktree/$PACKAGE_MANIFEST") && fileManager.isDirectory(build) && !fileManager.isSymbolicLink(build)
    }

    private fun lastModified(build: String): Instant? {
        val entries = fileManager.listNames(build).orEmpty().map { "$build/$it" }
        return (entries + build).mapNotNull(fileManager::lastModifiedMillis).maxOrNull()?.let(Instant::ofEpochMilli)
    }

    private companion object {
        const val BUILD_FOLDER = ".build"
        const val PACKAGE_MANIFEST = "Package.swift"
        val PACKAGE_FOLDERS = listOf("checkouts", "repositories")
    }
}
