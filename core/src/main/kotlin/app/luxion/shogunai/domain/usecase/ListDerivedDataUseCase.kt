package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.DerivedDataSizeStore
import app.luxion.shogunai.domain.io.DerivedDataSource
import app.luxion.shogunai.domain.io.PackageCacheSource
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
    private val packageCaches: PackageCacheSource = PackageCacheSource.NONE,
) {
    private val measureSizes = MeasureDerivedDataUseCase(source, sizes)

    suspend operator fun invoke(
        worktrees: List<Worktree>,
        includeCompilationCache: Boolean,
        measure: DerivedDataMeasure,
    ): Result<DerivedDataListing> = runCatching {
        coroutineScope {
            val compilationCache = async { if (includeCompilationCache) source.compilationCacheBytes() else null }
            val caches = async { packageCaches.sizes() }
            val attributed = source.folders().map { folder -> AttributedDerivedData(folder, ownerOf(folder.workspacePath, worktrees)) }
            val toMeasure = attributed.filter { entry ->
                when (measure) {
                    DerivedDataMeasure.NONE -> false
                    DerivedDataMeasure.ORPHANS -> entry.owner == DerivedDataOwner.Orphaned
                    DerivedDataMeasure.ALL -> true
                }
            }.map { it.folder }
            val measured = measureSizes(toMeasure).toList().associateBy { it.path }
            DerivedDataListing(
                folders = attributed.map { entry ->
                    val size = measured[entry.folder.path]
                    if (size == null) entry else entry.copy(folder = entry.folder.copy(sizeBytes = size.sizeBytes ?: 0L, packagesBytes = size.packagesBytes))
                },
                compilationCacheBytes = compilationCache.await(),
                packageCaches = caches.await(),
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
