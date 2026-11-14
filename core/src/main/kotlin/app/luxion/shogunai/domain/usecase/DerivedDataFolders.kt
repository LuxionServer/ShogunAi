package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.DerivedDataSource
import app.luxion.shogunai.domain.io.SwiftBuildSource
import app.luxion.shogunai.domain.model.DerivedDataFolder
import app.luxion.shogunai.domain.model.DerivedDataKind
import app.luxion.shogunai.domain.model.normalizePath
import java.time.Duration
import java.time.Instant

class DerivedDataFolders(
    private val derivedData: DerivedDataSource,
    private val swiftBuilds: SwiftBuildSource,
) {
    suspend fun size(folder: DerivedDataFolder): Long = when (folder.kind) {
        DerivedDataKind.XCODE -> derivedData.size(folder)
        DerivedDataKind.SWIFT_BUILD -> swiftBuilds.size(folder)
    }

    suspend fun packagesSize(folder: DerivedDataFolder): Long = when (folder.kind) {
        DerivedDataKind.XCODE -> derivedData.packagesSize(folder)
        DerivedDataKind.SWIFT_BUILD -> swiftBuilds.packagesSize(folder)
    }

    suspend fun delete(folder: DerivedDataFolder) = when (folder.kind) {
        DerivedDataKind.XCODE -> derivedData.delete(folder)
        DerivedDataKind.SWIFT_BUILD -> swiftBuilds.delete(folder)
    }

    suspend fun current(folder: DerivedDataFolder): DerivedDataFolder? = when (folder.kind) {
        DerivedDataKind.XCODE -> derivedData.folders().firstOrNull { it.path == folder.path }
        DerivedDataKind.SWIFT_BUILD -> swiftBuilds.folderOf(folder.workspacePath)
    }
}

private val FOLDER_IN_USE_GRACE: Duration = Duration.ofHours(1)

internal fun isWorktreeFolderInUse(worktreePath: String, active: Set<String>, lastAccessed: Instant?, now: Instant): Boolean {
    if (normalizePath(worktreePath) in active) return true
    return lastAccessed != null && Duration.between(lastAccessed, now) < FOLDER_IN_USE_GRACE
}
