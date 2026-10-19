package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.RepositoryTrust

class FakeRepositoryTrust(approved: Map<String, List<List<String>>> = emptyMap()) : RepositoryTrust {

    val approvals = approved.toMutableMap()

    override fun isApproved(repository: String, commands: List<List<String>>): Boolean = approvals[repository] == commands

    override fun approve(repository: String, commands: List<List<String>>) {
        approvals[repository] = commands
    }

    override fun revoke(repository: String) {
        approvals.remove(repository)
    }
}
