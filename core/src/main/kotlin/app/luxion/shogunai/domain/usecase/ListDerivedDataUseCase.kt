package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.DerivedDataSource
import app.luxion.shogunai.domain.model.AttributedDerivedData
import app.luxion.shogunai.domain.model.DerivedDataListing
import app.luxion.shogunai.domain.model.DerivedDataOwner
import app.luxion.shogunai.domain.model.Worktree

class ListDerivedDataUseCase(
    private val source: DerivedDataSource,
) {
    suspend operator fun invoke(
        worktrees: List<Worktree>,
        includeCompilationCache: Boolean,
    ): Result<DerivedDataListing> = runCatching {
        DerivedDataListing(
            folders = source.folders().map { folder ->
                AttributedDerivedData(folder, ownerOf(folder.workspacePath, worktrees))
            },
            compilationCacheBytes = if (includeCompilationCache) source.compilationCacheBytes() else null,
        )
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
