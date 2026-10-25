package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.RepositoryTrust
import app.luxion.shogunai.domain.model.RepositoryApprovalSubject

class FakeRepositoryTrust(
    approved: Map<String, RepositoryApprovalSubject> = emptyMap(),
    private val approvesEverything: Boolean = false,
) : RepositoryTrust {

    val approvals = approved.toMutableMap()

    override fun isApproved(repository: String, subject: RepositoryApprovalSubject): Boolean =
        approvesEverything || subject.isEmpty || approvals[repository] == subject

    override fun approve(repository: String, subject: RepositoryApprovalSubject) {
        approvals[repository] = subject
    }

    override fun revoke(repository: String) {
        approvals.remove(repository)
    }
}
