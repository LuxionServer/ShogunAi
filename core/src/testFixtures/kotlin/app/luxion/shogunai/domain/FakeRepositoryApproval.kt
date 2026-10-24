package app.luxion.shogunai.domain

import app.luxion.shogunai.domain.io.ApprovalDecision
import app.luxion.shogunai.domain.io.RepositoryApproval
import app.luxion.shogunai.domain.model.RepositoryApprovalSubject

class FakeRepositoryApproval(private val decision: ApprovalDecision) : RepositoryApproval {

    val asked = mutableListOf<Pair<String, RepositoryApprovalSubject>>()

    override suspend fun decide(repository: String, subject: RepositoryApprovalSubject): ApprovalDecision {
        asked += repository to subject
        return decision
    }
}
