package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.RepositoryConfigEdit
import app.luxion.shogunai.domain.model.RepositoryProvisioning

interface RepositoryProvisioningSource {

    fun read(repositoryPath: String): RepositoryProvisioning

    fun fingerprint(repositoryPath: String): String

    fun addCopyPatterns(repositoryPath: String, patterns: List<String>, expectedFingerprint: String? = null)

    fun preview(repositoryPath: String, edit: RepositoryConfigEdit): String

    fun write(repositoryPath: String, edit: RepositoryConfigEdit, expectedFingerprint: String)
}
