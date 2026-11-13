package app.luxion.shogunai.domain.usecase

import app.luxion.shogunai.domain.io.DerivedDataSource
import app.luxion.shogunai.domain.model.DerivedDataDeletion
import app.luxion.shogunai.domain.model.DerivedDataDeletionFailure
import app.luxion.shogunai.domain.model.DerivedDataFolder
import app.luxion.shogunai.domain.model.runSuspendCatching
import kotlin.coroutines.cancellation.CancellationException

class DeleteOrphanedDerivedDataUseCase(
    private val source: DerivedDataSource,
) {
    suspend operator fun invoke(): Result<DerivedDataDeletion> = runSuspendCatching {
        val candidates = source.folders().filter { folder ->
            !source.projectExists(folder.workspacePath) && !source.isOnUnavailableVolume(folder.workspacePath)
        }
        val deleted = mutableListOf<DerivedDataFolder>()
        val failures = mutableListOf<DerivedDataDeletionFailure>()
        candidates.forEach { folder ->
            if (!source.projectExists(folder.workspacePath)) {
                try {
                    val measured = folder.copy(sizeBytes = folder.sizeBytes ?: source.size(folder))
                    source.delete(folder)
                    deleted += measured
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    failures += DerivedDataDeletionFailure(folder.path, error.message ?: "delete failed")
                }
            }
        }
        DerivedDataDeletion(deleted = deleted, failures = failures)
    }
}
