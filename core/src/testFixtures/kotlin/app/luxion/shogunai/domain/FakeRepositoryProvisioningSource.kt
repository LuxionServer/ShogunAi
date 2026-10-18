package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.RepositoryConfigEdit
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.WorktreeError
import java.io.IOException

class FakeRepositoryProvisioningSource(
    private val byRepository: Map<String, RepositoryProvisioning> = emptyMap(),
    private val unreadable: Set<String> = emptySet(),
    private val failWritesFor: Set<String> = emptySet(),
) : RepositoryProvisioningSource {

    val addedPatterns = mutableListOf<Pair<String, List<String>>>()
    val writtenEdits = mutableListOf<Pair<String, RepositoryConfigEdit>>()
    var currentFingerprint = "fingerprint-1"

    override fun read(repositoryPath: String): RepositoryProvisioning {
        if (repositoryPath in unreadable) {
            throw WorktreeError.InvalidRepositoryConfig("$repositoryPath/${RepositoryProvisioning.CONFIG_FILE_NAME}", "malformed")
        }
        return byRepository[repositoryPath] ?: RepositoryProvisioning()
    }

    override fun fingerprint(repositoryPath: String): String = currentFingerprint

    override fun addCopyPatterns(repositoryPath: String, patterns: List<String>, expectedFingerprint: String?) {
        if (repositoryPath in failWritesFor) throw IOException("read-only file system")
        requireUnchanged(repositoryPath, expectedFingerprint)
        addedPatterns += repositoryPath to patterns
    }

    override fun preview(repositoryPath: String, edit: RepositoryConfigEdit): String = "preview of $edit"

    override fun write(repositoryPath: String, edit: RepositoryConfigEdit, expectedFingerprint: String) {
        if (repositoryPath in failWritesFor) throw IOException("read-only file system")
        requireUnchanged(repositoryPath, expectedFingerprint)
        writtenEdits += repositoryPath to edit
    }

    private fun requireUnchanged(repositoryPath: String, expected: String?) {
        if (expected != null && expected != currentFingerprint) {
            throw WorktreeError.RepositoryConfigChanged("$repositoryPath/${RepositoryProvisioning.CONFIG_FILE_NAME}")
        }
    }
}
