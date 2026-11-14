package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.SwiftBuildSource
import app.luxion.shogunai.domain.model.DerivedDataDeletionFailure
import app.luxion.shogunai.domain.model.DerivedDataFolder
import app.luxion.shogunai.domain.model.SwiftBuildDeletion
import app.luxion.shogunai.domain.model.Worktree
import app.luxion.shogunai.domain.model.normalizePath
import app.luxion.shogunai.domain.model.runSuspendCatching
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

class DeleteUnusedSwiftBuildsUseCase(
    private val swiftBuilds: SwiftBuildSource,
    private val now: () -> Instant = Instant::now,
) {
    suspend operator fun invoke(worktrees: List<Worktree>, activeWorktreePaths: Set<String>): Result<SwiftBuildDeletion> = runSuspendCatching {
        val active = activeWorktreePaths.map(::normalizePath).toSet()
        val deleted = mutableListOf<DerivedDataFolder>()
        val inUse = mutableListOf<DerivedDataFolder>()
        val failures = mutableListOf<DerivedDataDeletionFailure>()
        worktrees.mapNotNull { swiftBuilds.folderOf(it.path) }.forEach { folder ->
            if (isInUse(folder, active)) {
                inUse += folder
                return@forEach
            }
            try {
                val measured = folder.copy(sizeBytes = swiftBuilds.size(folder))
                val current = swiftBuilds.folderOf(folder.workspacePath)
                when {
                    current == null -> Unit
                    isInUse(current, active) -> inUse += measured
                    else -> {
                        swiftBuilds.delete(current)
                        deleted += measured
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failures += DerivedDataDeletionFailure(folder.path, error.message ?: "delete failed")
            }
        }
        SwiftBuildDeletion(deleted = deleted, inUse = inUse, failures = failures)
    }

    private fun isInUse(folder: DerivedDataFolder, active: Set<String>): Boolean =
        isWorktreeFolderInUse(folder.workspacePath, active, folder.lastAccessed, now())
}
