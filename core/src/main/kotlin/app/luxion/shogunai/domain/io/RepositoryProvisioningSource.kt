package app.luxion.shogunai.domain.io

import app.luxion.shogunai.domain.model.RepositoryProvisioning

interface RepositoryProvisioningSource {

    fun read(repositoryPath: String): RepositoryProvisioning

    fun addCopyPatterns(repositoryPath: String, patterns: List<String>)
}
