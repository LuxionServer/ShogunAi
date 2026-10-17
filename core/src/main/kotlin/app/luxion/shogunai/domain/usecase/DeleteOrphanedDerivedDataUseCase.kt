package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.DerivedDataSource
import app.luxion.shogunai.domain.model.DerivedDataDeletion
import app.luxion.shogunai.domain.model.DerivedDataDeletionFailure
import app.luxion.shogunai.domain.model.DerivedDataFolder

class DeleteOrphanedDerivedDataUseCase(
    private val source: DerivedDataSource,
) {
    suspend operator fun invoke(): Result<DerivedDataDeletion> = runCatching {
        val candidates = source.folders().filter { folder ->
            !source.projectExists(folder.workspacePath) && !source.isOnUnavailableVolume(folder.workspacePath)
        }
        val deleted = mutableListOf<DerivedDataFolder>()
        val failures = mutableListOf<DerivedDataDeletionFailure>()
        candidates.forEach { folder ->
            if (!source.projectExists(folder.workspacePath)) {
                try {
                    source.delete(folder)
                    deleted += folder
                } catch (error: Exception) {
                    failures += DerivedDataDeletionFailure(folder.path, error.message ?: "delete failed")
                }
            }
        }
        DerivedDataDeletion(deleted = deleted, failures = failures)
    }
}
