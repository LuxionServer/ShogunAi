package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.DerivedDataSizeStore
import app.luxion.shogunai.domain.io.DerivedDataSource
import app.luxion.shogunai.domain.model.AttributedDerivedData
import app.luxion.shogunai.domain.model.DerivedDataListing
import app.luxion.shogunai.domain.model.DerivedDataOwner
import app.luxion.shogunai.domain.model.Worktree
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList

enum class DerivedDataMeasure { NONE, ORPHANS, ALL }

class ListDerivedDataUseCase(
    private val source: DerivedDataSource,
    sizes: DerivedDataSizeStore = DerivedDataSizeStore.NONE,
) {
    private val measureSizes = MeasureDerivedDataUseCase(source, sizes)

    suspend operator fun invoke(
        worktrees: List<Worktree>,
        includeCompilationCache: Boolean,
        measure: DerivedDataMeasure,
    ): Result<DerivedDataListing> = runCatching {
        coroutineScope {
            val compilationCache = async { if (includeCompilationCache) source.compilationCacheBytes() else null }
            val attributed = source.folders().map { folder -> AttributedDerivedData(folder, ownerOf(folder.workspacePath, worktrees)) }
            val toMeasure = attributed.filter { entry ->
                when (measure) {
                    DerivedDataMeasure.NONE -> false
                    DerivedDataMeasure.ORPHANS -> entry.owner == DerivedDataOwner.Orphaned
                    DerivedDataMeasure.ALL -> true
                }
            }.map { it.folder }
            val measured = measureSizes(toMeasure).toList().associate { it.path to (it.sizeBytes ?: 0L) }
            DerivedDataListing(
                folders = attributed.map { entry ->
                    measured[entry.folder.path]?.let { entry.copy(folder = entry.folder.copy(sizeBytes = it)) } ?: entry
                },
                compilationCacheBytes = compilationCache.await(),
            )
        }
    }

    private fun ownerOf(workspacePath: String, worktrees: List<Worktree>): DerivedDataOwner =
        if (!source.projectExists(workspacePath)) {
            if (source.isOnUnavailableVolume(workspacePath)) DerivedDataOwner.UnavailableVolume
            else DerivedDataOwner.Orphaned
        } else {
            owningWorktree(workspacePath, worktrees)?.let { DerivedDataOwner.Worktree(it.path) }
                ?: DerivedDataOwner.OtherProject
        }
}
