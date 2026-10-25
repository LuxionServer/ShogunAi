package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.RepositoryProvisioningSource
import app.luxion.shogunai.domain.model.EnvironmentDeclaration
import app.luxion.shogunai.domain.model.RepositoryConfigEdit
import app.luxion.shogunai.domain.model.RepositoryProvisioning
import app.luxion.shogunai.domain.model.WorktreeError
import app.luxion.shogunai.domain.model.parseEnvironmentVariable
import java.io.IOException

class FakeRepositoryProvisioningSource(
    byRepository: Map<String, RepositoryProvisioning> = emptyMap(),
    private val unreadable: Set<String> = emptySet(),
    private val failWritesFor: Set<String> = emptySet(),
) : RepositoryProvisioningSource {

    private val byRepository = byRepository.toMutableMap()

    val addedPatterns = mutableListOf<Pair<String, List<String>>>()
    val addedLinkPatterns = mutableListOf<Pair<String, List<String>>>()
    val writtenEdits = mutableListOf<Pair<String, RepositoryConfigEdit>>()
    var currentFingerprint = "fingerprint-1"

    override fun read(repositoryPath: String): RepositoryProvisioning {
        if (repositoryPath in unreadable) {
            throw WorktreeError.InvalidRepositoryConfig("$repositoryPath/${RepositoryProvisioning.CONFIG_FILE_NAME}", "malformed")
        }
        return byRepository[repositoryPath] ?: RepositoryProvisioning()
    }

    override fun fingerprint(repositoryPath: String): String = currentFingerprint

    override fun addPatterns(
        repositoryPath: String,
        copyPatterns: List<String>,
        linkPatterns: List<String>,
        expectedFingerprint: String?,
    ) {
        if (repositoryPath in failWritesFor) throw IOException("read-only file system")
        requireUnchanged(repositoryPath, expectedFingerprint)
        if (copyPatterns.isNotEmpty()) addedPatterns += repositoryPath to copyPatterns
        if (linkPatterns.isNotEmpty()) addedLinkPatterns += repositoryPath to linkPatterns
    }

    override fun preview(repositoryPath: String, edit: RepositoryConfigEdit): String = "preview of $edit"

    override fun write(repositoryPath: String, edit: RepositoryConfigEdit, expectedFingerprint: String) {
        if (repositoryPath in failWritesFor) throw IOException("read-only file system")
        requireUnchanged(repositoryPath, expectedFingerprint)
        writtenEdits += repositoryPath to edit
        byRepository[repositoryPath] = read(repositoryPath).copy(
            providers = edit.providers,
            environment = EnvironmentDeclaration(edit.environment.map { parseEnvironmentVariable(it.name, it.template) }, edit.envFile),
        )
    }

    private fun requireUnchanged(repositoryPath: String, expected: String?) {
        if (expected != null && expected != currentFingerprint) {
            throw WorktreeError.RepositoryConfigChanged("$repositoryPath/${RepositoryProvisioning.CONFIG_FILE_NAME}")
        }
    }
}
