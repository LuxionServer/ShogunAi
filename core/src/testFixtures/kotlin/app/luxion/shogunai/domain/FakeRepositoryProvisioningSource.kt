package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.WorktreeError
import java.io.IOException

class FakeRepositoryProvisioningSource(
    private val byRepository: Map<String, RepositoryProvisioning> = emptyMap(),
    private val unreadable: Set<String> = emptySet(),
    private val failWritesFor: Set<String> = emptySet(),
) : RepositoryProvisioningSource {

    val addedPatterns = mutableListOf<Pair<String, List<String>>>()

    override fun read(repositoryPath: String): RepositoryProvisioning {
        if (repositoryPath in unreadable) {
            throw WorktreeError.InvalidRepositoryConfig("$repositoryPath/${RepositoryProvisioning.CONFIG_FILE_NAME}", "malformed")
        }
        return byRepository[repositoryPath] ?: RepositoryProvisioning()
    }

    override fun addCopyPatterns(repositoryPath: String, patterns: List<String>) {
        if (repositoryPath in failWritesFor) throw IOException("read-only file system")
        addedPatterns += repositoryPath to patterns
    }
}
